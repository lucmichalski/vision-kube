

import math, sys, cPickle, pdb, os, re

import numpy as np

from crossval import CrossvalOptimization

from collections import defaultdict

from yael import yael, ynumpy

from yael.threads import ParallelIter

from video_descriptors import label_map, get_data, data_normalize, combinations, standardize, power_normalize, L2_normalize, parse_listfile

from video_descriptors import compute_dcr, average_precision

sys.path.append('/home/lear/oneata/src/med12lear/threadsafe_classifier')

import libsvm_precomputed

import pdb

np.random.seed(0)

#############################################
# Learning


def libsvm_train(Kxx, cx, subset, c, pos_weight = 1.0, eps = 1e-3, verbose = 0, probability = 0):  
    # check input
    assert Kxx.shape[0] == Kxx.shape[1] and Kxx.flags.c_contiguous, Kxx.shape
    assert subset.flags.c_contiguous and cx.flags.c_contiguous    
    assert np.all(subset < Kxx.shape[0]) and np.all(subset >= 0)
    
    # set libsvm params 

    param = libsvm_precomputed.svm_parameter()
    libsvm_precomputed.svm_param_set_default(param)

    param.nr_weight = 2
    param.weight_label = weight_label = yael.ivec(2)
    weight_label[0] = -1
    weight_label[1] = 1
    param.weight = weights = yael.dvec(2)
    npos = (cx[subset] == 1).sum()
    nneg = (cx[subset] == -1).sum()
    weights[0] = 2 * npos / float(npos + nneg)
    weights[1] = 2 * nneg / float(npos + nneg) * pos_weight
    param.C = c
    param.nu = param.p = 0
    param.shrinking = 1
    param.probability = probability
    param.eps = eps
    libsvm_precomputed.svm_set_verbose(verbose)
    
    # prepare output
    nex = subset.size
    dual_coeffs = np.empty((nex,), dtype = np.float64)
    bias_out = yael.dvec(3)
    
    # actual call 
    ret = libsvm_precomputed.svm_train_precomputed(
                nex, 
                yael.numpy_to_ivec_ref(subset),
                yael.numpy_to_ivec_ref(cx),
                yael.numpy_to_fvec_ref(Kxx),
                Kxx.shape[1],
                param,
                yael.numpy_to_dvec_ref(dual_coeffs),
                bias_out)
    assert ret > 0

    bias_term = bias_out[0]
    #print dual_coeffs, bias_term
    if probability:
        probA = bias_out[1]
        probB = bias_out[2]        
        return dual_coeffs, bias_term, probA, probB
    else: 
        return dual_coeffs, bias_term


def scores_to_probas(scores, A, B):
     probas = np.empty(scores.size, dtype = np.float32)
     scores = scores.astype(np.float32)

     libsvm_precomputed.scores_to_probas(A, B, scores.size,
                                         yael.numpy_to_fvec_ref(scores),     
                                         yael.numpy_to_fvec_ref(probas))
     return probas


#############################################
# Optimization


class Stats:
    pass



def combine_kernels(all_Kxx, params):
    nchannel = len(all_Kxx)

    if nchannel == 1:     
        return all_Kxx[0]

    Kxx = all_Kxx[0].copy()
    for chan in range(1, nchannel):
        Kxx_ch = all_Kxx[chan]
        name = 'weight_channel_%d' % chan 
        factor = params[name] if name in params else 1.0
        # factor is on the descriptors => sqr on kernel
        Kxx += factor**2 * Kxx_ch
    return Kxx


