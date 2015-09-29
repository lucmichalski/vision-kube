import cPickle, os, thread, pdb, sys, struct
from yael import ynumpy, yael, threads
from yael.yael import gmm_read

import video_descriptors_tv14

import numpy as np
import warnings


label_map = {
     0: 'null',
     1: 'board_trick',
     2: 'feeding_an_animal',
     3: 'landing_a_fish',
     4: 'wedding_ceremony',
     5: 'woodworking_project',
     6: 'birthday_party',
     7: 'changing_vehicle_tire',
     8: 'flash_mob_gathering',
     9: 'unstuck_vehicle',
    10: 'grooming_an_animal',
    11: 'making_a_sandwich',
    12: 'parade',
    13: 'parkour',
    14: 'repairing_an_appliance',
    15: 'sewing_project',
    16: 'doing_homework_or_studying',
    17: 'hide_and_seek',
    18: 'hiking',
    19: 'installing_flooring',
    20: 'writing_text',
    21: 'attempting_a_bike_trick',
    22: 'cleaning_an_appliance',
    23: 'dog_show',
    24: 'giving_directions_to_a_location',
    25: 'marriage_proposal',
    26: 'renovating_a_home',
    27: 'rock_climbing',
    28: 'town_hall_meeting',
    29: 'winning_a_race_without_a_vehicle',
    30: 'working_on_a_metal_crafts_project',
    31: 'beekeeping',
    32: 'wedding_shower',
    33: 'non-motorized_vehicle_repair',
    34: 'fixing_musical_instrument',
    35: 'horse_riding_competition',
    36: 'felling_a_tree',
    37: 'parking_a_vehicle',
    38: 'playing_fetch',
    39: 'tailgating',
    40: 'tuning_musical_instrument',
    -1: 'unknown',
}

rev_label_map = {name:no for no, name in label_map.items()}


matthijs_data = "/home/lear/douze/src/experiments/trecvid12/data/"
dan_data = "/home/lear/oneata/src/med12lear/data/"
matthijs_data_2014 = "/home/lear/douze/src/experiments/trecvid14/data/"



def parse_listfile(listfile):
    """ Parses a list file in Dan's format, returns a list of (vidname, classno) tuples """
    res = []
    if '/' not in listfile:
        for d in dan_data, matthijs_data_2014 + "listfiles/":
            l = d + listfile + ".list"
            if os.access(l, os.R_OK):
                listfile = l
                break
        else:
            assert False, "cannot find listfile " + listfile

    
    for l in open(listfile, "r"):
        l = l.strip()        
        vidname = l.split('-')[0]
        if ' ' in l: 
            classname = l.split(' ')[-1]
        else:
            classname = 'unknown'
        res.append((vidname, rev_label_map[classname]))
    return res
        



############################################################################################
# Normalization functions
#

def normalize_fisher(fv):
    # a short version of the functions below
    fv = power_normalize(fv, 0.5)
    fv = L2_normalize(fv)
    return fv


def standardize(xx, mu=None, sigma=None, compute_norm_subset = None):
    """ If the mu and sigma parameters are None, returns the standardized data,
    i.e., the zero mean and unit variance data, along with the corresponding
    mean and variance that were used for this standardization. Otherwise---if
    mu and sigma are given---, fron the data xx is substracted mu and then xx
    is multiplied by sigma on each dimension.

    Inputs
    ------
    xx: array [N, D]
        Data.

    mu: array [D], default None
        Mean of the data along the columns.

    sigma: array [D], default None
        Variance on each dimension.

    Outputs
    -------
    xx: array [N, D]
        Standardized data.

    mu: array [D]
        Computed or given mean. 

    sigma: array [D]
        Computed or given variance.

    """
    if xx.ndim != 2:
        raise ValueError, "Input array must be two dimensional."

    if mu is None or sigma is None:
        if compute_norm_subset != None:
            xx_subset = xx[compute_norm_subset]
        else:
            xx_subset = xx
        if mu is  None:
            mu = xx_subset.mean(axis=0)        
        if np.all(mu == 0):
            sigma = xx_subset.std(axis=0)
        else:
            sigma = (xx_subset - mu).std(axis =0)

    if np.min(sigma) == 0:
        warnings.warn("At least one dimension of the data has zero variance.")
        sigma[sigma == 0] = 1.
    if np.all(mu == 0):
        return xx/sigma, 0, sigma
    else:
        return (xx - mu) / sigma, mu, sigma


def L2_normalize(xx):
    """ L2-normalizes each row of the data xx.

    Input
    -----
    xx: array [N, D]
        Data.

    Output
    ------
    yy: array [N, D]
        L2-normlized data.

    """
    if type(xx)==np.ndarray:
        Zx = np.sum(xx * xx, 1)
        xx_norm = xx / np.sqrt(Zx[:, np.newaxis])
        xx_norm[np.isnan(xx_norm)] = 0
    else:
        row_norms = np.sqrt(np.array(xx.multiply(xx).sum(axis=1))[:,0])
        row_norms[row_norms==0] = 1
        row_indices, col_indices = xx.nonzero()
        xx.data /= row_norms[row_indices]
        xx_norm = xx
    return xx_norm


def power_normalize(xx, alpha):
    """ Computes a alpha-power normalization for the matrix xx. """
    if type(xx)==np.ndarray:
        return np.sign(xx) * np.abs(xx) ** alpha
    else:
        xx.data = np.sign(xx.data) * np.abs(xx.data) ** alpha
        return xx


