def get_identify_training_fpaths(ibs, **kwargs):
    """
    Notes:
        Blog post: http://benanne.github.io/2015/03/17/plankton.html
        Code: https://github.com/benanne/kaggle-ndsb
        You need to do something like this to pass two images through the network:
        https://github.com/benanne/kaggle-ndsb/blob/master/dihedral.py#L89
        And then something like this to combine them again:
        https://github.com/benanne/kaggle-ndsb/blob/master/dihedral.py#L224

    CommandLine:
        python -m ibeis_cnn.ibsplugin --test-get_identify_training_fpaths

    Example:
        >>> # DISABLE_DOCTEST
        >>> from ibeis_cnn.ibsplugin import *  # NOQA
        >>> import ibeis
        >>> ibs = ibeis.opendb('testdb1')
        >>> (data_fpath, labels_fpath) = get_identify_training_fpaths(ibs)
    """
    print('get_identify_training_fpaths')
    aid1_list, aid2_list = get_identify_training_aid_pairs(ibs)
    max_examples = kwargs.pop('max_examples', None)
    if max_examples is not None:
        print('max_examples = %r' % (max_examples,))
        aid1_list = aid1_list[0:min(max_examples, len(aid1_list))]
        aid2_list = aid2_list[0:min(max_examples, len(aid2_list))]
    data_fpath, labels_fpath, training_dpath = cached_identify_training_data_fpaths(
        ibs, aid1_list, aid2_list, **kwargs)
    return data_fpath, labels_fpath, training_dpath


def get_identify_training_aid_pairs(ibs):
    r"""
    Returns:
        tuple: (aid1_list, aid2_list)

    CommandLine:
        python -m ibeis_cnn.ibsplugin --test-get_identify_training_aid_pairs

    Example:
        >>> # DISABLE_DOCTEST
        >>> from ibeis_cnn.ibsplugin import *  # NOQA
        >>> import ibeis
        >>> ibs = ibeis.opendb('NNP_Master3')
        >>> (aid1_list, aid2_list) = get_identify_training_aid_pairs(ibs)
        >>> result = str((len(aid1_list), len(aid2_list)))
        >>> assert aid1_list != aid2_list
        >>> print(result)
    """
    verified_aid1_list, verified_aid2_list = ibs.get_verified_aid_pairs()
    if len(verified_aid1_list) > 100:
        aid1_list = verified_aid1_list
        aid2_list = verified_aid2_list
    else:
        aid1_list, aid2_list = get_hotspotter_aid_pairs(ibs)
    aid1_list, aid2_list = filter_aid_pairs(aid1_list, aid2_list)
    return aid1_list, aid2_list


def cached_identify_training_data_fpaths(ibs, aid1_list, aid2_list, base_size=64):
    """
    todo use size in cfgstrings
    """
    print('[cached_identify_training_data_fpaths] begin')
    training_dpath = get_semantic_trainingpair_dir(ibs, aid1_list, aid2_list, 'train_identity')

    idcfg = IdentifyDataConfig()
    idcfg.base_size = base_size

    data_fpath = ut.unixjoin(training_dpath, 'data_' + idcfg.get_cfgstr())
    labels_fpath = ut.unixjoin(training_dpath, 'labels.pkl')
    NOCACHE_TRAIN = ut.get_argflag('--nocache-train')

    if NOCACHE_TRAIN or not (
            ut.checkpath(data_fpath, verbose=True)
            and ut.checkpath(labels_fpath, verbose=True)
    ):
        data, labels = get_aidpair_training_data_and_labels(ibs, aid1_list, aid2_list, **idcfg.kw())
        utils.write_data_and_labels(data, labels, data_fpath, labels_fpath)
    else:
        print('data and labels cache hit')
    return data_fpath, labels_fpath, training_dpath


