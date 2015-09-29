
"""
In 2014, we will use the official CSV files as file lists. They are in
fixed locations in Clement's directory, and so are the descriptors.
 

"""
import sys, csv, os, pdb, cPickle, time, socket

import numpy as np

import scipy

from yael import ynumpy

from multiprocessing import Pool
from multiprocessing.dummy import Pool as ThreadPool
from multiprocessing import cpu_count

MED_DIR = os.getenv("MED_BASEDIR")
COLLECTION_DIR = os.getenv("COLLECTION_DIR")
if COLLECTION_DIR == None:
    VIDEOS_WORKDIR = MED_DIR + "videos_workdir/"
else:
    VIDEOS_WORKDIR = COLLECTION_DIR + "/processing/videos_workdir/"

############################################################################################
# Normalization functions
#

def L2_normalize(xx):
    """ L2-normalizes each row of the data xx"""
    Zx = np.sum(xx * xx, 1)
    xx_norm = xx / np.sqrt(Zx[:, np.newaxis])
    xx_norm[np.isnan(xx_norm)] = 0
    return xx_norm


def power_normalize(xx, alpha):
    """ Computes a alpha-power normalization for the matrix xx. """
    return np.sign(xx) * np.abs(xx) ** alpha


def normalize_fisher(fv):
    # a short version of the functions below
    fv = power_normalize(fv, 0.5)
    fv = L2_normalize(fv)
    return fv


def standardize(xx, mu=None, sigma=None):
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
        mu = np.mean(xx, 0)
        sigma = np.std(xx - mu, 0)

    if np.min(sigma) == 0:
        warnings.warn("At least one dimension of the data has zero variance.")
        sigma[sigma == 0] = 1.

    return (xx - mu) / sigma, mu, sigma


############################################################################################
# Loading SIFT descriptors
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
        fv = None
    return fv


def load_and_compute_viddesc(datadir, clipname):
    print "      Caching", clipname, "\r",
    sys.stdout.flush()
    fv = load_bigimbaz_blocks("%s/%s_%%d.fvecs" % (datadir, clipname))
    if fv != None:
      # normalize per frame
      fv = normalize_fisher(fv)

      # average of frame (or chunk) descriptors
      fv = fv.mean(axis = 0)

    return clipname, fv


def cache_descriptors_sift_1(datadir):

    cache_index = datadir + "/cache.index"
    cache_fname = datadir + "/cache.fvecs"

    if os.access(cache_index, os.R_OK):
        # print "  Loading cache index", cache_index
        index = [l.split() for l in open(cache_index)]
    else:
        print "  Caching descriptors to file", cache_index
        video_names = []
        video_data = []

        pool = ThreadPool(cpu_count())

        clipnames = [f[:9] for f in os.listdir(datadir) if f.endswith("_0.fvecs")]
        clipnames.sort()

        print "    caching descriptors for %d clips" % len(clipnames)

        cf = open(cache_fname, "w")

        d = -1

        for vidname, desc in pool.map(lambda clipname: load_and_compute_viddesc(datadir, clipname), clipnames):
            if d == -1: d = desc.size
            else:       assert desc == None or d == desc.size

            if desc == None: desc = np.zeros((1, d), dtype = 'float32')

            index.append((vidname, cf.tell()))
            ynumpy.fvecs_fwrite(cf, desc.reshape(1, -1))

        del cf

        print "  writing index", cache_index
        f = open(cache_index, "w")
        for vidname, offset in index:
            print >> f, vidname, offset
        del f

    index = [(vidname, cache_fname, offset) for vidname, offset in index]

    return index


def cache_descriptors_sift(datadir):

    has_subdirs = os.access(datadir + "/000", os.R_OK)

    if not has_subdirs:
        return cache_descriptors_sift_1(datadir)
    else:
        index = []
        subdirs = []
        for i in range(1000):
            subdir = datadir + "/%03d" % i
            if not os.access(subdir, os.R_OK): break
            index += cache_descriptors_sift_1(subdir)
        return index


def fvec_fread(f):
  size = np.fromfile(f, dtype = "int32", count = 1)
  return np.fromfile(f, dtype = "float32", count = size)

def load_descriptors_sift(dataset, collection, desctype, clipids = None):
  """
  dataset = tv14dryrun, tv14ps, tv14ah (option to early_fusion)
  collection = "Event-BG"
  desctype = siftXXXXX
  clipids = list of clip ids
  """
  print clipids
  # if desctype in ("sift_1024", "sift_2048") and dataset == "tv11":
  if (desctype, dataset) != ("sift", "tv11"):
      k = int(desctype.split("_")[1])
      desc_key = "framestep60_dense_siftnonorm_lpca64_gmm%d_sfv2" % k
      if dataset == "tv11": 
          return load_aggregated_descriptors("/home/clear/dataset/trecvid14/tv11/%s/SIFT/%s/block" % (collection, desc_key) , clipids)
      elif dataset == "tv14ps" or dataset == "tv14ah":  
          return load_aggregated_descriptors("/home/clear/dataset/trecvid14/eval/%s/SIFT/%s/block" % (collection, desc_key) , clipids)       
      else: 
          assert False

  
  desc_key = "framestep60_dense_siftnonorm_lpca64_gmm256_sfv2"

  if dataset == 'tv14dryrun': 
      datadir = "/home/clear/dataset/trecvid14/dryrun/%s/SIFT/%s" % (collection, desc_key)
  elif dataset == "tv14test": 
      datadir = "/home/clear/dataset/trecvid14/MED14-Test/%s/SIFT/%s" % (collection, desc_key)
  elif dataset == "tv11":
      datadir = "/home/clear/dataset/trecvid14/tv11/%s/SIFT/%s" % (collection, desc_key)
  else:
      assert False
  
  index = cache_descriptors_sift(datadir)


  if clipids == None:
      print "    reading %d descriptors" % len(index)
      cache_fname = index[0][1]
      
      return ynumpy.fvecs_read(cache_fname), [vidname for (vidname, fname, offset) in index]
  else:                                                                 
      cache_map = {vidname: (cache_fname, int(offset)) for vidname, cache_fname, offset in index}
      print "    picking %d / %d  descriptors from cache" % (len(clipids), len(cache_map))
      tocat = []
      cur_cache_fname = None  
      for clipid in clipids:
        print "      Loading", clipid, "\r",
        sys.stdout.flush()    
        (cache_fname, offset) = cache_map["HVC" + clipid]
        if cache_fname != cur_cache_fname:
          cache_file = open(cache_fname, "r")
          cur_cache_fname = cache_fname
        cache_file.seek(offset)
        desc = fvec_fread(cache_file)
        tocat.append(desc)
      return np.vstack(tocat), clipids