class Optimizer(CrossvalOptimization):
    
    def __init__(self, Kxx, cx, criterion, test_fraction = 0.25, nfold = 10):
        CrossvalOptimization.__init__(self, None, cx)
        self.Kxx = Kxx
        self.cx = cx
        self.criterion = criterion
        
        # make the folds

        self.nfold = nfold

        pos = np.nonzero(cx == 1)[0].astype(np.int32); npos = pos.size
        neg = np.nonzero(cx == -1)[0].astype(np.int32); nneg = neg.size
        npos_test = int(npos * test_fraction)
        nneg_test = int(nneg * test_fraction)

        print "Drawing %d folds of train %dp + %dn / test %dp + %dn" % (
            self.nfold,
            npos - npos_test, nneg - nneg_test, 
            npos_test, nneg_test)
        
        splits = []
        rnd = np.random.RandomState(0)
        for i in range(self.nfold):
                ppos = rnd.permutation(pos)
                pneg = rnd.permutation(neg)
                splits.append((np.hstack((pneg[nneg_test:], ppos[npos_test:])),
                               np.hstack((pneg[:nneg_test], ppos[:npos_test]))))

        self.splits = splits
              

    def eval_params(self, params, fold):

        train_index, test_index = self.splits[fold][:2]

        if len(self.splits[fold]) == 3: # useful for multiclass optimization
            cx = self.splits[fold][2]
        else:
            cx = self.cx        

        c = params['c']    
        pos_weight = params['positive_weight']

        Kxx = combine_kernels(self.Kxx, params)
        
        dual_coef, bias = libsvm_train(Kxx, cx, train_index,
                                       c = c,
                                       pos_weight = pos_weight)

        scores = np.empty(test_index.size, dtype = np.float32)
        
        libsvm_precomputed.mul_matvec_subset(
            yael.numpy_to_fvec_ref(Kxx), Kxx.shape[1],
            yael.numpy_to_ivec_ref(train_index), train_index.size,
            yael.numpy_to_ivec_ref(test_index), test_index.size,
            yael.numpy_to_dvec_ref(dual_coef),
            yael.numpy_to_fvec_ref(scores))      
        scores += bias                          

        if self.criterion == 'ap':           
                perf = average_precision(cx[test_index], scores)
        elif self.criterion == 'dcr':
                perf = 1 - compute_dcr(cx[test_index], scores)
        elif self.criterion == 'sdcr':
                perf = 1 - surrogate_dcr(cx[test_index], scores)
        else:
                assert False

        #### microscopic penalizations
        # to favor the lowest c among ties
        perf -= math.log(c) * 1e-6
        # to favor positive_weight == 1
        perf -= abs(math.log(pos_weight)) * 1e-6
        

        stats = Stats()
        stats.valid_accuracies = np.array([perf])

        return stats


#############################################
# Load data 


def load_combination_train(dataset, cname):
        
        feature, params = combinations[cname]

        listfiles = {
            'tv11val':        ['train_balanced'],
            'tv11devo':       ['trecvid11_events'],
            'tv11devt':       ['trecvid11_events'],
            'tv11multiclass': ['trecvid11_multiclass_train'],
            'tv12wacv':       ['train_wacv'],
            'tv11':           ['train_balanced', 'test_balanced', 'trecvid11_more_null'],
            'tv12':           ['train_balanced', 'test_balanced', 'trecvid11_more_null', 'trecvid12_train'],
            'tv13':           ['trecvid13_train_events', 'trecvid13_train_null'],
            'tv13adhoc':      ['trecvid13_train_events', 'trecvid13_adhoc', 'trecvid13_train_null'],
            'tv14run11':      ['train_balanced', 'test_balanced', 'trecvid11_more_null'],
        }

        tr_data_i, tr_labels_i, tr_vidnames_i = [], [], []
        for lf in listfiles[dataset]:
            print "load %s" % lf
            tr_data_, tr_labels_, tr_vidnames_ = get_data(feature, lf, **params)
            tr_data_i.append(tr_data_)
            tr_labels_i.append(tr_labels_)
            tr_vidnames_i += tr_vidnames_

        tr_data_i = np.vstack(tr_data_i).astype(np.float32)
        tr_labels_i = np.hstack(tr_labels_i)

        return tr_data_i, tr_labels_i, tr_vidnames_i

def load_selection_train(dataset, selection, normalizers_in = None, power = 0.5):

    Kxx = []
    tr_data = []
    pdb.Set_train()
    nchannel = 0
    normalizers = {}


    for cname in selection:

        tr_data_i, tr_labels_i, tr_vidnames_i = load_combination_train(dataset, cname)

        if not normalizers_in:        
                print "normalizing (computing normalizers)"
                if dataset.statswith("tv14"):
                    # normalizers computed only on background videos
                    tr_data_i, mu, sigma = data_normalize(tr_data_i, power=power, compute_norm_subset = background_vids)
                else:
                    tr_data_i, mu, sigma = data_normalize(tr_data_i, power=power)
                normalizers[cname] = (mu, sigma)
        else:
                print "normalizing (provided mu, sigma)"
                (mu, sigma) = normalizers_in[cname] 

                tr_data_i, _, _ = standardize(tr_data_i, mu, sigma)
                tr_data_i = power_normalize(tr_data_i, power)
                tr_data_i = L2_normalize(tr_data_i)
                tr_data_i = tr_data_i.astype(np.float32)
                
        print "compute kernels train %d*%d" % (
                tr_data_i.shape)

        Kxx_i = np.dot(tr_data_i, tr_data_i.T)

        Kxx.append(Kxx_i)

        tr_data.append(tr_data_i)
        
        if nchannel == 0:
            tr_labels, tr_vidnames = tr_labels_i, tr_vidnames_i
        else:
            assert np.all(tr_labels == tr_labels_i)
            assert tr_vidnames_i == tr_vidnames

        nchannel += 1

        sys.stdout.flush()

    if not normalizers_in: 
            return Kxx, tr_data, tr_vidnames, tr_labels, normalizers
    else:
            return Kxx, tr_data, tr_vidnames, tr_labels, None


