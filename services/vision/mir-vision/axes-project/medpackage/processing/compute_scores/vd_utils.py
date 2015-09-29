



##############################################################################
# Modules importation #

import cPickle
import os
import thread
import pdb
import sys
import struct

from yael import ynumpy, yael, threads
from yael.yael import gmm_read

import numpy as np
import warnings


##############################################################################
# Global variables #


CLASSES = range(6, 16) + range(21, 31)

classifiers_dict = {
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

COMBINATIONS = {
       
        'hof_stab_h3':  ('densetracks', {'K': 256, 'descriptor': 'hof',  'pyramid': True}),
        'hog_stab_h3':  ('densetracks', {'K': 256, 'descriptor': 'hog',  'pyramid': True}),
        'mbhx_stab_h3': ('densetracks', {'K': 256, 'descriptor': 'mbhx', 'pyramid': True}),
        'mbhy_stab_h3': ('densetracks', {'K': 256, 'descriptor': 'mbhy', 'pyramid': True}),

        'hof_stab_1024':  ('densetracks', {'K': 1024, 'descriptor': 'hof',  'pyramid': True}),
        'hog_stab_1024':  ('densetracks', {'K': 1024, 'descriptor': 'hog',  'pyramid': True}),
        'mbhx_stab_1024': ('densetracks', {'K': 1024, 'descriptor': 'mbhx', 'pyramid': True}),
        'mbhy_stab_1024': ('densetracks', {'K': 1024, 'descriptor': 'mbhy', 'pyramid': True}),

        }       

def get_classifier_name_from_index(index):
    return classifiers_dict[index]

def get_classifier_index_from_name(name):
    for index,classifier_name in classifiers_dict.items():
        if name == classifier_name:
            return index
    return None


############################################################################################
# Normalization functions
#

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

    return (xx - mu) / sigma


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
    Zx = np.sum(xx * xx, 1)
    xx_norm = xx / np.sqrt(Zx[:, np.newaxis])
    xx_norm[np.isnan(xx_norm)] = 0
    return xx_norm


def power_normalize(xx, alpha):
    """ Computes a alpha-power normalization for the matrix xx. """
    return np.sign(xx) * np.abs(xx) ** alpha


def data_normalize(tr_data, te_data = None, power = 0.5):    
    tr_data, mu, sigma = standardize(tr_data)
    tr_data = power_normalize(tr_data, power)
    tr_data = L2_normalize(tr_data)

    if te_data != None: 
        te_data, _, _ = standardize(te_data, mu, sigma)
        te_data = power_normalize(te_data, power)
        te_data = L2_normalize(te_data)

        return tr_data, te_data, mu, sigma

    return tr_data, mu, sigma


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


###############################################################################
# Heng's MBH (2013)
#
       


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

 
def average_precision(gt, confvals):
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