############################################################################################
# Loading CNN descriptors
#


def load_descriptors_cnn(dataset, collection, desctype, clipids = None):

  assert desctype[:4] == "cnn_"

  do_sqr = False
  if desctype.endswith("_sq"): 
      do_sqr = True
      desctype = desctype[:-3]

  if desctype.startswith("cnn_6fps_"):
      desc_key = desctype[9:]
  else:
      desc_key = desctype[4:]

  
  
  if dataset == "tv11":
      if desctype.startswith("cnn_6fps_"):
          prefix = "/home/clear/dataset/trecvid14/tv11/%s/cnn_6fps/%s" % (collection, desc_key)
      else:
          prefix = "/home/clear/dataset/trecvid14/tv11/%s/cnn/%s" % (collection, desc_key)
  elif dataset == "tv14ps" or dataset == "tv14ah":
      if desctype.startswith("cnn_6fps_"):
          prefix = "/home/clear/dataset/trecvid14/eval/%s/cnn_6fps/%s" % (collection, desc_key)
      else:
          prefix = "/home/clear/dataset/trecvid14/eval/%s/cnn/%s" % (collection, desc_key)
  else:
      assert False

  desc, vidnames = load_aggregated_descriptors(prefix, clipids)

  if desc != None and do_sqr: 
      print "squaring", desctype
      desc = desc**2 * np.sign(desc)

  return desc, vidnames



############################################################################################
# Loading MFCC descriptors
#


def load_descriptors_mfcc(dataset, collection, desctype, clipids = None):

  assert desctype.startswith("mfcc")
  desc_key = desctype[5:]
  """
  if dataset == "tv11":
      prefix = "/home/clear/dataset/trecvid14/tv11/%s/mfcc/%s" % (collection, desc_key)
  elif dataset in ["tv14ps", "tv14ah"]:
      prefix = "/home/clear/dataset/trecvid14/eval/%s/mfcc/%s" % (collection, desc_key)
  """
  
  prefix = "/home/clear/xmartin/MED/dataset/" + dataset + "/eval/%s/mfcc/mfcc_fisher_vectors" % (collection)

  return load_aggregated_descriptors(prefix, clipids)
      

############################################################################################
# Loading Scatter descriptors
#


def load_descriptors_scatter(dataset, collection, desctype, clipids = None):

  assert desctype.startswith("scatter_")
  desc_key = desctype[8:]
  """
  if dataset == "tv11":
      prefix = "/home/clear/dataset/trecvid14/tv11/%s/scatter/%s" % (collection, desc_key)
  elif dataset in ["tv14ps", "tv14ah"]:
      prefix = "/home/clear/dataset/trecvid14/eval/%s/scatter/%s" % (collection, desc_key)
  else:
      assert False
  """
  prefix = "/home/clear/xmartin/MED/dataset/" + dataset + "/eval/%s/scatter/mfcc_fisher_vectors" % (collection)

  return load_aggregated_descriptors(prefix, clipids)


############################################################################################
# Loading trajectory descriptors
#

def load_descs_fvecs(filename):
    content = ynumpy.fvecs_read(filename)
    return content.tolist()[0]