def data_normalize(tr_data, te_data = None, power = 0.5, compute_norm_subset = None):    
    tr_data, mu, sigma = standardize(tr_data, compute_norm_subset = compute_norm_subset)
    tr_data = power_normalize(tr_data, power)
    tr_data = L2_normalize(tr_data)

    if te_data != None: 
        te_data, _, _ = standardize(te_data, mu, sigma)
        te_data = power_normalize(te_data, power)
        te_data = L2_normalize(te_data)

        return tr_data, te_data, mu, sigma

    return tr_data, mu, sigma


def data_normalize_inplace(x, power = 0.5, compute_norm_subset = None):

    if compute_norm_subset != None:
        x_subset = x[compute_norm_subset]
    else:
        x_subset = x
    
    mu = x_subset.mean(axis=0)    
    sigma = (x_subset - mu).std(axis=0)
    
    if np.min(sigma) == 0:
        warnings.warn("At least one dimension of the data has zero variance.")
        sigma[sigma == 0] = 1.

    del x_subset
    x -= mu
    x *= 1. / sigma
    
    if power == 1: 
        pass
    elif power == 0.5:
        yael.fvec_ssqrt(yael.numpy_to_fvec_ref(x), x.size)
    else:
        yael.fvec_spow(yael.numpy_to_fvec_ref(x), x.size, power)
    yael.fmat_normalize_columns_l2sqr_pow(yael.numpy_to_fvec_ref(x), x.shape[1], x.shape[0], -0.5)

    return mu, sigma
    
    


def average_inside_chunks(fv, nchunks):
    """ Average descriptors inside each of equal-length chunks.
    fv          - numpy array of descriptors
    nchunks     - number of chunks to use """

    ndesc = fv.shape[0]
    fv2 = np.empty((nchunks, fv.shape[1]), dtype=np.float32)
    ndesc = float(ndesc)
    for i in range(nchunks):
        begin_chunk = int(i*ndesc/nchunks)
        end_chunk = int(np.ceil((i+1)*ndesc/nchunks))
        fv2[i] = np.mean(fv[begin_chunk:end_chunk], axis=0)
    return fv2

    
############################################################################################
# Load descriptors.
#
# Each function loads a channel, given a 'split' -> refers to a list
# file. Functions have additional arguments that specify what subtype
# of channel to load.
#
# For some descriptors, the function accepts a 'ssl' argument. If not
# None, it is used like: slno, slb = ssl, where the list file is sliced
# in slb slices, and only slice slno is loaded.


def skip_npy(f):
  """ Step over numpy data in a file -- In some cache formats, the
  meta-information and numpy arrays are stored together."""
  assert f.read(6) == '\x93NUMPY'
  assert f.read(1) == '\x01'
  assert f.read(1) == '\x00'
  header_len, = struct.unpack('<h', f.read(2))
  array_format = eval(f.read(header_len))
  item_size = np.dtype(array_format['descr']).itemsize
  data_size = item_size * np.prod(array_format['shape'])
  f.seek(data_size, 1) # step over array data


############################################################################################
# SIFT (Matthijs)
#


def load_bigimbaz_blocks(input_pattern, d = -1):    
    block = 0
    fv = None
    while True:
        fname = input_pattern % block
        if os.access(fname, os.R_OK):
            fvi = ynumpy.fvecs_read(fname)
            if fvi.shape[0] == 0:
                pass
            elif fv == None:
                fv = fvi
            else:
                try:
                    fv = np.vstack((fv, fvi))
                except ValueError:
                    pdb.set_trace()
        else:
            break
        block += 1 

    if block == 0 or fv == None:
        print "!! warn empty video", input_pattern
        fv = np.zeros((1, d))
    else:
        assert d == -1 or fv.shape[1] == d
    return fv
    