#############################################
# Training 

def do_train(): 

    norm_fname = data_prefix + "_normalizer.pickle"

    Kxx, tr_data, tr_vidnames, tr_labels, normalizers = load_selection_train(dataset, selection, None, power = power)
    nchannel = len(Kxx)
    ntrain = tr_labels.size

    print "storing", norm_fname  
    cPickle.dump(normalizers, open(norm_fname, "w"))

    for cl in classes:

        print "################## train class E%03d" % cl

        if use_other_negs: 
            # train on all (not just pos + NULL) 
            subset = np.arange(tr_labels.size, dtype = np.int32)
        else: 
            subset = np.nonzero((tr_labels == 0) | (tr_labels == cl))[0].astype(np.int32)
        
        Kxx_cl = [
            ynumpy.extract_rows_cols(K, subset, subset)
            for K in Kxx]

        if not crossval_mixweights:
            print "combining kernels once..."
            Kxx_cl = [combine_kernels(Kxx_cl, {})]

        tr_labels_cl = ((tr_labels[subset] == cl) * 2 - 1).astype(np.int32)

        opt = Optimizer(Kxx_cl, tr_labels_cl, criterion)

        opt.init_point = {
            'c': 9.0,
            'positive_weight': 1.0}
        
        opt.ranges = {
            'c': [1./9, 1./3, 1.0, 3.0, 9.0, 27.0, 81.0, 243.0, 729.0, 2187.0],
            'positive_weight': [2.**i for i in range(-10, 10)],
            }

        if fixedc:
            opt.init_point['c'] = fixedc
            opt.ranges['c'] = [fixedc]

        if crossval_mixweights:
            for chan in range(1, len(Kxx_cl)):
                name = 'weight_channel_%d' % chan 
                opt.init_point[name] = 1.0
                opt.ranges[name] = [0.05 * i for i in range(50)]      

        opt.n_thread = n_thread

        # try hard to optimize...
        opt.score_tol_factor = 0.66
            
        params = opt.optimize()
        params = params[0][0]

        print "re-estimating on full train set with", params

        Kxx_c = combine_kernels(Kxx_cl, params)
                
        dual_coef, bias, probA, probB = libsvm_train(Kxx_c, tr_labels_cl,
                                                     np.arange(tr_labels_cl.size, dtype = np.int32),
                                                     c = params['c'],
                                                     pos_weight = params['positive_weight'],
                                                     probability = 1)
        
        dual_coef_full = np.zeros(ntrain)
        dual_coef_full[subset] = dual_coef
        
        # extract W
        weights = [np.dot(dual_coef_full, tr_data[0])]

        for chan in range(1, nchannel):
            # apply channel weights to W vector
            name = 'weight_channel_%d' % chan 
            factor = params[name] if name in params else 1.0
            weights.append(factor * np.dot(dual_coef_full, tr_data[chan]))
                
        cl_fname = data_prefix + "_classifier_E%03d.pickle" % cl
        print "storing", cl_fname  
        cPickle.dump((weights, bias, probA, probB, params), open(cl_fname, "w"))
        
#############################################
# Load data and do test
            