def load_descriptors_trajectory(event_class, video_list_filename, desctype, clipids =None):
  
    opts = desctype.split('_')
    assert opts[0] == 'denseTrack'
    desc_key = opts[1]
    #K = int(opts[2])
    #MODIFIED
    #K = 256
    
    # TODO: add padding when missing one descriptor
    
    video_list = []    
    with open(video_list_filename, 'r') as f:
        for l in f.readlines():
            video_list.append(l.rstrip('\n'))
    #print "    reading %d descriptors" % len(video_list)
    alldescs = []
    filenames = []
    if (desc_key == 'hog' or desc_key == 'hof'):
        for vid in video_list:
            DESC_FILE = VIDEOS_WORKDIR + vid + "/%s_fisher_vectors_k_256_descriptor.fvecs" % desc_key
            DESC_SFV_FILE = VIDEOS_WORKDIR + vid + "/%s_spatial_fisher_vectors_k_256_descriptor.fvecs" % desc_key
            filenames.append(DESC_FILE)
            filenames.append(DESC_SFV_FILE)
            
        p = Pool(10)
        descs = p.map(load_descs_fvecs, filenames)
        i = 0
        while i < len(descs):
            alldescs.append(descs[i] + descs[i+1])
            i += 2
        p.close()
        
        
    elif (desc_key == 'mbh'):
        for vid in video_list:
            DESC_MBHX_FILE = VIDEOS_WORKDIR + vid + "/%sx_fisher_vectors_k_256_descriptor.fvecs" % desc_key
            DESC_MBHX_SFV_FILE = VIDEOS_WORKDIR + vid + "/%sx_spatial_fisher_vectors_k_256_descriptor.fvecs" % desc_key
            DESC_MBHY_FILE = VIDEOS_WORKDIR + vid + "/%sy_fisher_vectors_k_256_descriptor.fvecs" % desc_key
            DESC_MBHY_SFV_FILE = VIDEOS_WORKDIR + vid + "/%sy_spatial_fisher_vectors_k_256_descriptor.fvecs" % desc_key
            filenames.append(DESC_MBHX_FILE)
            filenames.append(DESC_MBHX_SFV_FILE)
            filenames.append(DESC_MBHY_FILE)
            filenames.append(DESC_MBHY_SFV_FILE)
            
        p = Pool(10)
        descs = p.map(load_descs_fvecs, filenames)
        i = 0
        while i < len(descs):
            alldescs.append(descs[i] + descs[i+1] + descs[i+2] + descs[i+3])
            i += 4
        p.close()
            
    else:
        assert False
        
    #alldescs = np.reshape(alldescs, newshape=(len(alldescs), len(alldescs[0])) )
    alldescs = np.array(alldescs, dtype="float32")
    
    return alldescs, video_list
            
    """    
        prefix1 = MED_DIR + "../events/%s/eval/%s/denseTrack/%s_fisher_vectors" % (event_class, collection, desc_key)
        prefix2 = "/home/clear/xmartin/MED/dataset/%s/eval/%s/denseTrack/%s_spatial_fisher_vectors" % (dataset, collection, desc_key)

      desc1,clips = load_aggregated_descriptors(prefix1, clipids)
      desc2,clips = load_aggregated_descriptors(prefix2, clipids)

      if clipids == 'nodata':
          assert desc1 is None and desc2 is None
          descriptors = None
      else:
          SS_FV = slice(desc1.shape[1] / N_BINS)
          SS_SFV = slice(desc2.shape[1] / N_BINS)
          descriptors = np.hstack((desc1[:, SS_FV],desc2[:, SS_SFV]))

      return descriptors, clips
  elif (desc_key == 'mbh'):
      prefix1 = "/home/clear/xmartin/MED/dataset/%s/eval/%s/denseTrack/%sx_fisher_vectors" % (dataset, collection, desc_key)
      prefix2 = "/home/clear/xmartin/MED/dataset/%s/eval/%s/denseTrack/%sx_spatial_fisher_vectors" % (dataset, collection, desc_key)
      prefix3 = "/home/clear/xmartin/MED/dataset/%s/eval/%s/denseTrack/%sy_fisher_vectors" % (dataset, collection, desc_key)
      prefix4 = "/home/clear/xmartin/MED/dataset/%s/eval/%s/denseTrack/%sy_spatial_fisher_vectors" % (dataset, collection, desc_key)

      desc1,clips = load_aggregated_descriptors(prefix1, clipids)
      desc2,clips = load_aggregated_descriptors(prefix2, clipids)
      desc3,clips = load_aggregated_descriptors(prefix3, clipids)
      desc4,clips = load_aggregated_descriptors(prefix4, clipids)

      if clipids == 'nodata':
          assert desc1 is None and desc2 is None and desc3 is None and desc4 is None
          descriptors = None
      else:
          SS_FV = slice(desc1.shape[1] / N_BINS)
          SS_SFV = slice(desc2.shape[1] / N_BINS)
          descriptors = np.hstack((desc1[:, SS_FV],desc2[:, SS_SFV],desc3[:, SS_FV],desc4[:, SS_SFV]))

      return descriptors, clips
  else:
      assert False
      """
      
      
def load_descriptors_trajectory_aggregated(dataset, collection, desctype, clipids =None):
  
  opts = desctype.split('_')
  assert opts[0] == 'denseTrack'
  desc_key = opts[1]
  #K = int(opts[2])
  #MODIFIED
  #K = 256
  N_BINS = 4 if 'noSPM' in opts else 1

  if (desc_key == 'hog' or desc_key == 'hof'):
      prefix1 = "/home/clear/xmartin/MED/dataset/%s/eval/%s/denseTrack/%s_fisher_vectors" % (dataset, collection, desc_key)
      prefix2 = "/home/clear/xmartin/MED/dataset/%s/eval/%s/denseTrack/%s_spatial_fisher_vectors" % (dataset, collection, desc_key)

      desc1,clips = load_aggregated_descriptors(prefix1, clipids)
      desc2,clips = load_aggregated_descriptors(prefix2, clipids)

      if clipids == 'nodata':
          assert desc1 is None and desc2 is None
          descriptors = None
      else:
          SS_FV = slice(desc1.shape[1] / N_BINS)
          SS_SFV = slice(desc2.shape[1] / N_BINS)
          descriptors = np.hstack((desc1[:, SS_FV],desc2[:, SS_SFV]))

      return descriptors, clips
  elif (desc_key == 'mbh'):
      prefix1 = "/home/clear/xmartin/MED/dataset/%s/eval/%s/denseTrack/%sx_fisher_vectors" % (dataset, collection, desc_key)
      prefix2 = "/home/clear/xmartin/MED/dataset/%s/eval/%s/denseTrack/%sx_spatial_fisher_vectors" % (dataset, collection, desc_key)
      prefix3 = "/home/clear/xmartin/MED/dataset/%s/eval/%s/denseTrack/%sy_fisher_vectors" % (dataset, collection, desc_key)
      prefix4 = "/home/clear/xmartin/MED/dataset/%s/eval/%s/denseTrack/%sy_spatial_fisher_vectors" % (dataset, collection, desc_key)

      desc1,clips = load_aggregated_descriptors(prefix1, clipids)
      desc2,clips = load_aggregated_descriptors(prefix2, clipids)
      desc3,clips = load_aggregated_descriptors(prefix3, clipids)
      desc4,clips = load_aggregated_descriptors(prefix4, clipids)

      if clipids == 'nodata':
          assert desc1 is None and desc2 is None and desc3 is None and desc4 is None
          descriptors = None
      else:
          SS_FV = slice(desc1.shape[1] / N_BINS)
          SS_SFV = slice(desc2.shape[1] / N_BINS)
          descriptors = np.hstack((desc1[:, SS_FV],desc2[:, SS_SFV],desc3[:, SS_FV],desc4[:, SS_SFV]))

      return descriptors, clips
  else:
      assert False
  ############################################################################################