def get_aidpair_identify_images(ibs, aid1_list, aid2_list, base_size=64, stacked=False):
    r"""
    Args:
        ibs (IBEISController):  ibeis controller object
        aid1_list (list):
        aid2_list (list):

    Returns:
        tuple: (data, labels)

    CommandLine:
        python -m ibeis_cnn.ibsplugin --test-get_aidpair_identify_images --show
        python -m ibeis_cnn.ibsplugin --test-get_aidpair_identify_images --show --db NNP_Master3
        python -m ibeis_cnn.ibsplugin --test-get_aidpair_identify_images

    Example:
        >>> # DISABLE_DOCTEST
        >>> from ibeis_cnn.ibsplugin import *  # NOQA
        >>> import ibeis
        >>> # build test data
        >>> ibs = ibeis.opendb(defaultdb='testdb1')
        >>> (aid1_list, aid2_list) = get_identify_training_aid_pairs(ibs)
        >>> aid1_list = aid1_list[0:min(100, len(aid1_list))]
        >>> aid2_list = aid2_list[0:min(100, len(aid2_list))]
        >>> # execute function
        >>> thumb1_list, thumb2_list = get_aidpair_identify_images(ibs, aid1_list, aid2_list)
        >>> ut.quit_if_noshow()
        >>> import plottool as pt
        >>> for aid1, aid2, thumb1, thumb2 in ut.InteractiveIter(zip(aid1_list, aid2_list, thumb1_list, thumb2_list), display_item=False):
        ...     print('aid1=%r, aid2=%r' % (aid1, aid2))
        ...     pt.imshow(thumb1, pnum=(1, 2, 1))
        ...     pt.imshow(thumb2, pnum=(1, 2, 2))
        ...     pt.update()
        >>> ut.show_if_requested()
    """
    # TODO: Cache this with visual uuids

    import vtool as vt
    print('[ibeget_aidpair_identify_images len(aid1_list)=%r' % (len(aid1_list)))
    print('base_size = %r' % (base_size,))
    assert len(aid1_list) == len(aid2_list)

    # Flatten to only apply chip operations once
    flat_unique, reconstruct_tup = ut.inverable_unique_two_lists(aid1_list, aid2_list)

    print('get_chips')
    chip_list = ibs.get_annot_chips(flat_unique)
    print('resize chips: base_size = %r' % (base_size,))
    target_height = base_size
    AUTO_SIZE = False
    if AUTO_SIZE:
        target_size = compute_target_size_from_aspect(chip_list, target_height)
    else:
        if stacked:
            target_size = (2 * base_size, base_size)
        else:
            target_size = (base_size, base_size)

    thumb_list = [vt.padded_resize(chip, target_size) for chip in ut.ProgressIter(chip_list)]

    thumb1_list, thumb2_list = ut.uninvert_unique_two_lists(thumb_list, reconstruct_tup)

    return thumb1_list, thumb2_list


def compute_target_size_from_aspect(chip_list, target_height=256):
    r"""
    Args:
        chip_list (list):
        target_height (int):

    Returns:
        ?: data

    CommandLine:
        python -m ibeis_cnn.ibsplugin --test-compute_target_size_from_aspect

    Example:
        >>> # DISABLE_DOCTEST
        >>> from ibeis_cnn.ibsplugin import *  # NOQA
        >>> import ibeis
        >>> # build test data
        >>> ibs = ibeis.opendb('testdb1')
        >>> (aid1_list, aid2_list) = get_identify_training_aid_pairs(ibs)
        >>> # execute function
        >>> chip_list = ibs.get_annot_chips(ut.unique_keep_order2(aid1_list + aid2_list))
        >>> target_height = 256
        >>> target_size = compute_target_size_from_aspect(chip_list, target_height)
        >>> # verify results
        >>> result = str(target_size)
        >>> print(result)
        (381, 256)
    """
    import vtool as vt
    sizes1 = np.array([vt.get_size(chip1) for chip1 in chip_list])
    ar1_list = sizes1.T[0] / sizes1.T[1]
    ave_ar = ar1_list.mean()
    target_size = (int(np.round(ave_ar * target_height)), target_height)
    return target_size


def extract_chip_from_gpath_into_square_worker(args):
    import vtool as vt
    gfpath, bbox, theta, target_size = args
    imgBGR = vt.imread(gfpath)  # Read parent image
    return vt.extract_chip_into_square(imgBGR, bbox, theta, target_size)


def extract_square_chips_from_images(ibs, aid_list, target_size):
    """
    Simple function for computing a set of chips without going through the whole ibeis stuff

    Args:
        ibs (IBEISController):  ibeis controller object
        aid_list (int):  list of annotation ids

    CommandLine:
        python -m ibeis_cnn.ibsplugin --test-extract_square_chips_from_images --show

    Example:
        >>> # DISABLE_DOCTEST
        >>> from ibeis_cnn.ibsplugin import *  # NOQA
        >>> import ibeis
        >>> # build test data
        >>> ibs = ibeis.opendb('PZ_MTEST')
        >>> aid_list = ibs.get_valid_aids()[0:40]
        >>> target_size = (220, 220)
        >>> # execute function
        >>> chipBGR_square_list = extract_square_chips_from_images(ibs, aid_list, target_size)
        >>> ut.quit_if_noshow()
        >>> import plottool as pt
        >>> for chipBGR in ut.InteractiveIter(chipBGR_square_list, display_item=False):
        ...     pt.imshow(chipBGR)
        ...     pt.update()
        >>> # verify results
        >>> print(result)
    """
    gfpath_list = ibs.get_annot_image_paths(aid_list)
    bbox_list   = ibs.get_annot_bboxes(aid_list)
    theta_list  = ibs.get_annot_thetas(aid_list)
    target_size_list = [target_size] * len(aid_list)
    args_iter = zip(gfpath_list, bbox_list, theta_list, target_size_list)
    chipBGR_square_gen = ut.generate(extract_chip_from_gpath_into_square_worker, args_iter)
    chipBGR_square_list = list(chipBGR_square_gen)
    return chipBGR_square_list