def do_test(): 

    print "############################## test"

    norm_fname = data_prefix + "_normalizer.pickle"
    print "loading", norm_fname  
    normalizers = cPickle.load(open(norm_fname, "r"))

    # Prepare input and output

    if dataset_test == 'tv11val':
        listfiles = ['test_balanced']
    elif dataset_test == 'tv11devt':
        listfiles = ['trecvid11_devt']
    elif dataset_test == 'tv11devo':
        listfiles = ["trecvid11_test_%02d" % sl for sl in range(7)]
    elif dataset_test == 'tv12wacv':
        listfiles = ['test_wacv']
    elif dataset_test == 'tv11':
        if test_on_train:
            listfiles = ['train_balanced', 'test_balanced']
        else:       
            listfiles = ["trecvid11_test_%02d" % sl for sl in range(7)]
    elif dataset_test == 'tv12' or dataset_test.startswith('tv13'):
        # same test set in '12 and '13
        listfiles = ["trecvid12_test_%02d" % sl for sl in range(20)]
    else:
        assert False
        
    if test_on_train:
        of = {    
            eno: open("/dev/null", "w")
            for eno in classes}
    else:   
        prefix = data_prefix_test
        if probas: prefix += "_proba"

        print "Output results to", prefix
        of = {    
            eno: open(prefix + "_E%03d.dat" % eno, "w")
            for eno in classes}
    
    classifiers = {}
    
    # load classifiers

    for cl in classes:
        cl_fname = data_prefix + "_classifier_E%03d.pickle" % cl
        print "loading", cl_fname    
        classifiers[cl] = cPickle.load(open(cl_fname, "r"))

    # run tests (in //)

    if not ssl: 

        scores_src = ParallelIter(
            n_thread, [(listfile, None, selection, normalizers, classifiers,
                        probas) for listfile in listfiles], test_listfile)
    else: 
        scores_src = ParallelIter(
            n_thread, [(listfile, (i, ssl), selection, normalizers,
                        classifiers, probas) for listfile in listfiles for i in
                       range(ssl)], test_listfile)

    n_stored = 0
    all_scores, all_labels = defaultdict(list), defaultdict(list)
    for te_vidnames, scores_per_class in scores_src:
        for cl in classes:
            subset_test, scores, te_labels = scores_per_class[cl]

            for ii in subset_test:
                print >> of[cl], te_vidnames[ii], scores[ii]

            # Aggregate together the scores and labels from all listfiles.
            all_scores[cl].append(scores[subset_test])
            all_labels[cl].append(te_labels)

            of[cl].flush()
        n_stored += len(te_vidnames)
        print "Stored %d scores..." % n_stored

    # if we have ground-truth, evaluate
    if dataset_test == 'tv11val' or dataset_test.startswith('tv11dev') or 'wacv' in dataset_test or dataset_test == 'tv11':
        class_perfs = []
        for cls_idx in classes:
            _scores = np.hstack(all_scores[cls_idx]) 
            _labels = np.hstack(all_labels[cls_idx]) 
            if criterion == 'ap':
                perf = average_precision(_labels, _scores) * 100
            else:
                perf = compute_dcr(_labels, _scores)
            class_perfs.append((cls_idx, perf))
        print_results(class_perfs, print_format)


def print_results(class_perfs, print_format):
    printer = {
        'html': {
            'header': '<tr>\n\t<td></td>',
            'body': '\t<td> %2.2f </td>',
            'mean': '\t<td> %2.2f </td>',
            'footer': '</tr>'},
        'stdout': {
            'header': 'Event  Score\n------------',
            'body': 'E%03d   %.2f',
            'mean': 'Mean   %.2f',
            'footer': ''}}

    mean = np.mean([tt[1] for tt in class_perfs])
    if print_format == 'html':
        results = [
            tt[1]
            for tt in sorted(class_perfs, key=lambda tt: tt[0])]
    elif print_format == 'stdout':
        results = class_perfs
    else:
        assert False, "Unknown printing format."

    print printer[print_format]['header']
    for rr in results:
        print printer[print_format]['body'] % rr
    print printer[print_format]['mean'] % mean
    print printer[print_format]['footer']

    