def get_per_video_sift_data(split, desc_key = 'framestep60_dense_siftnonorm_lpca32_gmm256_w_mu_sigma',
                            dimensions = (255, 255 + 32 * 256),
                            ssl = None, nchunks = 1, v14 = False):
    """
    The descriptor key is a directory name. The dimensions can be used to select mu/w/sigma
    """

    # cache
    # filename = dan_data + "per_video_cache/SIFT_%s_%s.raw" % (desc_key, split)
    if nchunks > 1:
        suffix = "_nchunks%d" % nchunks
    else:
        suffix = ""


    if v14:
        if split in ('train_balanced', 'test_balanced', 'trecvid11_more_null'):
            outdir = "/home/clear/dataset/trecvid14/tv11/PS-Training/SIFT"
        elif split.startswith("trecvid11_test_"):
            outdir = "/home/clear/dataset/trecvid14/tv11/Test/SIFT"
        elif split == "tv14test_positives":
            outdir = "/home/clear/dataset/trecvid14/MED14-Test/PS-Training/SIFT"
        elif split == "tv14test_eventbg":
            outdir = "/home/clear/dataset/trecvid14/MED14-Test/Event-BG/SIFT"
        else:            
            assert False, "split " + split + " unknown"
        filename = "%s/%s%s_%s.raw" % (outdir, desc_key, suffix, split)
    else: 
        filename = "data/per_video_cache/SIFT_%s%s_%s.raw" % (desc_key, suffix, split)

    if os.path.exists(filename):
        # Load data from cache file.
        if ssl == None: 
          print "  load per video data", filename
          with open(filename, "r") as ff:
              video_data = np.load(ff)
              video_labels = np.load(ff)
              video_names = cPickle.load(ff)
        else:
          ff = open(filename, 'r')
          skip_npy(ff)
          labels = np.load(ff)
          names = cPickle.load(ff)
          data = np.load(filename, mmap_mode = 'r')

          assert data.shape[0] == labels.size == len(names)
          n = data.shape[0]
          slno, slb = ssl # read fraction i out of slb
          i0, i1 = slno * n / slb, (slno + 1) * n / slb
          # select the fraction
          video_data = data[i0:i1, :].copy()
          video_names = names[i0:i1]
          video_labels = labels[i0:i1]                  

        d0, d1 = dimensions
        return video_data[:, d0:d1], video_labels, video_names

    # to build the index
    # k=framestep60_dense_siftnonorm_lpca32_gmm256_w_mu_sigma;
    # k=framestep60_dense_siftnonorm_lpca32_gmm1024_w_mu_sigma;
    # (find /scratch2/clear/dataset/trecvid11/med//desc/frames/$k/* ; find /scratch/clear/dataset/trecvid12/desc/events/$k/E* /scratch/clear/dataset/trecvid12/desc/PROGTEST/$k/ ) | grep _0.fvecs  > $k.index

    assert ssl == None

    if split.startswith("tv14test"):
        single_location = outdir + "/" + desc_key
    elif "sfv" in desc_key: 
        # slightly different subsets used for storing SIFT        
        subsets = ['trecvid13_train', 'trecvid12_test', 'trecvid11_balanced', 'trecvid13_adhoc', 'trecvid11_more_null', 'trecvid11_test']
        if split == 'train_balanced' or split == 'test_balanced':
            subset_name = 'trecvid11_balanced'
        else:
            subset_name = None
            for s in subsets:
                if split.startswith(s):
                    subset_name = s
                    break

        basedir = "/home/lear/potapov/data/med11/static/desc/frames"
        final_dir= "framestep60_dense_siftnonorm_lpca64_gmm256_sfv2"
        if subset_name:
            single_location = "%s/%s/%s" % (basedir, subset_name, final_dir)
        else:
            single_location = None
    else:        
        single_location = None
        location = {}

        for l in open(matthijs_data + "per_video_cache/" + desc_key + ".index", "r"):
            i = l.rfind('/')
            d, f = l[:i], l[i+1:-9]
            location[f] = d        

    print "Caching video descriptors, will be put in", filename
        
    video_names = []
    video_labels = []
    video_data = []
    
    d = -1 # unknown... Let's hope the first video does not have 0 frames
    
    def load_sift_desc((vidname, classno)):
        # Fisher descriptors of frames in a video may be spread over several blocks
        dirname = single_location or location[vidname]
        fv = load_bigimbaz_blocks("%s/%s_%%d.fvecs" % (dirname, vidname), d)
    
        print vidname, "%d * %d" % fv.shape                 
        
        # normalize per frame
        fv = normalize_fisher(fv)            

        if nchunks > 1:
            # average descriptors inside chunks
            fv = average_inside_chunks(fv, nchunks)

            # normalize "per chunk"
            fv = power_normalize(fv, 0.5)
            fv = L2_normalize(fv)

        # average of frame (or chunk) descriptors
        desc = fv.sum(axis = 0) / fv.shape[0]
        return vidname, classno, desc
    
    # Identify the dimension
    parsed_listfile = parse_listfile(split)
    vidname, classno, desc = load_sift_desc(parsed_listfile[0])
    d = desc.shape[0]  # desc is a 1-dimensional vector

    nthread = yael.count_cpu()
    for vidname, classno, desc in threads.ParallelIter(nthread, parsed_listfile, load_sift_desc): 
        video_labels.append(classno)
        video_names.append(vidname)
        video_data.append(desc)
    
    video_data = np.vstack(video_data)
    video_labels = np.array(video_labels)
    
    with open(filename, "w") as ff:
        np.save(ff, video_data)
        np.save(ff, video_labels)
        cPickle.dump(video_names, ff)
    d0, d1 = dimensions
    return video_data[:, d0:d1], video_labels, video_names


def get_per_video_siftb_data(split, ssl = None):

    if split == "tv14test_positives":
        dataset = "tv14test"
        collection = "PS-Training"
    elif split == "tv14test_eventbg":
        dataset = "tv14test"
        collection = "Event-BG"
    elif split == "tv14test_test":
        dataset = "tv14test"
        collection = "MED14-Test"
    else: 
        assert False
    
    parsed_listfile = parse_listfile(split)
    
    if ssl: 
        i, n = ssl
        parsed_listfile = parsed_listfile[i * len(parsed_listfile) / n : 
                                          (i + 1) * len(parsed_listfile) / n]
    
    # remove HVC in front of clip ids
    clipids = [clipid[3:] for clipid, label in parsed_listfile]
    video_labels = [label for clipid, label in parsed_listfile]
    video_data = video_descriptors_tv14.load_descriptors_sift(dataset, collection, "sift", clipids)

    video_labels = np.array(video_labels)
    
    return video_data, video_labels, clipids



