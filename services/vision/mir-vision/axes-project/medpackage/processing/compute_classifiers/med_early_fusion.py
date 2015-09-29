

import math, sys, cPickle, pdb, os, re, random, errno, time, multiprocessing
import os.path

import numpy as np

from crossval import CrossvalOptimization

from collections import defaultdict

from yael import yael, ynumpy

from yael.threads import ParallelIter

from video_descriptors import label_map, get_data, data_normalize, combinations, standardize, power_normalize, L2_normalize, parse_listfile, data_normalize_inplace

from video_descriptors import compute_dcr, average_precision

import video_descriptors_tv14

from early_fusion import libsvm_train, scores_to_probas, combine_kernels, Stats, Optimizer

#sys.path.append('/home/lear/oneata/src/med12lear/threadsafe_classifier')
MED_DIR = os.getenv("MED_BASEDIR")
COLLECTION_DIR = os.getenv("COLLECTION_DIR")

sys.path.append(MED_DIR + 'compute_classifiers/med12lear/threadsafe_classifier')
import libsvm_precomputed

import scipy

dense_type = type(np.zeros(1, dtype=np.float32))

#############################################
def prepare_dir(fname):
    "make sure the enclosing directory of this file exists"
    dirname=fname[:fname.rfind('/')]
    if os.access(dirname,os.W_OK):
        return
    try:
        os.makedirs(dirname)
    except OSError,e:
        if e.errno!=errno.EEXIST:
            raise

def is_good(X):
    return np.logical_and(np.isfinite(X), np.absolute(X)<1e20)

def check_kernel(X, K): 
    print "check consistency of precomputed kernel"
    " check if it is reasonable that K == X X^T "
    n, d = X.shape
    if not K.shape == (n, n):
        print "cached kernel has shape %s vs. descriptor matrix %s" % (K.shape, X.shape)
        return False
    #assert K.shape == (n, n), "cached kernel has shape %s vs. descriptor matrix %s" % (K.shape, X.shape)
    rnd = random.Random(0)
    for it in range(20):
        i = rnd.randrange(n)
        j = rnd.randrange(n)
        if not (np.abs(K[i, j] - np.dot(X[i], X[j])) < 1e-4 * (np.dot(X[i], X[i]) + np.dot(X[j], X[j]))):
            print "kernel cache out of sync"
            return False
        #assert np.abs(K[i, j] - np.dot(X[i], X[j])) < 1e-4 * (np.dot(X[i], X[i]) + np.dot(X[j], X[j])), "kernel cache out of sync"
    return True
        