def test_listfile((listfile, ssl, selection, normalizers, classifiers, probas)): 

    # load input 

    te_data = []  
    nchannel = 0

    global power
    
    for cname in selection:

        feature, params = combinations[cname]

        print "load feature %s, list %s %s" % (cname, listfile, "slice %d/%d" % ssl if ssl else '')
        if ssl: 
                params = params.copy()
                params['ssl'] = ssl
                
        te_data_i, te_labels_i, te_vidnames_i = get_data(feature, listfile, **params)

        (mu, sigma) = normalizers[cname] 

        te_data_i, _, _ = standardize(te_data_i, mu, sigma)
        te_data_i = power_normalize(te_data_i, power)
        te_data_i = L2_normalize(te_data_i)
        te_data_i = te_data_i.astype(np.float32)

        te_data.append(te_data_i)
        
        if nchannel == 0:
            te_labels, te_vidnames = te_labels_i, te_vidnames_i
        else:
            assert dataset_test != "tv11val" or np.all(te_labels == te_labels_i), pdb.set_trace()
            assert te_vidnames_i == te_vidnames          

        nchannel += 1

        sys.stdout.flush()
    
    if dataset_test == "tv11" and te_labels.max() == -1:
        print "correcting labels"
        l = dict(parse_listfile(listfile))
        for i in range(len(te_labels)):
            te_labels[i] = l[te_vidnames[i]]

    # do testing 

    ntest = te_labels.size

    perfs = []
    scores_per_class = {}
    for cl in classes:

        weights, bias = classifiers[cl][:2]

        if dataset == 'tv11val':
            subset_test = np.nonzero((te_labels == 0) | (te_labels == cl))[0]
        else: 
            subset_test = np.arange(te_labels.size)
            
        te_labels_cl = ((te_labels[subset_test] == cl) * 2 - 1)

        # accumulate scores for all channels 
        scores = np.dot(weights[0], te_data[0].T) + bias

        for chan in range(1, nchannel):        
            scores += np.dot(weights[chan], te_data[chan].T)

        if probas: 
                probA, probB = classifiers[cl][2:4]
                scores = scores_to_probas(scores, probA, probB)

        # store results 
        scores_per_class[cl] = subset_test, scores, te_labels_cl

        
        # if we have ground-truth, evaluate
        if dataset_test == 'tv11val' or dataset_test.startswith('tv11dev') or test_on_train:
            if criterion == 'ap':           
                perf = average_precision(te_labels_cl, scores[subset_test])
                print "ap=", perf
            elif criterion == 'dcr' or criterion == 'sdcr':
                perf = compute_dcr(te_labels_cl, scores[subset_test])
                print "dcr=", perf
            perfs.append(perf)

    # return results to be stored
    return te_vidnames, scores_per_class

        

#############################################
# Main script


def usage():
    print >>sys.stderr, """python early_fusion.py [options] channel1 channel2 ...

Trains classifiers by cross-validating C and the weight of positives + weights for
the kernel matrices. Also performs testing 

Options:

-train              do only training
-test               do only testing
-ap                 use average precision to select the best combination (this is the default)
-sdcr               use "surrogate DCR" to select the best combination 
-dcr                use DCR to select the best combination 
-tv11val            learn on tv11 balanced set, test on tv11 balanced test set
-tv11               learn on whole tv11 train set, test on tv11 test set
-tv11devt           learn on tv11 events, test on tv11 DEV-T (see Tang et al, 2012) 
-tv11devo           learn on tv11 events, test on tv11 DEV-O (see Tang et al, 2012) 
-tv12wacv           use splits defined in WACV paper
-tv12               idem for tv12
-tv12test11         train on tv12, test tv11
-tv13               idem for tv13
-tv13test11         train on tv13, test tv11
-tv13adhoc          learn for the AdHoc task
-cl classno         train/test only this class
-key name           use this name as prefix for all output
-probas             output probabilities
-nomixw             fix mixture weights to 1 (= simple sum of kernels)
-ssl n              load test data in this many slices (to limit memory consumption)
-html               prints the results using the HTML format
-power <p>          use the power p for power-normalization at final stage
-fixedc <C>         use the specified value of SVM C without cross-validation

# pythonpath should include yael and the threadsafe_classifier subdir

export PYTHONPATH=/home/lear/douze/src/yael:threadsafe_classifier/

"""

    sys.exit(1)

