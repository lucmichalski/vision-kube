#!/usr/bin/env python
"""
tests a test set of data using a specified, pre0trained model and weights
"""
from __future__ import absolute_import, division, print_function
from ibeis.control.controller_inject import make_ibs_register_decorator
from ibeis.constants import Species, VIEWTEXT_TO_YAW_RADIANS
# from ibeis_cnn import utils
from ibeis_cnn import models
from ibeis_cnn import test
from ibeis_cnn import _plugin_grabmodels as grabmodels
import utool as ut
import cv2
import numpy as np
import random
print, print_, printDBG, rrr, profile = ut.inject(__name__, '[ibeis_cnn._plugin]')


CLASS_INJECT_KEY, register_ibs_method = make_ibs_register_decorator(__name__)


def convert_species_viewpoint(species, viewpoint):
    species_mapping = {
        'ZEBRA_PLAINS':        Species.ZEB_PLAIN,
        'ZEBRA_GREVYS':        Species.ZEB_GREVY,
        'ELEPHANT_SAVANNA':    Species.ELEPHANT_SAV,
        'GIRAFFE_RETICULATED': Species.GIRAFFE,
        'GIRAFFE_MASAI':       Species.GIRAFFE_MASAI,
    }
    viewpoint_list = VIEWTEXT_TO_YAW_RADIANS.keys()
    viewpoint_mapping = {
        'LEFT':        viewpoint_list[4],
        'FRONT_LEFT':  viewpoint_list[3],
        'FRONT':       viewpoint_list[2],
        'FRONT_RIGHT': viewpoint_list[1],
        'RIGHT':       viewpoint_list[0],
        'BACK_RIGHT':  viewpoint_list[7],
        'BACK':        viewpoint_list[6],
        'BACK_LEFT':   viewpoint_list[5],
    }
    species_ = species_mapping[species]
    viewpoint_ = viewpoint_mapping[viewpoint]
    return species_, viewpoint_


def convert_label(label):
    species, viewpoint = label.strip().split(':')
    species = species.strip()
    viewpoint = viewpoint.strip()
    species_, viewpoint_ = convert_species_viewpoint(species, viewpoint)
    return species_, viewpoint_


@register_ibs_method
def get_neuralnet_dir(ibs):
    nets_dir = ut.unixjoin(ibs.get_cachedir(), 'nets')
    return nets_dir


@register_ibs_method
def get_verified_aid_pairs(ibs):
    """
    Example:
        >>> # DISABLE_DOCTEST
        >>> from ibeis_cnn.train import *  # NOQA
        >>> import ibeis
        >>> ibs = ibeis.opendb('NNP_Master3')
        >>> verified_aid1_list, verified_aid2_list = get_verified_aid_pairs(ibs)
    """
    # Grab marked hard cases
    am_rowids = ibs._get_all_annotmatch_rowids()
    remove_photobombs = True
    if remove_photobombs:
        flags = ibs.get_annotmatch_is_photobomb(am_rowids)
        am_rowids = ut.filterfalse_items(am_rowids, flags)
    verified_aid1_list = ibs.get_annotmatch_aid1(am_rowids)
    verified_aid2_list = ibs.get_annotmatch_aid2(am_rowids)
    return verified_aid1_list, verified_aid2_list


@register_ibs_method
def detect_annot_species_viewpoint_cnn(ibs, aid_list):
    # Load chips and resize to the target
    target = (96, 96)
    print('Loading chips...')
    chip_list = ibs.get_annot_chips(aid_list, verbose=True)
    print('Resizing chips...')
    chip_list_resized = [ cv2.resize(chip, target, interpolation=cv2.INTER_LANCZOS4) for chip in ut.ProgressIter(chip_list, lbl='resizing chips') ]
    # Build data for network
    X_test = np.array(chip_list_resized, dtype=np.uint8)
    y_test = None
    # Define model and load weights
    print('Loading model...')
    model = models.ViewpointModel()
    weights_path = grabmodels.ensure_model('viewpoint', redownload=False)
    # Predict on the data and convert labels to IBEIS namespace
    pred_list, label_list, conf_list = test.test_data(X_test, y_test, model, weights_path)
    species_viewpoint_list = [ convert_label(label) for label in label_list ]
    return species_viewpoint_list