# Loading attributes descriptors
#
def load_descriptors_attributes(dataset, collection, desctype, clipids =None):
     assert desctype[:3] == 'att'
     voc_size = desctype.split('_')[1]
     desc_key = desctype.split('_')[2]
     if (dataset == "tv11")&(not desctype.endswith('test')):
        prefix = "/home/clear/dataset/trecvid14/tv11/%s/attribute_%s/%s/attributes" % (collection, desc_key,voc_size) 
     elif (dataset == "tv11")&( desctype.endswith('test')): 
        prefix = "/home/clear/nchesnea/data/trecvid14/features/tv11/%s/attribute_%s/%s/attributes" % (collection, desc_key,voc_size)
     return load_aggregated_descriptors(prefix, clipids)
  
def load_descriptors_cnn_attributes(dataset, collection, desctype, clipids =None):
     assert desctype[:8] == 'acnn_att'
     desc_key = desctype[1:4]
     if (dataset == "tv11"):
        prefix = "/home/clear/dataset/trecvid14/tv11/%s/attribute_%s_6fps/attributes" % (collection, desc_key)   
        #prefix = "/home/clear/nchesnea/data/trecvid14/features/tv11/%s/attribute_%s/%s/attributes" % (collection, desc_key,voc_size)
     return load_aggregated_descriptors(prefix, clipids)

def load_fused_attributes(dataset, collection, desctype, clipids =None):
     assert desctype[:4] == 'fatt'
     voc_size = desctype.split('_')[1]
     if (dataset == "tv11"):
        #prefix = "/home/clear/dataset/trecvid14/tv11/%s/fused_attributes/%s/attributes" % (collection, voc_size)   
        prefix = "/home/clear/nchesnea/data/trecvid14/features/tv11/%s/fused_attributes/%s/attributes" % (collection, voc_size) 
     if (dataset.startswith("tv14")):  
         prefix ="/home/clear/dataset/trecvid14/eval/%s/fused_attributes/%s/attributes"% (collection, voc_size) 
     return load_aggregated_descriptors(prefix, clipids)

def add_aug(pat, classes, csr, clipids_f): 
    print "    augmenting training data matrix %d*%d with 10 static positives" % csr.shape
    csr = [csr]
    for cl in classes:
        c = cPickle.load(open(pat % cl, 'r'))
        csr.append(c)
        clipids_f.append('static_positive_E%03d' % cl)
    csr = scipy.sparse.vstack(csr, format = 'csr')
    return csr

def load_sparse_blocks(prefix, clipids):
    # make index
    index = {}
    clipids_f = []
    for i in range(1000):
        indexname = "%s_%03d.index" % (prefix, i)
        if not os.access(indexname, os.R_OK): continue
        j = 0
        for hvc in open(indexname, "r"):
            hvc = hvc.strip()
            index[hvc] = (i, j)
            clipids_f.append(hvc)
            j += 1
            
    if clipids == "nodata":
        return None, clipids_f
    
    if clipids == None:
        clipids = clipids_f

    cur_blockno = -1
    csr = []
    hvcids = []
    for cid in clipids:
        hvc = cid
        hvcids.append(hvc)
        blockno, i = index[hvc]
        if blockno != cur_blockno:
            cur_blockno = blockno            
            blockname = "%s_%03d.sparse" % (prefix, blockno)
            block = cPickle.load(open(blockname, 'r'))
        csr.append(block[i, :])
    mat = scipy.sparse.vstack(csr, format = 'csr').astype(np.float32).toarray()

    return mat, hvcids
        

csr_cache = dict()