def view_training_data(ibs, **kwargs):
    """
    CommandLine:
        python -m ibeis_cnn.ibsplugin --test-view_training_data --db NNP_Master3

    Example:
        >>> # DISABLE_DOCTEST
        >>> from ibeis_cnn.ibsplugin import *  # NOQA
        >>> import ibeis
        >>> # build test data
        >>> ibs = ibeis.opendb(defaultdb='testdb1')
        >>> view_training_data(ibs)
    """
    data_fpath, labels_fpath, training_dpath = get_identify_training_fpaths(ibs, **kwargs)
    data = np.load(data_fpath, mmap_mode='r')
    #width = height = base_size * 2  # HACK FIXME
    #channels = 3
    #img_list = utils.convert_data_to_imglist(data, width, height, channels)
    import plottool as pt
    for img in ut.InteractiveIter(data, display_item=False):
        pt.imshow(img)
        pt.update()


class IdentifyDataConfig(NewConfigBase):
    def __init__(idcfg):
        idcfg.stacked = False
        idcfg.data_format = 'cv2'
        idcfg.base_size = 64

    def get_cfgstr(idcfg):
        cfgstr_list = [
            'stacked' if idcfg.stacked else 'strided',
            idcfg.data_format,
            'sz=%d' % (idcfg.base_size,),
        ]
        return ','.join(cfgstr_list)


def get_aidpair_training_data_and_labels(ibs, aid1_list, aid2_list, base_size=64, stacked=False, data_format='cv2'):
    thumb1_list, thumb2_list = get_aidpair_identify_images(ibs, aid1_list, aid2_list, base_size=base_size, stacked=stacked)
    assert data_format == 'cv2'
    # Stacking these might not be the exact correct thing to do.
    if stacked:
        img_list = [
            np.vstack((thumb1, thumb2)) for thumb1, thumb2, in
            zip(thumb1_list, thumb2_list)
        ]
    else:
        # Strided data comes in in pairs of two
        img_list = ut.flatten(list(zip(thumb1_list, thumb2_list)))
    data = np.array(img_list)
    del img_list
    #data = [img[None, :] for img in im
    #data = utils.convert_imagelist_to_data(img_list)
    labels = get_aidpair_training_labels(ibs, aid1_list, aid2_list)
    assert labels.shape[0] == data.shape[0] // 2
    from ibeis import const
    assert np.all(labels != const.TRUTH_UNKNOWN)
    return data, labels


def get_hotspotter_aid_pairs(ibs):
    aid_list = ibs.get_valid_aids()
    import utool as ut
    aid_list = ut.list_compress(aid_list, ibs.get_annot_has_groundtruth(aid_list))
    qres_list = ibs.query_chips(aid_list, aid_list)
    num_top = 3
    aid1_list = np.array(ut.flatten([[qres.qaid] * num_top for qres in qres_list]))
    aid2_list = np.array(ut.flatten([qres.get_top_aids()[0:num_top] for qres in qres_list]))
    return aid1_list, aid2_list


def filter_aid_pairs(aid1_list, aid2_list):
    """
    TODO: move to results_organizer
    """
    np.vstack((aid1_list, aid2_list)).T
    import vtool as vt
    index_list = vt.find_best_undirected_edge_indexes(np.vstack((aid1_list, aid2_list)).T)
    aid1_list = ut.list_take(aid1_list, index_list)
    aid2_list = ut.list_take(aid2_list, index_list)
    return aid1_list, aid2_list