############################################################################################
# MBH
#

  
def get_per_video_mbh_data_given_list(list_name, K, **kwargs):
    """ Loads the Fisher vectors corresponding to the samples found in the
    list specified by `list_name`.

    """
    sstats_path = ('/home/lear/oneata/data/trecvid12/features'
                   '/dense5.track15mbh.small.skip_1/statistics_k_%d' % K)

    # Default base directories.
    list_base_path = kwargs.get(
        'list_base_path', '/home/lear/douze/src/experiments/trecvid12/data')
    cache_base_path = kwargs.get(
        'cache_base_path', sstats_path)
    spm = kwargs.get('spm', (1, 1, 1))  # Spatial pyramid grid.
    bin = kwargs.get('bin', 0)          # Bin number.

    # slice support for partial loading on machines with < 80 GB ram....
    ssl = kwargs.get('ssl', None)
    
    if spm == (1, 1, 1):
        suffix = ''
    else:
        suffix = '_spm%d%d%d' % spm + 'bin%d' % bin

    # If this file exists load them directly.
    cache_filename = os.path.join(cache_base_path, 'mbh_' + list_name + suffix + '.raw')

    if ssl == None: 

      with open(cache_filename, 'r') as ff:
          print "Loading Fisher vectors from MBH descriptors for list %s..." % (list_name)
          data = np.load(ff)
          data = data.astype(np.float32) # some are stored in float64...
          labels = np.load(ff)
          names = cPickle.load(ff)
    else:
      ff = open(cache_filename, 'r')
      skip_npy(ff)
      labels = np.load(ff)
      names = cPickle.load(ff)
      data = np.load(cache_filename, mmap_mode = 'r')

      assert data.shape[0] == labels.size == len(names)
      n = data.shape[0]
      slno, slb = ssl # read fraction i out of slb
      i0, i1 = slno * n / slb, (slno + 1) * n / slb
      # select the fraction
      data = data[i0:i1, :].copy().astype(np.float32)
      names = names[i0:i1]
      labels = labels[i0:i1]      
    return data, labels, names


############################################################################################
# Jochen's audio descriptors.
#
    

jochen_lock = thread.allocate_lock()
jochen_index = {}

def init_jochen_index(channel):
    with jochen_lock:
        if channel in jochen_index:
            return jochen_index[channel]

        root = "/scratch/clear/douze/data_tv12/jochen_audio_descs/" + channel + '/'   

        video_to_file = {}        

        for d in 'training', 'validation', '2011_test', 'PROGTEST':
            d1 = root + d + '/'
            if not os.access(d1, os.R_OK): continue

            for f in os.listdir(d1):
                if not f.endswith('.log'): continue
                fname_txt = d1 + f
                fname = d1 + f[:-4] + '.fvecs'
                for i, l in enumerate(open(fname_txt, "r")):
                    vidname = l[-15:-6]
                    video_to_file[vidname] = (fname, i)
            
        jochen_index[channel] = video_to_file

        print "  indexed %d videos for Jochen's audio channel %s" % (len(video_to_file), channel)

        return video_to_file

def get_jochen_vector(jochen_cache, (fname, i)):

    if fname not in jochen_cache:
        print "   loading", fname
        descs = ynumpy.fvecs_read(fname)
        if '1024_SNS_NULL' in fname and descs.shape[1] != 80894:
            print "   has weird size %s, transposing" % (descs.shape,)
            descs = descs.T.copy()
        jochen_cache[fname] = descs

    return jochen_cache[fname][i]
            

def get_audio_data_jochen(split, channel, ssl = None):
    " read Jochen's audio descriptors"

    video_to_file = init_jochen_index(channel)
    video_names = []
    labels = []
    data = []
    miss = []
    cache = {}

    n_wanted = 0
    d = 80894 if channel == "1024_SNS_NULL" else 40447

    toload = parse_listfile(split)

    if ssl != None:
        slno, slb = ssl # read fraction i out of slb
        n = len(toload)
        i0, i1 = n * slno / slb, (slno + 1) * n / slb
        toload = toload[i0:i1]

    for vidname, classno in toload:
        
        n_wanted += 1
        video_names.append(vidname)
        labels.append(classno)
        
        if vidname in video_to_file:
            desc = get_jochen_vector(cache, video_to_file[vidname])
            d = desc.size            
            data.append(desc)
        else:
            miss.append(vidname)
            data.append(np.zeros(d))
    
    print "  %s: missing %d/%d descriptors" % (split, len(miss), n_wanted)

    data = np.vstack(data)
    labels = np.array(labels)
    return data, labels, video_names

###############################################################################
# Dan's audio descriptors (computed with Jochen's AXES executable).
#