def load_descriptors_ocr(dataset, collection, desctype, clipids=None):
    t = time.time()
    assert desctype[:3]== "ocr" or desctype[:3] == "asr"
    aug = desctype.endswith('_aug')
    if aug: 
        desctype = desctype.split('_')[0]

    sparse_name = "aggregated_bow_vect.sparse"

    if (dataset == "tv11"):
        if desctype == "asr2": 
            sparse_name = "aggregated_bow_vect_fraunhofer.sparse"
        if (dataset,collection,desctype) not in csr_cache:
            csr_cache[(dataset,collection,desctype)] = cPickle.load(open("/home/clear/dataset/trecvid14/tv11/%s/%s/%s"%(collection, desctype[:3], sparse_name),"rb"))
        csr = csr_cache[(dataset,collection,desctype)]

        # index same for limsi and fraunhofer versions
        clipids_f = [i[:-1] for i in 
                     open("/home/clear/dataset/trecvid14/tv11/%s/%s/index"%(collection, desctype[:3])).readlines()]
        
        if aug and collection == "PS-Training":
            csr = add_aug("/home/clear/dataset/trecvid14/tv11/PS-Training/evt_desc_vect/E%03d_desc.sparse", 
                          range(6, 16), csr, clipids_f)
    elif dataset == 'tv14ps' or dataset == 'tv14ah':

        if collection == "MED14-EvalFull" and (desctype == 'asr' or desctype == 'asr2'):
            # load blocks
            return load_sparse_blocks("/home/clear/dataset/trecvid14/eval/MED14-EvalFull/%s/block" % desctype,clipids)            
            
        else:
            if (dataset,collection,desctype) not in csr_cache:
                fname = "/home/clear/dataset/trecvid14/eval/%s/%s/%s"%(collection, desctype, sparse_name)
                print "loading", fname
                csr_cache[(dataset,collection,desctype)] = cPickle.load(open(fname,"rb"))
            csr = csr_cache[(dataset,collection,desctype)]
            clipids_f = [i.replace("\n","") for i in open("/home/clear/dataset/trecvid14/eval/%s/%s/index"%(collection, desctype)).readlines()]

            if aug and collection == "PS-Training":
                csr = add_aug("/home/clear/dataset/trecvid14/eval/PS-Training/evt_desc_vect/E%03d_desc.sparse", 
                              range(21, 41), csr, clipids_f)
            if aug and collection == "AH-Training":
                csr = add_aug("/home/clear/dataset/trecvid14/eval/AH-Training/evt_desc_vect/E%03d_desc.sparse", 
                              range(41, 51), csr, clipids_f)
    else: 
        assert False
        
    if clipids == "nodata":
        return None, clipids_f
    if clipids == None:
        if collection == "MED14-EvalFull":
            return scipy.sparse.vstack([csr_cache[(dataset,collection,desctype,b)] for b in range(nblocks)])
        else:
            return csr.astype(np.float32).toarray(), clipids_f
    else:
            s = set(clipids)
            subset = np.zeros(len(clipids_f),dtype=np.bool)
            all_clips = []
            for j,c in enumerate(clipids_f):
                # if "HVC"+c in s:
                if c[3:] in s:
                    subset[j] = True
                    all_clips.append(c)
            print "subset size %d/%d" % (np.sum(subset), len(clipids_f))

            mat = csr[np.nonzero(subset)[0],:].astype(np.float32).toarray()
            #print "Timing for ocr/asr: %g"%(time.time()-t), time.time(), clipids_f.index("HVC"+clipids[0])
            return mat, all_clips

        
    

############################################################################################
# Common loading function
#

known_local_copies = set()

def local_prefix(prefix):    
    slash = prefix.rfind('/')
    p1, p2 = prefix[:slash], prefix[slash:]
    hostname = socket.gethostname().split('.')[0]
    dirname = p1 + '/copy_' + hostname
    # print "look at", dirname
    if os.access(dirname, os.R_OK):
        # print "known_local_copies=", known_local_copies
        if dirname not in known_local_copies: 
            known_local_copies.add(dirname)
            print "    using local copy on", dirname
        return dirname + p2
    else:
        return prefix

    

def load_aggregated_descriptors(prefix, clipids = None):
    prefix = local_prefix(prefix)
    # print prefix
    indexname = prefix + "_000.index"
    
    if os.access(indexname, os.R_OK): # try multipart index first
        index = []
        for i in range(1000):
            indexname = prefix + "_%03d.index" % i 
            if not os.access(indexname, os.R_OK): break
            dataname = prefix + "_%03d.fvecs" % i
            idx = [l.split() for l in open(indexname, "r")]
            index += [(vidname, dataname, int(offset)) for vidname, offset in idx]
    else:
        indexname = prefix + ".index"
        dataname = prefix + ".fvecs"
        idx = [l.split() for l in open(indexname, "r")]
        index = [(vidname, dataname, int(offset)) for vidname, offset in idx]

    if clipids == "nodata":
        print "    just loading index of %d videos" % len(index)
        vidnames = []
        for vidname, fname, offset in index:
            vidnames.append(vidname)
        return None, vidnames
    elif clipids == None:
        print "    reading %d descriptors" % len(index)
        vidnames = []
        fname_prev = None
        tocat = []
        for vidname, fname, offset in index:
            vidnames.append(vidname)
            if fname != fname_prev:
                print "      loading", fname
                descs = ynumpy.fvecs_read(fname)
                tocat.append(descs)
                fname_prev = fname
        alldescs = np.vstack(tocat)
        assert alldescs.shape[0] == len(index)
        return alldescs, vidnames
    else:                                                                 
        cache_map = {vidname: (cache_fname, int(offset)) for vidname, cache_fname, offset in index}
        print "    picking %d / %d  descriptors from cache" % (len(clipids), len(cache_map))
        tocat = []
        cur_cache_fname = None  
        for clipid in clipids:
            # print "      Loading", clipid, "\r",
            sys.stdout.flush()
            (cache_fname, offset) = cache_map[clipid]
            if cache_fname != cur_cache_fname:
                cache_file = open(cache_fname, "r")
                cur_cache_fname = cache_fname
            cache_file.seek(offset)
            desc = fvec_fread(cache_file)
            tocat.append(desc)
            """if not all([d.shape[0] == tocat[0].shape[0] for d in tocat]):
                print [d.shape[0] == tocat[0].shape[0] for d in tocat].index(False)
                print [d.shape for d in tocat]
                print tocat[[d.shape[0] == tocat[0].shape[0] for d in tocat].index(False)].shape
                print cache_fname"""
        
        return np.vstack(tocat), clipids


