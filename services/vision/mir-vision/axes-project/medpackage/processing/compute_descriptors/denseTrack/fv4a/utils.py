
from itertools import izip_longest
import numpy as np
import os
import re


def grouper(iterable, n, fillvalue=None):
    """Collects data into fixed-length chunks or blocks.

    Example
    -------
        grouper('ABCDEFG', 3, 'x')  # --> ABC DEF Gxx

    """
    args = [iter(iterable)] * n
    return izip_longest(fillvalue=fillvalue, *args)


def regular_multidim_digitize(data, n_bins=3, lbes=None, rbes=None):
    """Build a regular grid and assigns each data point to a cell.

    Parameters
    ----------
    data: (n_points, n_dims) array,
          the data that we which to digitize

    n_bins: int or (n_dims, ) array-like, optional, default: 3,
            per-dimension number of bins

    lbes: float or (n_dims, ) array-like, optional, default: None,
          per-dimension left-most bin edges
          by default, use the min along each dimension

    rbes: float or (n_dims, ) array-like, optional, default: None,
          per-dimension right-most bin edges
          by default, use the max along each dimension

    Returns
    -------
    assignments: (n_points, ) array of integers,
                 the assignment index of each point

    Notes
    -----
    1. `Regular' here means evenly-spaced along each dimension.
    2. Each cell has its unique integer index which is the cell number in
    C-order (last dimension varies fastest).
    
    To obtain the cell coordinates from the cell numbers do:
        np.array(zip(*np.unravel_index(assignments, n_bins)))

    """
    n_points, n_dims = data.shape

    if isinstance(n_bins, int):
        n_bins = np.array([n_bins for i in range(n_dims)], dtype=np.int)
    else:
        n_bins = np.asarray(n_bins, dtype=np.int)
        assert n_bins.shape == (n_dims,), "invalid n_bins: {0}".format(n_bins)

    if lbes is None:
        lbes = np.min(data, axis=0).astype(np.float)
    else:
        if isinstance(lbes, float):
            lbes = np.array([lbes for i in range(n_dims)], dtype=np.float)
        else:
            lbes = np.asarray(lbes, dtype=np.float)
            assert len(lbes) == n_dims, "Invalid lbes: {0}".format(lbes)
        # check for overflow
        assert np.alltrue(lbes <= np.min(data, axis=0)), "lbes not low enough"

    if rbes is None:
        rbes = np.max(data, axis=0).astype(np.float)
    else:
        if isinstance(rbes, float):
            rbes = np.array([rbes for i in range(n_dims)], dtype=np.float)
        else:
            rbes = np.asarray(rbes, dtype=np.float)
            assert len(rbes) == n_dims, "Invalid rbes: {0}".format(rbes)
        # check for overflow
        assert np.alltrue(rbes >= np.max(data, axis=0)), "rbes not high enough"

    # Get the bin-widths per dimension
    bws = 1e-5 + (rbes - lbes) / n_bins  # Add small shift to have max in last bin
    # Get the per-dim bin multi-dim index of each point
    dis = ((data - lbes[np.newaxis, :]) / bws[np.newaxis, :]).astype(np.int)
    # Get index of the flattened grid
    assignments = np.ravel_multi_index(dis.T, n_bins)
    # DEBUG
    # assert np.alltrue(dis == np.array(zip(*np.unravel_index(assignments, n_bins))))
    return assignments


def hms_to_s(hms): 
    """Parse time in the hh:mm:ss.ms format to seconds
    accepted format: hh:mm:ss[.ms][frameno/framerate]"""
    if hms=='N/A': return float('nan')
    f=hms.split(':')
    fr=0
    if len(f)==4:
        f2 = f[3].split('/')
        fr = float(f2[0])/float(f2[1])
        f.pop(-1)
    s = 0
    for n in f:
        s *= 60
        s += float(n)
    return s+fr


def get_video_infos(vidpath, tool='mplayer'):
    """Gets video information using `ffmpeg` or `mplayer`.

    Returns
    -------
    res: dict
        Dictionary containing video information:
        {'duration', 'start_time', 'fps', 'img_size'}.

    """
    res = {}
    
    if tool == 'ffmpeg':
        # Use ffmpeg to check FPS.
        cmd = "ffmpeg -i \"%s\"" % vidpath
        res['start_time'] = 0.0
        for line in os.popen4(cmd,"r")[1]:
            p = re.search( " Duration: ([0-9:.N/A]+)", line )
            if p: res['duration'] = hms_to_s(p.group(1))
            
            p = re.search( " start: ([0-9.]+)", line)
            if p: res['start_time'] = float(p.group(1))
            
            p = re.search( "Stream .*: Video:.* (\d+)x(\d+)", line )
            if p: 
                assert 'img_size' not in res, "error: too many video streams for video %s" % vidpath
                res['img_size'] = (int(p.group(1)),int(p.group(2)))
                p = re.search( "([0-9.k]+) fps", line )
                if p: res['fps'] = float(p.group(1).replace('k','000'))
                else:
                    p = re.search( "([0-9.k]+) tbr", line )
                    if p: res['fps'] = float(p.group(1).replace('k','000'))
            if len(res)>=4: break
    
    elif tool in ('mplayer','mencoder'):
        cmd = "mplayer -identify -vo null -ao null -frames 0 \"%s\" 2> /dev/null" % vidpath
        
        w = h = 0
        for line in os.popen4(cmd,"r")[1]:
            fields = line.split("=")
            if fields[0] == "ID_VIDEO_WIDTH": w=int(fields[1])
            if fields[0] == "ID_VIDEO_HEIGHT": h=int(fields[1])
            if fields[0] == "ID_VIDEO_FPS": res['fps']=float(fields[1])
            if fields[0] == "ID_LENGTH": res['duration']=float(fields[1])
            if fields[0] == "ID_START_TIME": res['start_time']=float(fields[1])
        if w and h: res['img_size'] = (w, h)
        
    else: 
        assert False, 'Error: Unknown tool %s.' % tool
    
    assert len(res) == 4, "Error: no information for video '%s' (res=%s)" % (vidpath,str(res))

    return res


def import_dataset(module_name, variable_name):
    module = __import__('datasets.' + module_name, fromlist=[variable_name])
    return getattr(module, variable_name)


def average_precision(groundtruth, confidence_values):
    """Compute AP from confidence values and GT list."""
    def get_precision_recall(confvals_l, gt_l, tot_pos=-1):
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

    def get_average_precision_from_precision_recall(rec, prec, ss=0.10):
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

    rec, prec = get_precision_recall(confidence_values, groundtruth)
    return get_average_precision_from_precision_recall(rec, prec)