def load_selection_train(event_class, selection, normalizers_in = None, power = 0.5, kernel_cache_path = None, just_kernel_matrix = False, needs_sorting = True):

    Kxx = []
    tr_data = []

    nchannel = 0
    normalizers = {}

    for cname in selection:
        
        print "  (%d/%d) - %s" % ( nchannel + 1, len(selection), time.ctime() )

        kernel_cache_fname = None
        Kxx_i = None
        
        if kernel_cache_path != None:        
            kernel_cache_fname = kernel_cache_path + cname + ".fvecs"
            if os.access(kernel_cache_fname, os.R_OK): 
                print "Load precomputed kernel from", kernel_cache_fname
                Kxx_i = ynumpy.fvecs_read(kernel_cache_fname)        

        no_train_matrix = just_kernel_matrix and Kxx_i != None
        
        tr_data_i, tr_labels_i, tr_vidnames_i = video_descriptors_tv14.get_train_descriptors(
            event_class, cname, no_train_matrix = no_train_matrix)
        if tr_vidnames_i != list(sorted(tr_vidnames_i)) and needs_sorting:
            print "sorting vidnames..."
            sv = [(vidname, i) for i, vidname in enumerate(tr_vidnames_i)]
            sv.sort()
            tr_vidnames_i = [vidname for vidname, i in sv]
            perm = np.array([i for vidname, i in sv])
            tr_labels_i = tr_labels_i[perm]
            if tr_data_i != None: 
                tr_data_i = tr_data_i[perm,:]
        
        if tr_data_i == None: 
            pass
        elif type(tr_data_i) == dense_type: #dense matrix
            #print "Using dense matrix"
            if not no_train_matrix: 

                nandescs = np.nonzero(np.any(np.logical_not(is_good(tr_data_i)), axis = 1))[0]
                if nandescs.size > 0:
                    print "WARN %d/%d nan descriptors. Replacing with 0." % (nandescs.size, tr_data_i.shape[0])
                    tr_data_i[nandescs, :] = 0

                    print "data range: [%g, %g]"%(tr_data_i.min(), tr_data_i.max())
                    
                if not normalizers_in:        
                    #print "normalizing (computing normalizers)"
                    # normalizers computed only on background videos

                    background_vids = (tr_labels_i <= 0)
                    print "normalizing from %d videos, power norm %g" % (background_vids.sum(), power)
                    nvid = background_vids.size
                    nbg = background_vids.sum()
                    if np.all(background_vids[-nbg:]):
                        background_vids = slice(nvid - nbg, nvid)
                    mu, sigma = data_normalize_inplace(tr_data_i, power = power, compute_norm_subset = background_vids)          

                    # tr_data_i, mu, sigma = data_normalize(tr_data_i, power=power, compute_norm_subset = background_vids)

                    normalizers[cname] = (mu, sigma)
                else:
                    print "normalizing (provided mu, sigma)"
                    (mu, sigma) = normalizers_in[cname] 

                    tr_data_i, _, _ = standardize(tr_data_i, mu, sigma)
                    tr_data_i = power_normalize(tr_data_i, power)
                    tr_data_i = L2_normalize(tr_data_i)
                    tr_data_i = tr_data_i.astype(np.float32)


                assert np.all(np.isfinite(tr_data_i))

                print "compute kernels train %d*%d" % tr_data_i.shape
            
                
                if Kxx_i == None or check_kernel(tr_data_i, Kxx_i) == False:     
                    Kxx_i = np.dot(tr_data_i, tr_data_i.T)
                    if kernel_cache_fname and tr_data_i.shape[1] > tr_data_i.shape[0]:
                        print "storing kernel cache to", kernel_cache_fname
                        ynumpy.fvecs_write(kernel_cache_fname, Kxx_i)
        else: #sparse matrix (for ocr and such)
            row_norms = np.sqrt(np.array(tr_data_i.multiply(tr_data_i).sum(axis=1))[:,0])
            row_indices, col_indices = tr_data_i.nonzero()
            tr_data_i.data /= row_norms[row_indices]

            Kxx_i = tr_data_i.dot(tr_data_i.T).toarray().astype(np.float32)
            if kernel_cache_fname and tr_data_i.shape[1] > tr_data_i.shape[0]:
                print "storing kernel cache to", kernel_cache_fname
                ynumpy.fvecs_write(kernel_cache_fname, Kxx_i)
                

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

def store_normalizers(data_prefix, normalizers):
    """ store normalizers (mean and stddev) or, if file exists, check
    that its content is the same as the input"""
    
    norm_fname = data_prefix + "_normalizer.pickle"
    print "storing", norm_fname  
    cPickle.dump(normalizers, open(norm_fname, "w"))

def store_or_check_normalizers(data_prefix, normalizers):
    """ store normalizers (mean and stddev) or, if file exists, check
    that its content is the same as the input"""
    
    norm_fname = data_prefix + "_normalizer.pickle"

    if os.access(norm_fname, os.R_OK): 
        print "comparing with", norm_fname
        ref_normalizers = cPickle.load(open(norm_fname, "r"))
        for ch in normalizers:
            for a, b in zip(normalizers[ch], ref_normalizers[ch]):
                # print "   %s cmp norm" % ch, a[:10], b[:10]
                assert np.abs(a - b).sum() < 1e-5 * (np.abs(b) + np.abs(a)).sum(), "Normalizer different from stored one"
    else: 
        print "storing", norm_fname  
        cPickle.dump(normalizers, open(norm_fname, "w"))