def load_descriptors_color(dataset, collection, desctype, clipids = None):
  
  if dataset == "tv11":
      prefix = "/home/clear/dataset/trecvid14/tv11/%s/color" % collection      
      if desctype == "color": 
          return load_aggregated_descriptors(prefix + "/color", clipids)
      elif desctype == "color_256":
          return load_aggregated_descriptors(prefix + "/color_256/block", clipids)
  elif dataset == "tv14ps":
      prefix = "/home/clear/dataset/trecvid14/eval/%s/color" % collection
      return load_aggregated_descriptors(prefix + "/statistics_k_1024_stats.tmp_d32_sfv/block", clipids)      
  else:
      assert False


############################################################################################
# Load list files + switch between descriptor types
#

available_descriptors = [
    'sift', 'sift_1024', 'sift_2048',
    'cnn_fc8_avg', 'cnn_fc7_ReLU_avg', 'cnn_fc7_avg', 'cnn_fc6_avg', 'cnn_fc6_ReLU_avg','cnn_6fps_fc6_ReLU_avg',
    'cnn_6fps_fc6_ReLU_avg_sq',
    'color', 'color_256',
    'mfcc_lpca39_gmm256', 'mfcc_lpca39_gmm1024',
    'mfcc',
    'scatter_lpca256_gmm128', 'scatter_lpca256_gmm256',
    'scatter_lpca256_gmm128_whiten', 'scatter_lpca256_gmm256_whiten',
    'scatter_average',
    'denseTrack_hof',
    'denseTrack_hog',
    'denseTrack_mbh', 
    'att_64_hmdb',
    'att_256_hmdb',
    'att_512_hmdb',
    'att_1024_hmdb',
    'att_512_hmdb_test',
    'att_1024_hmdb_test',
    'acnn_att',
    'fatt_1024',
    'ocr', 'ocr_aug', 
    'asr', 'asr_aug', 
    'asr2', 'asr2_aug']

# denseTrack versions

available_descriptors += ["denseTrack_" + c for c in "hof", "hog", "mbh"]

# For different number of clusters.
dense_track_clusters = [
    '_'.join(("denseTrack", c, str(k)))
    for c in "hof", "hog", "mbh"
    for k in 64, 256, 512, 1024]

# No spatial pyramid.
dense_track_no_spm = ['_'.join((dtc, "noSPM")) for dtc in dense_track_clusters]

available_descriptors += dense_track_clusters
available_descriptors += dense_track_no_spm

# detailed ones
available_descriptors += ["denseTrack_" + c + suf
                          for c in "hof", "hog", "mbhx", "mbhy"
                          for suf in "_fisher_vectors", "_spatial_fisher_vectors"]


# cnn versions
available_descriptors += ['cnn_6fps_fc%d_vlad%d-%d-%d_ReLU'%(i,c,k,m) for i in [6,7,8] for c in [128,256,512,1024,2048,4096] for k in range(32) for m in range(32)] 

available_descriptors += ['cnn_6fps_fc%d_fisher%d-%d_ReLU'%(i,c,k) for i in [6,7,8] for c in [128,256,512,1024,2048,4096] for k in range(32)] 

#attributes
available_descriptors += ['att_64_hmdb']

#scatter versions
available_descriptors += ['scatter_lpca%d_gmm128_whiten'%lpca for lpca in [32,64,128,256]]
available_descriptors += ['scatter_Tsec%0.3f_lpca%d_gmm128_whiten'%(t,lpca) for t in [0.2,1.5] for lpca in [32,64,128,256]]


def load_descriptors(event_class, video_list_filename, desctype, clipids = None):

    desc_key = desctype
    
    video_list = []    
    with open(video_list_filename, 'r') as f:
        for l in f.readlines():
            video_list.append(l.rstrip('\n'))
    #print "    reading %d descriptors" % len(video_list)
    alldescs = []
    filenames = []
    
    for vid in video_list:
        DESC_FILE = VIDEOS_WORKDIR + vid + "/%s.fvecs" % desc_key
        filenames.append(DESC_FILE)
        
    p = Pool(10)
    descs = p.map_async(load_descs_fvecs, filenames).get(9999999)
    # map is not used because of a python bug
    # http://stackoverflow.com/questions/1408356/keyboard-interrupts-with-pythons-multiprocessing-pool
    
    i = 0
    while i < len(descs):
        alldescs.append(descs[i])
        i += 1
    p.close()
        
        
    #alldescs = np.reshape(alldescs, newshape=(len(alldescs), len(alldescs[0])) )
    alldescs = np.array(alldescs, dtype="float32")
    
    return alldescs, video_list

#def load_descriptors(event_class, video_list_filename, desctype, clipids = None):
#    """ collection is a metadata store """
#    if desctype.startswith("denseTrack"):
#        return load_descriptors_trajectory(event_class, video_list_filename, desctype, clipids)  
#    else:
#        print >>sys.stderr, "unknown file type"