def get_audio_data_dan(split, K=512, ssl = None):
    feature_path = ('/home/lear/oneata/data/trecvid12/features/'
                    'mfcc/statistics_k_%d/' % K)
    gmm_path = '/home/lear/oneata/data/trecvid12/features/mfcc/gmm/gmm_%d' % K
    filelist = '/home/lear/oneata/src/med12lear/data/%s.list' % split

    filename = os.path.join(feature_path, split + '.raw')

    if os.path.exists(filename):
        video_data, video_labels, video_names = load_cache_file(filename, ssl)

        return video_data, video_labels.squeeze(), video_names

    # from fisher_vectors.model.fv_model import FVModel
    from fv_model import sstats_to_features

    video_names = []
    video_labels = []
    video_data = []

    sstats_path = os.path.join(feature_path, 'stats.tmp', '%s.dat')
    gmm = gmm_read(open(gmm_path, 'r'))
    dim = gmm.k + 2 * gmm.d * gmm.k

    empty = 0

    with open(os.path.join(filelist), 'r') as ff:
        for line in ff.readlines(): 

            try:
                sample_name, class_name = line.split()
            except ValueError:
                sample_name = line.strip()
                class_name = 'unknown'
            classno = rev_label_map[class_name]
            vidname = sample_name.split('-')[0]
            
            video_labels.append([classno])
            video_names.append(vidname)

            sstats_filename = sstats_path % sample_name
            if os.access(sstats_filename, os.R_OK): 
                sstats = np.fromfile(sstats_filename, dtype=np.float32)
            else:
                print "Missing sstats", sstats_filename
                sstats = None

            if (sstats == None or np.isnan(np.max(sstats))
                or len(sstats) != dim or np.sum(sstats ** 2) == 0):

                warnings.warn("Empty video %s." % vidname)
                fv = np.zeros((1, dim))
                empty += 1
            else: 
                sstats = np.atleast_2d(sstats)
                fv = sstats_to_features(sstats, gmm)

                print "%-30s" % vidname,
                print "%dx%d" % sstats.shape

            video_data.append(fv)

    print "Empty videos: %d (out of %d)." % (empty, len(video_names))
    
    video_data = np.array(np.vstack(video_data), dtype=np.float32)
    video_labels = np.array(video_labels)
    
    with open(filename, "w") as ff:
        np.save(ff, video_data)
        np.save(ff, video_labels)
        cPickle.dump(video_names, ff)

    return video_data, video_labels.squeeze(), video_names


def get_per_video_color_data(split, sfv=False, K=256, D=64):
    """
    The descriptor key is a directory name. The dimensions can be used to select mu/w/sigma
    """
    fisher_dim = K - 1 + 2 * D * K
    spatial_fisher_dim = 2 * 3 * K
    if sfv:
        dim = fisher_dim + spatial_fisher_dim
    else:
        dim = fisher_dim

    # Cache file.
    sfv_suffix = '' if sfv else '_nosfv'
    dim_suffix = ('_d%d' % D) if D != 64 else ''
    filename = os.path.join(
        "data", "per_video_cache/%s_color_k%d%s%s.raw" % (
            split, K, dim_suffix, sfv_suffix))

    if os.path.exists(filename):
        # Load data from cache file.
        print "  load per video data", filename
        with open(filename, "r") as ff:
            video_data = np.load(ff)
            video_labels = np.load(ff)
            video_names = cPickle.load(ff)
        return video_data[:, :dim], video_labels, video_names

    video_names = []
    video_labels = []
    video_data = []
    sstats_path = os.path.expanduser(
        "~oneata/data/trecvid13/features/dense.color/"
        "statistics_k_%d/stats.tmp%s_sfv" % (K, dim_suffix))

    def load_color_desc((vidname, classno)): 

        fv = load_bigimbaz_blocks("%s/%s_%%d.fvecs" % (sstats_path, vidname), fisher_dim + spatial_fisher_dim)

        print vidname, "%d * %d" % fv.shape                 
        fv = normalize_fisher(fv)            

        # Average of descriptors.
        desc = fv.sum(axis=0) / fv.shape[0]

        return vidname, classno, desc

    nthread = yael.count_cpu()
    for vidname, classno, desc in threads.ParallelIter(nthread, parse_listfile(split), load_color_desc): 
        video_labels.append(classno)
        video_names.append(vidname)
        video_data.append(desc)
       
    video_data = np.vstack(video_data)
    video_labels = np.array(video_labels)
    
    with open(filename, 'w') as ff:
        np.save(ff, video_data)
        np.save(ff, video_labels)
        cPickle.dump(video_names, ff)

    return video_data[:, :dim], video_labels, video_names


###############################################################################
# Albert + Dan's text descriptors.
#

def get_per_video_text_data(split, K=64, cca_type='linear'):
    cca_suffix = '' if cca_type == 'linear' else '_KCCA'
    my_split_names = {
        'train_balanced': 'jerome_train_balanced',
        'test_balanced': 'jerome_test_balanced'}
    fisher_vectors_path = (
        "/home/lear/oneata/data/trecvid13/features/"
        "ocr/embeddings/fisher_vectors_%s%s_k%d.raw" % (
            my_split_names[split], cca_suffix, K))
    filelist = '/home/lear/oneata/src/med12lear/data/%s.list' % split
    filename = os.path.join(
        dan_data, "per_video_cache",
        "text%s_%s_k%d.raw" % (cca_suffix, split, K))

    if os.path.exists(filename):
        # Load data from cache file.
        print "Load per video MBH data", filename
        with open(filename, "r") as ff:
            video_data = np.load(ff)
            video_labels = np.load(ff)
            video_names = cPickle.load(ff)
        return video_data, video_labels, video_names

    with open(fisher_vectors_path, 'r') as ff:
        data = np.load(ff)
        names = cPickle.load(ff)

    fisher_vectors = {}
    from itertools import izip
    for datum, name in izip(data, names):
        fisher_vectors[name] = datum

    video_names = []
    video_labels = []
    video_data = []

    D = 96
    FV_DIM = 2 * D * K
    empty = 0

    with open(os.path.join(filelist), 'r') as ff:
        for line in ff.readlines(): 

            sample_name, class_name = line.split()
            classno = rev_label_map[class_name]
            vidname = sample_name.split('-')[0]
            
            video_labels.append([classno])
            video_names.append(vidname)

            if vidname not in fisher_vectors:
                warnings.warn("Empty video %s." % vidname)
                fv = np.zeros((1, FV_DIM), dtype=np.float32)
                empty += 1
            else: 
                fv = fisher_vectors[vidname]
                print "%-30s" % vidname,
                print "%d" % fv.shape

            video_data.append(fv)

    print "Empty videos: %d (out of %d)." % (empty, len(video_names))
    
    video_data = np.array(np.vstack(video_data), dtype=np.float32)
    video_labels = np.array(video_labels)
    
    with open(filename, "w") as ff:
        np.save(ff, video_data)
        np.save(ff, video_labels)
        cPickle.dump(video_names, ff)

    return video_data, video_labels, video_names