def vid_labels_to_binary(subset_vidnames, inv_tr_vidnames, clno = -1):
    """ vid_subset = list of videos,
    vid_subset_labels = corresponding list of "positive", "negative", "near-miss"
    returns indices of training subset and +1/-1 labels for them as int32 arrays
    """
    # actually 4 possible lables: 0 = near-miss, 2 = static positive
    m = {"background": -1, "near-miss": 0, "positive": 1}
    subset_vidnames = [(vid, m[gt]) for (vid, gt) in subset_vidnames]

    subset = np.array([inv_tr_vidnames[vid] for vid, label in subset_vidnames], dtype = np.int32)
    tr_labels_cl = np.array([label for vid, label in subset_vidnames], dtype = np.int32)

    # map fake 231..235 classes to 31..35 
    clno = clno % 100

    static_positive_name = 'static_positive_E%03d' % clno
    if static_positive_name in inv_tr_vidnames: 
        subset.resize(subset.size + 1)
        tr_labels_cl.resize(tr_labels_cl.size + 1)
        subset[-1] = inv_tr_vidnames[static_positive_name]
        tr_labels_cl[-1] = 2

    return subset, tr_labels_cl


def training_subset_for_class(indata, cl, inv_tr_vidnames):
    """ choose a training subset for the given dataset and class
    inv_tr_vidnames is a dictionary that maps HVCxxx video ids to indices in the descriptor array 
    """
    dataset = indata.dataset
    vid_subset, vid_subset_labels = video_descriptors_tv14.parse_csv(dataset, cl)
    clno = int(cl[1:4])
    return vid_labels_to_binary(zip(vid_subset, vid_subset_labels), inv_tr_vidnames, clno = clno)


# what to do with near misses: 1 = positive, -1: negative, 0: remove
# for the splits (train, test)

nearmiss_status_table = {
    'allpos': (1,   1),
    'pos':    (1,  -1),
    'remove': (0,  -1),
    'neg':    (-1, -1)
    }
    

                                                        
def train_on_full_set(params, Kxx_cl, tr_labels_cl,
                      tr_data, subset, nearmiss_status):
    """ when the parameters are determined by cross-validation, use
    all the training data to train to get the final classifier.

    Kxx_cl is restricted to training data of the current class: Kxx_cl.shape[0] = subset.size
    tr_labels_cl contains 0, -1, +1, size subset.size
    tr_data[subset] is the training matrix corresponding to Kxx_cl
    """    
    
    nchannel = len(Kxx_cl)   
    Kxx_c = combine_kernels(Kxx_cl, params)        

    train_subset = np.arange(tr_labels_cl.size, dtype = np.int32)

    if np.any(tr_labels_cl == 0):
        
        todo, _ = nearmiss_status_table[nearmiss_status]        

        if todo == 0:
            train_mask = (tr_labels_cl != 0)
            subset = subset[train_mask]
            train_subset = np.nonzero(train_mask)[0].astype('int32')
        else: 
            tr_labels_cl = tr_labels_cl.copy()
            tr_labels_cl[tr_labels_cl == 0] = todo

    if np.any(tr_labels_cl == 2): 
        tr_labels_cl = tr_labels_cl.copy()
        tr_labels_cl[tr_labels_cl == 2] = 1
        
    print "Re-training on full set with %dp + %dn" % (
        (tr_labels_cl[train_subset] == 1).sum(),
        (tr_labels_cl[train_subset] == -1).sum())
    
    dual_coef, bias, probA, probB = libsvm_train(Kxx_c, tr_labels_cl,
                                                 train_subset,
                                                 c = params['c'],
                                                 pos_weight = params['positive_weight'],
                                                 probability = 1)

    
    ntrain = tr_data[0].shape[0]
    dual_coef_full = np.zeros(ntrain)
    dual_coef_full[subset] = dual_coef

    # extract W
    if type(tr_data[0]) == dense_type:
        print "WEIGHTS: DENSE"
        weights = [np.dot(dual_coef_full, tr_data[0])]
    else:
        print "WEIGHTS: SPARSE"
        weights = [scipy.sparse.csr_matrix(dual_coef_full).dot(tr_data[0])]
       
    for chan in range(1, nchannel):
        # apply channel weights to W vector
        name = 'weight_channel_%d' % chan 
        factor = params[name] if name in params else 1.0
        if type(tr_data[chan]) == dense_type:
            weights.append(factor * np.dot(dual_coef_full, tr_data[chan]))
        else:
            weights.append(factor * scipy.sparse.csr_matrix(dual_coef_full).dot(tr_data[chan]))
                

    return weights, bias, probA, probB

                          