def parse_csv(dataset, eqname):
  if dataset == "tv14dryrun":
      csvfile = "/home/lear/leray/MED_MER14_IO_Client/dr_resources/%s.csv" % eqname      
  elif dataset == "tv14test": 
      # generated by python generate_med14test_queries.py
      csvfile = "/home/lear/douze/experiments/trecvid14/med12lear/data/med14test_queries/%s.csv" % eqname
  elif dataset == "tv11":
      csvfile = "/home/clear/dataset/trecvid14/tv11/csv/%s.csv" % eqname
      # csvfile = "/home/lear/douze/experiments/trecvid14/med12lear/data/listfiles/%s.csv" % eqname
  elif dataset == "tv14ps" or dataset == "tv14ah":
      csvfile = "/home/clear/dataset/trecvid14/eval/csv/%s.csv" % eqname
  elif dataset == "trecvid_all":
      csvfile = "/home/clear/xmartin/MED/dataset/trecvid_all/eval/csv/%s.csv" % eqname
  elif dataset == "thumos14":
      csvfile = "/home/clear/xmartin/MED/dataset/thumos14/eval/csv/%s.csv" % eqname
  else: 
      # csvfile = "/home/lear/leray/MED_MER14_IO_Client/resources/%s.csv" % eqname
      assert False

  f = csv.reader(open(csvfile, "r"))

  keys = f.next()

  fieldno = (keys.index("ClipID") if "ClipID" in keys else
             keys.index("VideoID"))

  f = list(f)
    
  clipids = [l[fieldno] for l in f]
  if "Label" in keys: 
      labelfield = keys.index("Label")
      gt = [l[labelfield] for l in f]
      print "Train set %s: %d clips (%d background, %d positive, %d near-miss)" % (
          eqname, len(clipids),
          gt.count("background"), gt.count("positive"), gt.count("near-miss"))
  else:
      gt = None
      
  return clipids, gt

def get_test_video_ids(dataset):
    if dataset == "tv11":
        return [l[3:l.find('-')] for l in open("/home/clear/dataset/trecvid14/tv11/csv/trecvid11_Test.list", "r")]
    else:
        assert dataset == "tv14ps", dataset
        ids = [l[l.rfind('/')+1:][3:9] for l in open("/home/clear/dataset/trecvid14/eval/csv/MED14-EvalFull.csv", "r").readlines()[1:]]
        return ids
    
    

dense_type = type(np.zeros(1, dtype=np.float32))

def get_train_descriptors(event_class, desctype, no_train_matrix = False):

    clipids = "nodata" if no_train_matrix else None
    
    print "Loading", desctype, "descriptors"

    pos_descs, pos_clips = load_descriptors(event_class, 
                                            MED_DIR + "../events/%s/workdir/_positive.txt" % (event_class),
                                            desctype,
                                            clipids = clipids)
                                            
    neg_descs, neg_clips = load_descriptors(event_class, 
                                            MED_DIR + "../events/%s/workdir/_background.txt" % (event_class),
                                            desctype,
                                            clipids = clipids)

    all_clips = pos_clips + neg_clips
    
    if not no_train_matrix: 
      print "get_train_descriptors %s %s: %d train + %d bg descs in %d dim" % (
          event_class, desctype, pos_descs.shape[0], neg_descs.shape[0], 
          pos_descs.shape[1])
      if type(pos_descs) == dense_type:
          #print "Dense descs"
          descs = np.vstack((pos_descs, neg_descs))
      else:
          #print "sparse descs"
          descs = scipy.sparse.vstack((pos_descs, neg_descs), 'csr')
    else:
      descs = None

    # label = 100 means training data (labels not specified)
    labels = np.ones(len(all_clips), dtype = 'int32') * 1
    # label = 0: null
    labels[len(pos_clips):] = -1
    
    return descs, labels, all_clips