@register_ibs_method
def validate_annot_species_viewpoint_cnn(ibs, aid_list, verbose=False):
    # Load chips and metadata
    species_list = ibs.get_annot_species(aid_list)
    viewpoint_list = ibs.get_annot_yaw_texts(aid_list)
    species_viewpoint_list = ibs.detect_annot_species_viewpoint_cnn(aid_list)
    # Find all bad
    bad_species_list = []
    bad_viewpoint_list = []
    data = zip(aid_list, species_list, viewpoint_list, species_viewpoint_list)
    for aid, species, viewpoint, (species_, viewpoint_) in data:
        if species != species_:
            bad_species_list.append( (aid, species, species_) )
            continue
        if viewpoint != viewpoint_:
            bad_viewpoint_list.append( (aid, species, viewpoint, viewpoint_) )
            continue
    # Print bad if verbose
    if verbose:
        print('Found conflicting species:')
        for bad_species in bad_species_list:
            print('    AID %4d (%r) should be %r' % bad_species)
        print('Found conflicting viewpoints:')
        for bad_viewpoint in bad_viewpoint_list:
            print('    AID %4d (%r, %r) should be %r' % bad_viewpoint)
    # Return bad
    return bad_species_list, bad_viewpoint_list


def _suggest_random_candidate_regions(ibs, image, min_size, num_candidates=2000):
    h, w, c = image.shape
    h -= 1
    w -= 1
    min_x, min_y = min_size
    def _candidate():
        x0, y0, x1, y1 = 0, 0, 0, 0
        while x1 - x0 < min_x or y1 - y0 < min_y:
            x0 = int(random.uniform(0, w))
            y0 = int(random.uniform(0, h))
            x1 = int(random.uniform(0, w))
            y1 = int(random.uniform(0, h))
            if x0 > x1:
                x0, x1 = x1, x0
            if y0 > y1:
                y0, y1 = y1, y0
        return x0, y0, x1, y1
    candidate_list = [ _candidate() for _ in range(num_candidates) ]
    return candidate_list


def _suggest_bing_candidate_regions(ibs, image_path_list):
    def _dedictify(dict_list):
        return [ [d_['minx'], d_['miny'], d_['maxx'], d_['maxy']] for d_ in dict_list ]

    from pybing import BING_Detector
    detector = BING_Detector()
    results_list = detector.detect(image_path_list)
    result_list = [ _dedictify(results[1]) for results in results_list ]
    return result_list


def non_max_suppression_fast(box_list, conf_list, overlapThresh=0.5):
    """
        Python version of Malisiewicz's Matlab code:
        https://github.com/quantombone/exemplarsvm

        NOTE: This is adapted from Pedro Felzenszwalb's version (nms.m),
        but an inner loop has been eliminated to significantly speed it
        up in the case of a large number of boxes

        Reference: https://github.com/rbgirshick/rcnn/blob/master/nms/nms.m
        Reference: http://www.pyimagesearch.com/2015/02/16/faster-non-maximum-suppression-python/
    """
    # if there are no boxes, return an empty list
    if len(box_list) == 0:
        return []

    # Convert to Numpy
    box_list  = np.array(box_list)
    conf_list = np.array(conf_list)

    # if the bounding boxes integers, convert them to floats --
    # this is important since we'll be doing a bunch of divisions
    if box_list.dtype.kind == "i":
        box_list = box_list.astype("float")

    # initialize the list of picked indexes
    pick = []

    # grab the coordinates of the bounding boxes
    # Our boxes are stored as y1, y2, x1, x2 to be in-line with OpenCV indexing
    x1 = box_list[:, 0]
    y1 = box_list[:, 1]
    x2 = box_list[:, 2]
    y2 = box_list[:, 3]
    s  = conf_list

    # compute the area of the bounding boxes and sort the bounding
    # boxes by the bottom-right y-coordinate of the bounding box
    area = (x2 - x1 + 1) * (y2 - y1 + 1)
    idxs = np.argsort(s)

    # keep looping while some indexes still remain in the indexes
    # list
    while len(idxs) > 0:
        # grab the last index in the indexes list and add the
        # index value to the list of picked indexes
        last = len(idxs) - 1
        i = idxs[last]
        pick.append(i)

        # find the largest (x, y) coordinates for the start of
        # the bounding box and the smallest (x, y) coordinates
        # for the end of the bounding box
        xx1 = np.maximum(x1[i], x1[idxs[:last]])
        yy1 = np.maximum(y1[i], y1[idxs[:last]])
        xx2 = np.minimum(x2[i], x2[idxs[:last]])
        yy2 = np.minimum(y2[i], y2[idxs[:last]])

        # compute the width and height of the bounding box
        w = np.maximum(0, xx2 - xx1 + 1)
        h = np.maximum(0, yy2 - yy1 + 1)

        # compute the ratio of overlap
        overlap = (w * h) / area[idxs[:last]]

        # delete all indexes from the index list that have
        idxs = np.delete(idxs, np.concatenate(([last], np.where(overlap > overlapThresh)[0])))

    # return only the bounding boxes that were picked using the
    # integer data type
    return pick