class OptimizerWith0(Optimizer):
    """ same as optimizer, but accepts label 0 = near-miss"""
    
    def __init__(self, Kxx, cx, criterion, nearmiss_status, test_fraction = 0.25, nfold = 10):
        CrossvalOptimization.__init__(self, None, cx)
        self.Kxx = Kxx
        self.cx = None
        self.criterion = criterion
        
        # make the folds

        self.nfold = nfold

        pos = np.nonzero(cx == 1)[0].astype(np.int32); npos = pos.size
        neg = np.nonzero(cx == -1)[0].astype(np.int32); nneg = neg.size
        zeros = np.nonzero(cx == 0)[0].astype(np.int32); n0 = zeros.size
        spos = np.nonzero(cx == 2)[0].astype(np.int32); nspos = spos.size
        npos_test = int(npos * test_fraction)
        nneg_test = int(nneg * test_fraction)
        n0_test = int(n0 * test_fraction)

        print "Drawing %d folds of train %dp + %dsp + %dn + %dnm / test %dp + %dn + %dnm (nearmiss_status = %s)" % (
            self.nfold,
            npos - npos_test, nspos, nneg - nneg_test, n0 - n0_test,
            npos_test, nneg_test, n0_test,
            nearmiss_status)
        
        splits = []
        rnd = np.random.RandomState(0)
        for i in range(self.nfold):
            ppos = rnd.permutation(pos)
            pneg = rnd.permutation(neg)
            p0 = rnd.permutation(zeros)

            cx_fold = np.zeros(cx.size, dtype = 'int32')
            cx_fold[pos] = 1
            cx_fold[spos] = 1
            cx_fold[neg] = -1

            train = [spos, pneg[nneg_test:], ppos[npos_test:]]
            test = [pneg[:nneg_test], ppos[:npos_test]]                

            todo_train, todo_test = nearmiss_status_table[nearmiss_status]

            def maybe_append_and_set_label(l, zeros_split, todo):
                if todo == 0: return
                l.append(zeros_split)
                cx_fold[zeros_split] = todo
                    
            maybe_append_and_set_label(train, p0[n0_test:], todo_train)
            maybe_append_and_set_label(test, p0[:n0_test], todo_test)

            train = np.hstack(train)
            test = np.hstack(test)
            if i == 1:
                print "     -> %dp + %dn / %dp + %dn" % (
                    (cx_fold[train] == 1).sum(), (cx_fold[train] == -1).sum(), 
                    (cx_fold[test] == 1).sum(),  (cx_fold[test] == -1).sum())
            splits.append((train, test, cx_fold))
        self.splits = splits


def do_train(indata, settings, data_prefix, kernel_cache_path): 

    event_class = indata.event_class
    selection = indata.selection
    needs_sorting = indata.needs_sorting
    
    Kxx, tr_data, tr_vidnames, tr_labels, normalizers = load_selection_train(
        event_class, selection, None, power = settings.power, kernel_cache_path = kernel_cache_path, needs_sorting = needs_sorting)

    store_normalizers(data_prefix, normalizers) 

    nchannel = len(Kxx)
    ntrain = tr_labels.size

    inv_tr_vidnames = {
        name: no for no, name in enumerate(tr_vidnames)}
    
    print "################## train class", event_class

    #subset, tr_labels_cl = training_subset_for_class(indata, event_class, inv_tr_vidnames)

    # TODO: remove the need for 'subset'.
    subset = []
    for i in range(len(tr_vidnames)):
        subset.append(i)
    subset = np.array(subset, dtype="int32")    
    tr_labels_cl = np.array(tr_labels, dtype="int32")
    
    # kernel matrix restricted to subset
    Kxx_cl = [
        ynumpy.extract_rows_cols(K, subset, subset)
        for K in Kxx]

    Kxx_cl_orig = Kxx_cl # needed for final estimation
    if not settings.crossval_mixweights:
        print "combining kernels once..."
        Kxx_cl = [combine_kernels(Kxx_cl, {})]

    if np.any(np.abs(tr_labels_cl) != 1): 

        print "Training with %dp + %dsp + %dn + %dnm" % (
            (tr_labels_cl == 1).sum(), 
            (tr_labels_cl == 2).sum(), 
            (tr_labels_cl == -1).sum(),
            (tr_labels_cl == 0).sum())

        opt = OptimizerWith0(Kxx_cl, tr_labels_cl, settings.criterion, indata.nearmiss_status,
                             test_fraction = settings.fold_test_fraction)
    else:
        
        print "Training with %dp + %dn" % (
            (tr_labels_cl > 0).sum(), 
            (tr_labels_cl < 0).sum())
    
        opt = Optimizer(Kxx_cl, tr_labels_cl,
                        settings.criterion, test_fraction = settings.fold_test_fraction)

    opt.init_point = {
        'c': 9.0,
        'positive_weight': 1.0}
    
    opt.ranges = {
        'c': [1./9, 1./3, 1.0, 3.0, 9.0, 27.0, 81.0, 243.0, 729.0, 2187.0],
        'positive_weight': [2.**i for i in range(-10, 10)],
        }

    if settings.crossval_mixweights:
        for chan in range(1, len(Kxx_cl)):
            name = 'weight_channel_%d' % chan 
            opt.init_point[name] = 1.0
            opt.ranges[name] = [0.05 * i for i in range(50)]      

    for pname in settings.fixedparams:
        val = settings.fixedparams[pname]
        opt.init_point[pname] = val
        opt.ranges[pname] = [val]

    all_fixed = all([len(opt.ranges[name]) == 1 for name in opt.init_point])

    if all_fixed:
        print "all parameters are fixed, not cross-validating"
        params = opt.init_point
    else:             
        opt.n_thread = settings.n_thread

        # try hard to optimize...
        opt.score_tol_factor = 0.66
        
        params = opt.optimize()
        params = params[0][0]

    print "Estimating on full train set with", params     

    weights, bias, probA, probB = train_on_full_set(params, Kxx_cl_orig, tr_labels_cl, tr_data, subset, indata.nearmiss_status)

    cl_fname = data_prefix + ".pickle"
    print "storing", cl_fname  
    cPickle.dump((weights, bias, probA, probB, params), open(cl_fname, "w"))
        