def get_train_descriptors_10Ex_trans(dataset, desctype, versions, transformations=[], nearmiss_status="neg"):
    assert all([1<= t <= 20 for t in transformations])
    csv_path = "/home/clear/dataset/trecvid14/tv11/csv/"
    
    classes = [("E%03d_010Ex_%s"%(i,v),i, (j,v)) for i in range(6,16) for j,v in enumerate(versions)]
    pos_clips = []
    trf_clips = []
    nm_clips = []
    neg_clips = set()
    pos_labels = []
    fold_labs = [[],[],[],[]]
    trf_labels = []
    for c,classn,(j,v) in classes:
        for l in  open(csv_path + c + ".csv").readlines()[1:]:
            cl, exset, clipid, label = l.replace("\n","").replace("\r","").split(',')
            if label == "positive":
                pos_clips.append(clipid)
                fold_labs[0].append(v)
                pos_labels.append(classn-5)
                for transformation in transformations:
                    trf_clips.append("%02d"%transformation+clipid)
                    fold_labs[1].append(v)
                    trf_labels.append(classn-5)
                
            elif label== "near-miss":
                fold_labs[3].append(v)
                nm_clips.append(clipid)
            else:
                assert label == "background"
                neg_clips.add(clipid)
    fold_labs[2] = ["abcdefghij"]*len(neg_clips)
    
    assert len(trf_clips) == len(pos_clips)*len(transformations)

    

    pos_descs, pos_clips = load_descriptors(dataset, "PS-Training", desctype, pos_clips)
    neg_descs, neg_clips = load_descriptors(dataset, "Event-BG", desctype, list(neg_clips))
    if len(transformations) != 0:
        trf_descs, trf_clips = load_descriptors(dataset,"PS-Training-trfs", desctype, trf_clips)
    if nearmiss_status != "remove":
        nm_descs, nm_clips = load_descriptors(dataset,"PS-Training", desctype, nm_clips)
    
    if len(transformations) !=0:
        if nearmiss_status == "neg":
            descs = np.vstack((pos_descs,trf_descs, neg_descs, nm_descs))
            labels = np.concatenate((np.array(pos_labels, dtype=np.int32), np.array(trf_labels, dtype=np.int32), np.zeros(neg_descs.shape[0]+nm_descs.shape[0], dtype=np.int32)))  
            fold_labs = fold_labs[0]+fold_labs[1]+fold_labs[2]+fold_labs[3]
            print "Loaded %d pos, %d trfs, %d negs, %d near_misses"%(len(pos_clips), len(trf_clips), len(neg_clips), len(nm_clips))
        else:
            assert nearmiss_status == "remove"
            descs = np.vstack((pos_descs,trf_descs, neg_descs))
            labels = np.concatenate((np.array(pos_labels, dtype=np.int32), np.array(trf_labels, dtype=np.int32), np.zeros(neg_descs.shape[0], dtype=np.int32))) 
            fold_labs = fold_labs[0]+fold_labs[1]+fold_labs[2]
            print "Loaded %d pos, %d trfs, %d negs, %d near_misses"%(len(pos_clips), len(trf_clips), len(neg_clips), 0)
    else:
        if nearmiss_status == "neg":
            descs = np.vstack((pos_descs, neg_descs, nm_descs))
            labels = np.concatenate((np.array(pos_labels, dtype=np.int32), np.zeros(neg_descs.shape[0]+nm_descs.shape[0], dtype=np.int32)))   
            fold_labs = fold_labs[0]+fold_labs[2]+fold_labs[3]
            print "Loaded %d pos, %d trfs, %d negs, %d near_misses"%(len(pos_clips),0, len(neg_clips), len(nm_clips))
        else:
            assert nearmiss_status == "remove"
            descs = np.vstack((pos_descs, neg_descs))
            labels = np.concatenate((np.array(pos_labels, dtype=np.int32), np.zeros(neg_descs.shape[0], dtype=np.int32))) 
            fold_labs = fold_labs[0]+fold_labs[2]
            print "Loaded %d pos, %d trfs, %d negs, %d near_misses"%(len(pos_clips), 0, len(neg_clips), 0)

        
    assert len(trf_clips) == len(pos_clips)*len(transformations)

    
    
    return descs,labels, pos_clips + trf_clips + neg_clips + nm_clips, fold_labs



    
def get_test_descriptors(dataset, desctype, ssl = None):
    if dataset == "tv14test":  
        trialindex = "/home/bigimbaz/dataset/video/TrecVid2014/LDC2014E27-V3/MEDDATA/databases/MED14-Test_20140513_TrialIndex.csv"
        f = os.popen('cat %s | tr -d \\" | cut -d . -f 1 | sort | uniq' % trialindex, "r")
        clipids= [l[:-1] for l in f if "TrialID" not in l]
        gtlabels = -np.ones(len(clipids), dtype = 'int32')
    elif dataset == "tv11":
        clipids = [fname[3:3+6] for fname in open("/scratch/clear/douze/data_tv12/trecvid11_test.list", "r")]
        gtlabels = -np.ones(len(clipids), dtype = 'int32')
    elif dataset == "tv14test":
        clipids, gtlabels = parse_csv(dataset, "MED14-Test")
        assert gtlabels == None
    elif dataset == "tv14ps":
        clipids, gtlabels = parse_csv(dataset, "MED14-EvalFull")
        clipids.sort()
        # clipids = clipids[:1500]        
        gtlabels = -np.ones(len(clipids), dtype = 'int32')
    else:
        assert False

    if ssl != None:
        (i, n) = ssl
        tot = len(clipids)
        # print "in %d clips..." % tot
        clipids = clipids[tot * i / n : tot * (i + 1) / n]
        if gtlabels != None:
            gtlabels = gtlabels[tot * i / n : tot * (i + 1) / n]
        # print "  ... out %d clips" % len(clipids)

    
    if dataset == "tv14ps":
        collection = "MED14-EvalFull"
    else:
        collection = "Test"
    descs, clipids_ = load_descriptors(dataset, collection, desctype, clipids)

    return descs, gtlabels, ["HVC" + id for id in clipids]
    
def get_test_groundtruth(dataset): 
    """ returns a dict, gt["000032"] = ["E10", "E45"]
    means that clip id 32 belongs to events 10 and 45
    """
    if dataset == "tv14test": 
        gtfile = "/home/bigimbaz/dataset/video/TrecVid2014/LDC2014E27-V3/MEDDATA/databases/MED14-Test_20140513_Ref.csv"
        print "opening ground-truth file", gtfile
        f = csv.reader(open(gtfile, "r"))
        keys = f.next()
        assert keys == ["TrialID","Targ"], pdb.set_trace()
        gt = {}
        for trialID, Targ in f: 
            videoid, eventid = trialID.split('.')
            if videoid not in gt: gt[videoid] = []
            if Targ == "y": gt[videoid].append(eventid)
        return gt
    else: 
        print "no groundtruth"
        return None

if __name__ == "__main__":


    traintest, dataset, desctype = sys.argv[1:]

    if traintest == "train": 
        descs, labels, clipnames = get_train_descriptors(dataset, desctype)
    elif traintest == "test":        
        descs, labels, clipnames = get_test_descriptors(dataset, desctype, ssl = (24, 100))

    
    print labels, descs, len(clipnames)
    pdb.set_trace()