###############################################################################
# Heng's MBH (2013)
#

def load_cache_file(filename, ssl):
    if ssl == None:
        try:
            with open(filename, 'r') as ff:
                video_data = np.load(ff)
                video_labels = np.load(ff)
                video_names = cPickle.load(ff)
        except:
            print >> sys.stderr, "cannot load", filename
            raise
    else: 
        ff = open(filename, 'r')
        skip_npy(ff)
        labels = np.load(ff)
        names = cPickle.load(ff)
        data = np.load(filename, mmap_mode = 'r')

        assert data.shape[0] == labels.size == len(names)
        n = data.shape[0]
        slno, slb = ssl # read fraction i out of slb
        i0, i1 = slno * n / slb, (slno + 1) * n / slb
        # select the fraction
        video_data = data[i0:i1, :].copy().astype(np.float32)
        video_names = names[i0:i1]
        video_labels = labels[i0:i1]      
    return video_data, video_labels, video_names
        

def get_per_video_densetracks(split, K=256, descriptor='hog', pyramid=True, ssl = None):
    """Loads Heng's features."""

    DESC_DIM = {'mbhx': 48, 'mbhy': 48, 'hog': 48, 'hof': 54}
    FV_DIM = 2 * DESC_DIM[descriptor] * K

    if pyramid == True:
        dims = slice(FV_DIM * 4)
    else:
        dims = slice(FV_DIM)

    outfile = os.path.join("data", "per_video_cache", "densetracks_%s_%s_k%d.raw" % (descriptor, split, K))
    if os.path.exists(outfile):
        
        video_data, video_labels, video_names = load_cache_file(outfile, ssl = ssl)

        return video_data[:, dims], video_labels, video_names

    import gzip
    from itertools import izip

    if "trecvid13" in split or split.startswith("trecvid12_test"):
        infile = "/scratch/clear/hewang/trecvid2013/trecvid13/result_DenseTrack_MED_stab_480/fisher/%s_%s_k%d.gz"
    else:
        infile = "/scratch/clear/hewang/trecvid2013/trecvid11_full/result_DenseTrack_MED_stab_480/fisher/%s_%s_k%d.gz"
        
    video_names, video_labels = zip(*parse_listfile(split))
    N = len(video_names)
    missing = [1] * N
    video_data = np.zeros((N, FV_DIM * 4), dtype=np.float32)

    nthread = yael.count_cpu()
    # nthread = 1

    def get_video((ii, (video_name, video_label))): 
        try:
            with gzip.open(infile % (video_name, descriptor, K) ,"r") as ff:
                video_name_, fisher_vector, fisher_vector_h3, _ = ff.read().split('\n')

            assert video_name_.split()[1] == video_name
            # fisher_vector_1 = map(np.float32, fisher_vector.split())
            fisher_vector = np.fromstring(fisher_vector, dtype = np.float32, sep = ' ')
            # assert np.all(fisher_vector_1 == fisher_vector_2)
            # fisher_vector_h3 = map(np.float32, fisher_vector_h3.split())
            fisher_vector_h3 = np.fromstring(fisher_vector_h3, dtype = np.float32, sep = ' ')
            result = "%d" % len(fisher_vector)
            missing[ii] = 0
        except IOError:            
            result = "missing"
        print "\t#%6d/%6d. %s: %s" % (ii, N, video_name, result)
        video_data[ii] = np.hstack((fisher_vector, fisher_vector_h3))

    if nthread == 1: 
        for ii, (video_name, video_label) in enumerate(izip(video_names, video_labels)):
            get_video((ii, (video_name, video_label)))
    else:
        threads.RunOnSet(nthread,
                 enumerate(izip(video_names, video_labels)),
                 get_video)
                 
    nr_miss_videos = sum(missing)
    print "Missing videos: %d (out of %d)." % (nr_miss_videos, N)

    video_labels = np.array(video_labels)
    video_names = list(video_names)

    with open(outfile, "w") as ff:
        np.save(ff, video_data)
        np.save(ff, video_labels)
        cPickle.dump(video_names, ff)

    return video_data[:, dims], video_labels, video_names


############################################################################################
# Select the appropriate features
#
   