#############################################
# Load data and do test
            
def do_test(indata, settings, data_prefix, data_prefix_test): 

    print "############################## test"

    norm_fname = data_prefix + "_normalizer.pickle"
    print "loading", norm_fname  
    normalizers = cPickle.load(open(norm_fname, "r"))

    classes = indata.classes
    
    classifiers = {}
    
    # load classifiers

    for cl in classes:
        cl_fname = data_prefix + "_classifier_%s.pickle" % cl
        print "loading", cl_fname    
        classifiers[cl] = cPickle.load(open(cl_fname, "r"))

    run_test_for_classes(indata, settings, normalizers, classifiers, data_prefix_test)


def run_test_for_classes(indata, settings, normalizers, classifiers, data_prefix_test):

    classes = classifiers.keys()
    classes.sort()

    print "Output results to", data_prefix_test
    of = {    
        cl: open(data_prefix_test + "_" + cl + ".dat", "w")
        for cl in classes}

    # run tests (in // if ssl specified)

    ssl = settings.ssl
    n_thread = settings.n_thread
    t0 = time.time()

    if not ssl: 
        te_vidnames, scores_per_class = test_listfile((None, indata, settings, normalizers, classifiers))
        scores_src = [(te_vidnames, scores_per_class)]
    else:        
        #scores_src = ParallelIter(
        #    n_thread, [((i, ssl), indata, settings, normalizers, classifiers)
        #               for i in range(ssl)], test_listfile)
        pool = multiprocessing.Pool(n_thread)
        scores_src = pool.imap(test_listfile,[((i, ssl), indata, settings, normalizers, classifiers) for i in range(ssl)])

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
        print "Stored %d scores in %.2f s" % (n_stored, time.time() - t0)
    # output files closes automatically
    print "Results stored to basename", data_prefix_test
    