#@ibeis.register_plugin()
def train_identification_pz():
    r"""

    CommandLine:
        python -m ibeis_cnn.train --test-train_identification_pz

    Example:
        >>> # DISABLE_DOCTEST
        >>> from ibeis_cnn.train import *  # NOQA
        >>> train_identification_pz()
    """
    print('get_identification_decision_training_data')
    import ibeis
    ibs = ibeis.opendb('NNP_Master3')
    base_size = 128
    #max_examples = 1001
    #max_examples = None
    max_examples = 400
    data_fpath, labels_fpath, training_dpath = ibsplugin.get_identify_training_fpaths(ibs, base_size=base_size, max_examples=max_examples)

    model = models.SiameseModel()
    config = dict(
        equal_batch_sizes=True,
        patience=50,
        batch_size=32,
        learning_rate=.03,
        show_confusion=False,
        run_test=None,
    )
    nets_dir = ibs.get_neuralnet_dir()
    ut.ensuredir(nets_dir)
    weights_fpath = join(training_dpath, 'ibeis_cnn_weights.pickle')
    train_harness.train(model, data_fpath, labels_fpath, weights_fpath, training_dpath, **config)
    #X = k


    # TODO: Finishme
    #NODUP_KEYPOINT_WARP = True
    #if NODUP_KEYPOINT_WARP:
    #    # Prevent duplicate keypoint warping computations
    #    fx1_list = [fm.T[0] for fm in fm_list]
    #    fx2_list = [fm.T[1] for fm in fm_list]
    #    aidfx1_list = [zip([aid1] * len(fx1), fx1) for aid1, fx1 in zip(aid1_list, fx1_list)]
    #    aidfx2_list = [zip([aid2] * len(fx2), fx2) for aid2, fx2 in zip(aid2_list, fx2_list)]
    #    iddict_ = {}
    #    unique_feat_ids1 = [vt.compute_unique_data_ids_(rows, iddict_=iddict_) for rows in aidfx1_list]
    #    unique_feat_ids2 = [vt.compute_unique_data_ids_(rows, iddict_=iddict_) for rows in aidfx2_list]
    #    unique_feat_ids = ut.flatten(unique_feat_ids1) + ut.flatten(unique_feat_ids2)
    #    print(len(set(unique_feat_ids)) / len(unique_feat_ids))


    #if False:
    #    aid_multilist = (np.array(aid1_list), np.array(aid2_list))
    #    flat_unique_, flat_groupx_multilist = ut.inverable_group_multi_list()
    #    fx1_list = [fm.T[0] for fm in fm_list]
    #    fx2_list = [fm.T[1] for fm in fm_list]
    #    fx_multilist = (fx1_list, fx2_list)
    #    fx_groups = [[ut.list_take(fx_multilist[listx], groupx) for listx, groupx in enumerate(groupx_list)] for groupx_list in flat_groupx_multilist]

    #    for fxgrp in fx_groups:
    #        flat_list1, cumsum1 = ut.invertible_flatten2(fxgrp)
    #        flat_list2, cumsum2 = ut.invertible_flatten2(flat_list1)
    #        unique_fx, invert_fx3 = np.unique(flat_list2, return_inverse=True)
    #        pass
    #    #[[ut.list_take(aid_multilist[listx], groupx) for listx, groupx in enumerate(groupx_list)] for groupx_list in flat_groupx_multilist]


            #patch_list = []
            #for fx, kp in zip(fxs, kpts):
            #    try:
            #        patch = cache_[(aid, fxs)]
            #    except KeyError:
            #        patch = vt.get_warped_patches(chip, kpts)[0]
            #        cache_[(aid, fx)] = patch
            #    patch_list.append(patch)
            #return patch_list



#def get_unique_featureids(aid1_list, aid2_list, fm_list):
#    import vtool as vt
#    vt.group_indices(np.array(aid1_list))
#    fx1_list = [fm.T[0] for fm in fm_list]
#    fx2_list = [fm.T[1] for fm in fm_list]
#    #aidfx1_list = [zip([aid1] * len(fx1), fx1) for aid1, fx1 in zip(aid1_list, fx1_list)]
#    #aidfx2_list = [zip([aid2] * len(fx2), fx2) for aid2, fx2 in zip(aid2_list, fx2_list)]
#    #iddict_ = {}
#    #unique_feat_ids1 = [vt.compute_unique_data_ids_(rows, iddict_=iddict_) for rows in aidfx1_list]
#    #unique_feat_ids2 = [vt.compute_unique_data_ids_(rows, iddict_=iddict_) for rows in aidfx2_list]
#    #unique_feat_ids = ut.flatten(unique_feat_ids1) + ut.flatten(unique_feat_ids2)
#    #return unique_feat_ids

#    #

    # check for any duplicates
    #if False:
    #    aidfx1_list = [zip([aid1] * len(fx1), fx1) for aid1, fx1 in zip(aid1_list, fx1_list)]
    #    aidfx2_list = [zip([aid2] * len(fx2), fx2) for aid2, fx2 in zip(aid2_list, fx2_list)]
    #    aidfx_list = ut.flatten(aidfx1_list) + ut.flatten(aidfx2_list)
    #    import vtool as vt
    #    unique_ids = vt.compute_unique_data_ids_(aidfx_list)
    #    print(len(set(unique_ids)) / len(unique_ids))

    #print('Reading keypoint sets')
    #