if __name__ == '__main__':

    args = sys.argv[1:]

    selection = []
    cl_subset = []

    dataset = 'tv12'
    key = ''
    todo = 'train test'
    probas = False
    test_on_train = False
    n_thread = yael.count_cpu()
    crossval_mixweights = True
    use_other_negs = True
    criterion = 'ap'
    print_format = 'stdout'
    ssl = 0
    power = 0.5
    fixedc = False    
    
    while args:
        a = args.pop(0)
        if a in combinations: selection.append(a)
        elif a in ('-h', '--help'):              usage()
        elif a == '-probas':                     probas = True
        elif a == '-ap':                         criterion = 'ap'
        elif a == '-dcr':                        criterion = 'dcr'
        elif a == '-sdcr':                       criterion = 'sdcr'
        elif a == '-tv11':                       dataset = 'tv11'
        elif a == '-tv11devt':                   dataset = 'tv11devt'
        elif a == '-tv11devo':                   dataset = 'tv11devo'
        elif a == '-tv11val':                    dataset = 'tv11val'
        elif a == '-tv12wacv':                   dataset = 'tv12wacv'
        elif a == '-tv12':                       dataset = 'tv12'
        elif a == '-tv12test11':                 dataset = 'tv12test11'; todo = 'test'
        elif a == '-tv13':                       dataset = 'tv13'
        elif a == '-tv13test11':                 dataset = 'tv13test11'; todo = 'test'
        elif a == '-tv13adhoc':                  dataset = 'tv13adhoc'

        elif a == '-tv14run11':                  dataset = 'tv14run11'
        
        elif a == '-cl':                         cl_subset += [int(x) for x in args.pop(0).split(',')]
        elif a == '-key':                        key = args.pop(0)
        elif a == '-train':                      todo = 'train'
        elif a == '-test':                       todo = 'test'    
        elif a == '-nomixweight':                crossval_mixweights = False
        elif a == '-nomixw':                     crossval_mixweights = False
        elif a == '-test-on-train':              todo = 'test'; test_on_train = True
        elif a == '-nootherneg':                 use_other_negs = False
        elif a == '-nt':                         n_thread = int(args.pop(0))
        elif a == '-ssl':                        ssl = int(args.pop(0))
        elif a == '-html':                       print_format = 'html'
        elif a == '-power':                      power = float(args.pop(0))
        elif a == '-fixedc':                     fixedc = float(args.pop(0))
        else:
            raise RuntimeError('unknown arg '+ a)

    if not key: 
        key = ",".join(selection)
        if not crossval_mixweights: key += '-nomixw'
        if not use_other_negs: key += '-nootherneg'
        if power != 0.5: key += '-power%0.2f' % power
        if fixedc: key += re.sub('\+', '', '-c%0.3e' % fixedc)        
        
    dataset_test = dataset
    
    if dataset == 'tv11val':
        classes = range(1, 16)
        data_prefix_test = data_prefix = "data/validationTV11_scores/ef_" + key
    elif dataset == 'tv11':
        classes = range(6, 16)
        data_prefix_test = data_prefix = "data/finalTV11_classifiers/ef_" + key
    elif dataset == 'tv11devt':
        classes = range(1, 6)
        data_prefix_test = data_prefix = "data/DEV-O_DEV-T_classifiers/ef_" + key
    elif dataset == 'tv11devo':
        classes = range(6, 16)
        data_prefix_test = data_prefix = "data/DEV-O_DEV-T_classifiers/ef_" + key
    elif dataset == 'tv11multiclass':
        classes = range(6, 16)
        data_prefix_test = data_prefix = "data/multiclass_classifiers/ef_" + key
    elif dataset == 'tv12wacv':
        classes = range(1, 16) + range(21, 31)
        data_prefix_test = data_prefix = "data/wacv_classifiers/ef_" + key
    elif dataset == 'tv12':
        classes = range(6, 16) + range(21, 31)
        data_prefix_test = data_prefix = "data/finalTV12/ef_" + key
    elif dataset == 'tv13':
        classes = range(6, 16) + range(21, 31)
        data_prefix_test = data_prefix = "data/finalTV13/ef_" + key
    elif dataset == 'tv13adhoc':
        classes = range(31, 41)
        data_prefix_test = data_prefix = "data/finalTV13adhoc/ef_" + key
    elif dataset == 'tv12test11':
        classes = range(6, 16)
        dataset_test = 'tv11'
        dataset = 'tv12'
        data_prefix = "data/finalTV12/ef_" + key
        data_prefix_test = "data/finalTV11_train_on_12/ef_" + key
    elif dataset == 'tv13test11':
        classes = range(6, 16)
        dataset_test = 'tv11'
        dataset = 'tv13'
        data_prefix = "data/finalTV13/ef_" + key
        data_prefix_test = "data/finalTV11_train_on_13/ef_" + key

    elif dataset == 'tv14run11':
        classes = range(6, 16)
        data_prefix = "/home/clear/dataset/trecvid14/tv11/" + key        
    
    else:
        assert False

    print "putting classifiers and scores at", data_prefix
            
    if cl_subset:
        classes = cl_subset

    if 'train' in todo:
        do_train()

    if 'test' in todo:
        do_test()