@register_ibs_method
def detect_image_cnn(ibs, gid, confidence=0.90, extraction='bing'):
    # Load chips and resize to the target
    target = (96, 96)
    targetx, targety = target
    # gid = gid_list[random.randint(0, len(gid_list))]
    # gid = gid_list[0]
    print('Detecting with gid=%r...' % (gid, ))
    image = ibs.get_images(gid)
    rects = np.copy(image)
    h, w, c = image.shape

    print('Querrying for candidate regions...')
    image_path = ibs.get_image_paths(gid)
    if extraction == 'random':
        candidate_list = _suggest_random_candidate_regions(ibs, image, (32, 32))
    else:
        candidate_list = _suggest_bing_candidate_regions(ibs, [image_path])[0]

    print('Num candidates: %r' % (len(candidate_list), ))
    chip_list_resized = []
    print('Extracting candidate regions...')
    for candidate in candidate_list:
        x0, y0, x1, y1 = candidate
        chip = image[y0 : y1, x0 : x1]
        chip = cv2.resize(chip, target, interpolation=cv2.INTER_LANCZOS4)
        chip_list_resized.append(chip)
        color = (255, 0, 0)
        # cv2.rectangle(rects, (x0, y0), (x1, y1), color)
        mx = int((x1 - x0) * 0.5)
        my = int((y1 - y0) * 0.5)
        cv2.circle(rects, (x0 + mx, y0 + my), 5, color, -1)
    # cv2.imshow('', rects)
    # cv2.waitKey(0)
    # cv2.destroyAllWindows()

    # Build data for network
    X_test = np.array(chip_list_resized, dtype=np.uint8)
    y_test = None
    # Define model and load weights
    print('Loading model...')
    model = models.ViewpointModel()
    weights_path = grabmodels.ensure_model('viewpoint', redownload=False)
    # Predict on the data and convert labels to IBEIS namespace
    pred_list, label_list, conf_list = test.test_data(X_test, y_test, model, weights_path)
    species_viewpoint_list = [ convert_label(label) for label in label_list ]

    num_all_candidates = len(conf_list)
    index_list = non_max_suppression_fast(candidate_list, conf_list)
    print('Surviving candidates: %r' % (index_list, ))
    num_supressed_candidates = num_all_candidates - len(index_list)
    print('Supressed: %d candidates' % (num_supressed_candidates, ))

    candidate_list         = np.take(candidate_list, index_list, axis=0)
    pred_list              = np.take(pred_list, index_list, axis=0)
    species_viewpoint_list = np.take(species_viewpoint_list, index_list, axis=0)
    conf_list              = np.take(conf_list, index_list, axis=0)

    values = zip(candidate_list, pred_list, species_viewpoint_list, conf_list)
    rects = np.copy(image)
    color_dict = {
        'giraffe': (255, 0, 0),
        'giraffe_masai': (255, 255, 0),
        'zebra_plains': (0, 0, 255),
        'zebra_grevys': (0, 255, 0),
        'elephant_savanna': (0, 0, 0),
    }
    skipped = 0
    for candidate, pred, species_viewpoint, conf in values:
        x0, y0, x1, y1 = tuple(candidate)
        species, viewpoint = species_viewpoint
        if conf < confidence:
            skipped += 1
            continue
        print('%r Found %s (%s, %s) at %s' % (candidate, pred, species, viewpoint, conf, ))
        color = color_dict[species]
        cv2.rectangle(rects, (x0, y0), (x1, y1), color)
        # mx = int((x1 - x0) * 0.5)
        # my = int((y1 - y0) * 0.5)
        # cv2.circle(rects, (x0 + mx, y0 + my), 5, color, -1)
    print('Skipped [ %d / %d ]' % (skipped, len(values), ))

    cv2.imshow('', rects)
    cv2.waitKey(0)
    cv2.destroyAllWindows()