combinations = {
        'sift_mu':       ('sift', {'dimensions' : (255, 255 + 32 * 256) }),
        'sift_sigma':    ('sift', {'dimensions' : (255 + 32 * 256, 255 + 32 * 256 * 2) }),
        'sift_mu_sigma': ('sift', {'dimensions' : (255, 255 + 32 * 256 * 2) }),
        'sift_1024':     ('sift', {'dimensions' : (1023, 1023 + 32 * 1024 * 2),
                                   'desc_key': 'framestep60_dense_siftnonorm_lpca32_gmm1024_w_mu_sigma' }),
        'mbh':              ('mbh', {'K': 256}),
        'mbh_spm131_bin0':  ('mbh', {'K': 256, 'spm': (1, 3, 1), 'bin': 0}),
        'mbh_spm131_bin1':  ('mbh', {'K': 256, 'spm': (1, 3, 1), 'bin': 1}),
        'mbh_spm131_bin2':  ('mbh', {'K': 256, 'spm': (1, 3, 1), 'bin': 2}),
        'mbh_1024':         ('mbh', {'K': 1024}),

        'mbh_1024_spm0':  ('mbh', {'K': 1024, 'spm': (1, 3, 1), 'bin': 0}),
        'mbh_1024_spm1':  ('mbh', {'K': 1024, 'spm': (1, 3, 1), 'bin': 1}),
        'mbh_1024_spm2':  ('mbh', {'K': 1024, 'spm': (1, 3, 1), 'bin': 2}),

        'mbh_1024_spm131_bin0':  ('mbh', {'K': 1024, 'spm': (1, 3, 1), 'bin': 0}),
        'mbh_1024_spm131_bin1':  ('mbh', {'K': 1024, 'spm': (1, 3, 1), 'bin': 1}),
        'mbh_1024_spm131_bin2':  ('mbh', {'K': 1024, 'spm': (1, 3, 1), 'bin': 2}),

        'hof_stab_h3':  ('densetracks', {'K': 256, 'descriptor': 'hof',  'pyramid': True}),
        'hog_stab_h3':  ('densetracks', {'K': 256, 'descriptor': 'hog',  'pyramid': True}),
        'mbhx_stab_h3': ('densetracks', {'K': 256, 'descriptor': 'mbhx', 'pyramid': True}),
        'mbhy_stab_h3': ('densetracks', {'K': 256, 'descriptor': 'mbhy', 'pyramid': True}),

        'hof_stab_1024':  ('densetracks', {'K': 1024, 'descriptor': 'hof',  'pyramid': True}),
        'hog_stab_1024':  ('densetracks', {'K': 1024, 'descriptor': 'hog',  'pyramid': True}),
        'mbhx_stab_1024': ('densetracks', {'K': 1024, 'descriptor': 'mbhx', 'pyramid': True}),
        'mbhy_stab_1024': ('densetracks', {'K': 1024, 'descriptor': 'mbhy', 'pyramid': True}),

        'color':              ('color', {'sfv': False, 'K': 256}),
        'color_sfv':          ('color', {'sfv': True,  'K': 256}),
        'color_1024':         ('color', {'sfv': False, 'K': 1024}),
        'color_1024_sfv':     ('color', {'sfv': True,  'K': 1024}),
        'color_1024_d32':     ('color', {'sfv': False, 'K': 1024, 'D': 32}),
        'color_1024_d32_sfv': ('color', {'sfv': True,  'K': 1024, 'D': 32}),
        'color_1024_d16':     ('color', {'sfv': False, 'K': 1024, 'D': 16}),
        'color_1024_d16_sfv': ('color', {'sfv': True,  'K': 1024, 'D': 16}),

        'jochen_audio':     ('jochen_audio', {'channel': '512_NULL'}),
        'jochen_audio_sns': ('jochen_audio', {'channel': '1024_SNS_NULL'}),

        'dan_audio':      ('dan_audio', {'K': 512}),
        'dan_audio_256':  ('dan_audio', {'K': 256}),
        'dan_audio_1024': ('dan_audio', {'K': 1024}),

        'text':          ('text', {'K':  64, 'cca_type': 'linear'}),
        'text_128':      ('text', {'K': 128, 'cca_type': 'linear'}),
        'text_kcca':     ('text', {'K':  64, 'cca_type': 'kernel'}),
        'text_kcca_128': ('text', {'K': 128, 'cca_type': 'kernel'}),
        'text_kcca_256': ('text', {'K': 256, 'cca_type': 'kernel'}),
    
        'sift_w_mu_sigma_sfv':   ('sift', {'dimensions' : (0, 255 + 64 * 256 * 2 + 2 * 3 * 256),
                                           'desc_key': 'k256_framestep60_perframe_nonorm_sfv' }),
        'sift_w_mu_sigma_sfv_chunks':   ('sift', {'dimensions' : (0, 255 + 64 * 256 * 2 + 2 * 3 * 256), 'desc_key': 'k256_framestep60_perframe_nonorm_sfv', 'nchunks': 6}),

        'sift_v14':              ('sift', {'dimensions' : (0, 255 + 64 * 256 * 2 + 2 * 3 * 256),
                                           'desc_key': 'k256_framestep60_perframe_nonorm_sfv',
                                           'v14':True}),
        'sift_v14b':             ('siftb', {})
                                  
    
        }       


def get_data(features, split, **kwargs):
    """ Loads data for the specified features. """
    if features == 'sift':
        data, labels, vidnames = get_per_video_sift_data(split, **kwargs)
    elif features == 'siftb':
        data, labels, vidnames = get_per_video_siftb_data(split, **kwargs)
    elif features == 'mbh':
        data, labels, vidnames = get_per_video_mbh_data_given_list(split, **kwargs)        
    elif features == 'dan_audio':
        data, labels, vidnames = get_audio_data_dan(split, **kwargs)
    elif features == 'color':
        data, labels, vidnames = get_per_video_color_data(split, **kwargs)
    elif features == 'text':
        data, labels, vidnames = get_per_video_text_data(split, **kwargs)
    elif features == 'densetracks':
        data, labels, vidnames = get_per_video_densetracks(split, **kwargs)
    elif features == 'ssim':
        data, labels, vidnames = get_per_video_ssim_data(split, **kwargs)
    else:
        assert False
    return data, labels, vidnames