def test_listfile((ssl, indata, settings, normalizers, classifiers)): 

    # load input 
    classes = classifiers.keys()
    classes.sort()

    te_data = []  
    nchannel = 0
    dataset = indata.dataset

    verbose = 0
    #tt = time.time()
    for cname in indata.selection:

        assert cname in video_descriptors_tv14.available_descriptors
        if verbose: 
            print "load feature %s, %s" % (cname, "slice %d/%d" % ssl if ssl else '')
        #t = time.time()
        te_data_i, te_labels_i, te_vidnames_i = video_descriptors_tv14.get_test_descriptors(dataset, cname, ssl)
        #print "Loading time:", time.time()-t
        
        if type(te_data_i) == dense_type:
            (mu, sigma) = normalizers[cname] 

            te_data_i, _, _ = standardize(te_data_i, mu, sigma)
            te_data_i = power_normalize(te_data_i, settings.power)
            te_data_i = L2_normalize(te_data_i)
            te_data_i = te_data_i.astype(np.float32)
        else:
            row_norms = np.sqrt(np.array(te_data_i.multiply(te_data_i).sum(axis=1))[:,0])
            row_indices, col_indices = te_data_i.nonzero()
            te_data_i.data /= row_norms[row_indices]
        
        te_data.append(te_data_i)
        
        if nchannel == 0:
            te_labels, te_vidnames = te_labels_i, te_vidnames_i
        else:
            assert np.all(te_labels == te_labels_i), pdb.set_trace()
            assert te_vidnames_i == te_vidnames          

        nchannel += 1

        sys.stdout.flush()
    
    # do testing 

    # ntest = te_data[0].shape[0]
    ntest = te_labels.size

    scores_per_class = {}
    for cl in classes:

        weights, bias = classifiers[cl][:2]
        assert len(weights) == nchannel, "%d weights in classifier, %d channels in selection" % (
            len(weights), nchannel)

        eno = int(cl.split("_")[0][1:])

        te_labels_cl = ((te_labels == eno) * 2 - 1)
        subset_test = np.arange(te_labels.size)
        
        if verbose:
            print "    eqname %s %dp + %dn" % (cl, (te_labels_cl > 0).sum(), (te_labels_cl < 0).sum()) 

        # accumulate scores for all channels 

        if type(te_data[0]) == dense_type:
            # print "weights shape %s test data shape %s" % (weights[0].shape, te_data[0].shape)
            scores = (np.dot(weights[0], te_data[0].T) + bias).flatten()
            assert scores.shape[0] == subset_test.shape[0],(scores.shape[0],subset_test.shape[0])
        else:
            assert type (weights[0]) == scipy.sparse.csr_matrix, type(weight[0])
            scores = (weights[0].dot(te_data[0].T).toarray().astype(np.float32) + bias).reshape(-1)
        for chan in range(1, nchannel):      
            if type(te_data[0]) == dense_type:
                scores += np.dot(weights[chan], te_data[chan].T)
            else:
                assert type (weights[chan]) == scipy.sparse.csr_matrix, type (weights[chan])
                scores += (weights[chan].dot(te_data[chan].T).toarray().astype(np.float32) + bias).reshape(-1)

        # store results 
        scores_per_class[cl] = subset_test, scores, te_labels_cl

    # return results to be stored
    #print time.time() - tt
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
-ap10               use average precision interpolated at 10 pts

-tv11               learn on whole tv11 train set, test on tv11 test set

-cl classno         train/test only this class
-key name           use this name as prefix for all output
-nomixw             fix mixture weights to 1 (= simple sum of kernels)
-ssl n              load test data in this many slices (to limit memory consumption)
-html               prints the results using the HTML format
-power <p>          use the power p for power-normalization at final stage
-fixedc <C>         use the specified value of SVM C without cross-validation
-fixedposw <pw>     set constant positive weight
-nearmiss (pos|neg|remove)
                    near-miss handling

# pythonpath should include yael and the threadsafe_classifier subdir

export PYTHONPATH=/home/lear/douze/src/yael:threadsafe_classifier/