#############################################
# scoring functions


def compute_dcr(gt, confvals):
  Wmiss = 1.0
  Wfa = 12.49999

  res = zip(confvals, -gt)
  res.sort()
  res.reverse()
  tp_ranks = [(rank, confval) for rank, (confval, istp) in enumerate(res) if istp <= 0]
  
  ntot = len(confvals)
  npos = len(tp_ranks)
  dcr_tab = [Wmiss]

  for i, (rank, confval) in enumerate(tp_ranks):
    # consider results >= confval
    nres = rank + 1      # nb of results 
    ntp = i + 1          # nb of tp 
    nfa = nres - ntp     # nb of false alarms
    nmiss = npos - ntp   # nb of missed 
    Pfa = nfa / float(ntot - npos)
    Pmiss = nmiss / float(npos)    
    dcr = Wmiss * Pmiss + Wfa * Pfa
    dcr_tab.append(dcr)

  return min(dcr_tab)

 
def average_precision_10(gt, confvals):
    """ Returns the 10-point interpolated average precision.

    Inputs
    ------
    gt: list
        Ground truth information, contains +/-1 values.

    confvals: list
        Confidence values.

    """
    rec, prec = _get_pr(confvals, gt)
    return _get_ap_from_pr(rec, prec)


# Functions used by average_precision.
def _get_pr(confvals_l, gt_l, tot_pos=-1):
    """ Return the precision and recall values from the confidence values
    and ground truth information

    Parameters
    ----------
    confvals_l: list of confidence values for the class of interest

    gt_l: list of ground truth info (+/- 1) for the class of interest

    tot_pos: total number of pos (if different from the ones in gt_l)

    Returns
    -------
    rec: recall values of the precision-recall curve

    prec: precision values of the precision-recall curve

    Notes
    -----
    gt_l and confvals_l must be in the same order (confvals_l[i] and gt_l[i]
    must correspond to the same example)
    """
    # check that there are no 'NaN' values in the confvals
    if tot_pos <= 0:
        tot_pos = float(sum(1 for samp_lab in gt_l if samp_lab == 1))  # = tp + fn
        if tot_pos == 0:
            # no positives in ground truth
            return [[0], [0]]
    tp = 0.0  # number of true positives at the current rank
    fp = 0.0  # number of false positives at the current rank
    prec = []  # tp / tp+fp
    rec = []  # tp / tp+fn
    # order the samples by decreasing conf vals
    sid_cv = [(idx, confv) for idx, confv in enumerate(confvals_l)]
    sid_cv.sort(key=lambda x: x[1], reverse=True)  # sorting wrt the conf vals
    # compute precision and recall at each rank
    for idx, _confv in sid_cv:
        # at rank r in the ordered list of samples:
        if gt_l[idx] == 1:
            # it's a tp
            tp = tp + 1.0
        elif gt_l[idx] == -1:
            # it's a fp
            fp = fp + 1.0
        else:
            # unknown value: leave it out of the evaluation
            continue
        # add precision at current rank
        prec.append(tp / (tp + fp))
        # add recall at current rank
        rec.append(tp / tot_pos)
        if tp == tot_pos:
            break
    return [rec, prec]


def _get_ap_from_pr(rec, prec, ss=0.10):
    """Computes Average Precision from the (Recall, Precision) curve

    Uses Pascal VOC's method (max over quantized, with step-size ss, recall ranges)
    """
    # convert the rec and prec to arrays
    rec = np.array(rec)
    prec = np.array(prec)
    ap = 0.0
    quant_t = np.arange(0, 1.01, ss)
    # warning: arange stops one step earlier than its matlab counterpart
    for t in quant_t:
        tmp = prec[rec >= t]
        if len(tmp):
            ap += np.max(tmp) / len(quant_t)
    return ap

def score_ap_from_ranks_1 (ranks, nres, rp_points=None ):
    """ Compute the average precision of one search.
    ranks = ordered list of ranks of true positives (best rank = 0)
    nres  = total number of positives in dataset  
    """
    if nres==0 or ranks==[]:
      if rp_points!=None:
        rp_points+=[(0,0),(1,0)]
      return 0.0
    
    ap=0.0
    
    # accumulate trapezoids in PR-plot. All have an x-size of:
    recall_step=1.0/nres
    
    for ntp,rank in enumerate(ranks):
        # ntp = nb of true positives so far
        # rank = nb of retrieved items so far

        # y-size on left side of trapezoid:
        if rank==0: precision_0=1.0
        else:       precision_0=ntp/float(rank)      
        if rp_points!=None: rp_points.append((ntp/float(nres),precision_0))
        # y-size on right side of trapezoid:
        precision_1=(ntp+1)/float(rank+1)
        if rp_points!=None: rp_points.append(((ntp+1)/float(nres)-1e-10,precision_1))
        ap+=(precision_1+precision_0)*recall_step/2.0
    
    if rp_points!=None:
      rp_points.append((rp_points[-1][0]+1e-8,0))
    
    return ap


def average_precision(gt, confvals):
    assert map(int, sorted(list(set(gt)))) == [-1, 1]
    ntp = (np.array(gt) == 1).sum()
    # setting FP to True so that FPs in ties go before TPs
    res = [(score, label == -1) for score, label in zip(confvals, gt)]
    res.sort()
    res.reverse()
    tp_ranks = [rank for rank, (score, is_fp) in enumerate(res) if not is_fp]

    return score_ap_from_ranks_1(tp_ranks, ntp)