"""

    sys.exit(1)


def expand_dataset(indata, key = ''):
    """ figure out which classes the user wants to process, update the indata structure
    and return the paths where data can be found / stored
    """

    assert(indata.event_class != "")
    if not os.path.isdir(COLLECTION_DIR + "/events/" + indata.event_class + "/workdir"):
        print "\nERROR: Could not find event class \"" + indata.event_class + "\", check arguments.\n"
        assert False
    
    kernel_cache_path = None
    """
    elif indata.dataset == "tv14test":
        data_prefix = "/home/clear/dataset/trecvid14/MED14-Test/classifiers/early_fusion/" + key
        data_prefix_test = "/home/clear/dataset/trecvid14/MED14-Test/scores/early_fusion/" + key
    """ 
    # Disabled kernel cache path for safe concurrent execution
    #kernel_cache_path = MED_DIR + "compute_classifiers/kernel_cache/early_fusion/"
    data_prefix = COLLECTION_DIR + "/events/" + indata.event_class + "/classifiers/" + key + "_classifier"
    data_prefix_test = COLLECTION_DIR + "/events/" + indata.event_class + "/classifiers/early_fusion/scores/" + key
        
    #prepare_dir(kernel_cache_path)
    prepare_dir(data_prefix)
    prepare_dir(data_prefix_test)
    
    return kernel_cache_path, data_prefix, data_prefix_test 


class Struct:
    def __init__(self, **kwargs):
        for k, v in kwargs.items():
            setattr(self, k, v)

if __name__ == '__main__':

    key = ''
    todo = 'train test'

    indata = Struct(
        event_class = '',
        selection = [],
        versions = [],
        exconfig = "",
        nearmiss_status = 'allpos',
        needs_sorting = True)
    
    settings = Struct(   
        crossval_mixweights = True, 
        criterion = 'ap',
        print_format = 'stdout',
        power = 0.5, 
        fixedparams = {}, 
        fold_test_fraction = 0.25, 
        ssl = 0,
        n_thread = yael.count_cpu())
        
    args = sys.argv[1:]

    while args:
        a = args.pop(0)        
        if   a in ('-h', '--help'):              usage()

        elif a == '--key':                        key = args.pop(0)
        elif a == '--train':                      todo = 'train'
        elif a == '--test':                       todo = 'test'    

        elif a == '--event-class':
            event_class = args.pop(0)
            indata.event_class = event_class
            
            # example: trecvid_all, thumos14

        elif a == '--nosort':                     indata.needs_sorting = False
        
        #elif a == '--cl':                         indata.classes = [int(x) for x in args.pop(0).split(',')]
        elif a == '--ex':                         indata.exconfig = args.pop(0)
        elif a == '--versions':
            arg = args.pop(0)
            if '-' in arg:
                a,z = arg.split('-')
                indata.versions = [chr(i) for i in range(ord(a), ord(z) + 1)]
            else: 
                indata.versions = arg.split(',')
        elif a == '--nearmiss':                   indata.nearmiss_status = args.pop(0)

        # Select channels
        #elif a in video_descriptors_tv14.available_descriptors:
        #                                         indata.selection.append(a)
        
        elif a == '--ap':                         settings.criterion = 'ap'
        elif a == '--ap10':                       settings.criterion = 'ap10'
        elif a == '--nomixweight':                settings.crossval_mixweights = False
        elif a == '--nomixw':                     settings.crossval_mixweights = False
        elif a == '--nt':                         settings.n_thread = int(args.pop(0))
        elif a == '--ssl':                        settings.ssl = int(args.pop(0))
        elif a == '--html':                       settings.print_format = 'html'
        elif a == '--power':                      settings.power = float(args.pop(0))
        elif a == '--fixedc':                     settings.fixedparams['c'] = float(args.pop(0))
        elif a == '--fixedposw':                  settings.fixedparams['positive_weight'] = float(args.pop(0))
        elif a.startswith('--fixedchanw'):
            chan = int(a.split('w')[1])
            settings.fixedparams['weight_channel_%d' % chan] = float(args.pop(0))
        elif a == '--fold_test_fraction':         settings.fold_test_fraction = float(args.pop(0))
        else:
            indata.selection.append(a)
            #raise RuntimeError('unknown arg '+ a)


    if not key: 
        #key = event_class.split('/')[-1:][0] + "_" + "_".join(indata.selection)
        key = event_class.split('/')[-1]
        if not settings.crossval_mixweights: key += '-nomixw'
        if settings.power != 0.5: key += '-power%0.2f' % settings.power
        for pname in sorted(settings.fixedparams.keys()):
            key += '-%s%g' % (pname, settings.fixedparams[pname])
        if settings.fold_test_fraction != 0.25:
            key += "-foldtest%g" % settings.fold_test_fraction
        if indata.nearmiss_status != "allpos":
            key += '-nearmiss_' + indata.nearmiss_status

    kernel_cache_path, data_prefix, data_prefix_test = expand_dataset(indata, key)

    print "Classifier will be saved at", data_prefix + ".pickle"

    if 'train' in todo:
        do_train(indata, settings, data_prefix, kernel_cache_path)

    if 'test' in todo:
        do_test(indata, settings, data_prefix, data_prefix_test)
