#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
tests a test set of data using a specified, pre0trained model and weights
"""
from __future__ import absolute_import, division, print_function
from ibeis_cnn import utils
from ibeis_cnn import models
from ibeis_cnn import net_strs
from ibeis_cnn import batch_processing as batch

import cPickle as pickle
from lasagne import layers

import theano
import time
import numpy as np
import utool as ut
import cv2
import six  # NOQA
from os.path import join, abspath
print, rrr, profile = ut.inject2(__name__, '[ibeis_cnn.test]')


def test(data_fpath, model, weights_fpath, results_dpath=None, labels_fpath=None, **kwargs):
    """
    Driver function

    Args:
        data_fpath (?):
        labels_fpath (?):
        model (?):
        weights_fpath (?):
    """

    ######################################################################################

    # Load the data
    print('\n[data] loading data...')
    print('data_fpath = %r' % (data_fpath,))
    X_test, y_test = utils.load(data_fpath, labels_fpath)
    if len(X_test.shape) == 3:
        # add channel dimension for implicit grayscale
        X_test.shape = X_test.shape + (1,)

    return test_data(X_test, y_test, model, weights_fpath, results_dpath, **kwargs)


def test_data(X_test, y_test, model, weights_fpath, results_dpath=None, **kwargs):
    """
    Driver function

    Args:
        data_fpath (str):
        labels_fpath (str):
        model (Model):
        weights_fpath (str):

    CommandLine:
        python -m ibeis_cnn.test --test-test_data

    Example:
        >>> # DISABLE_DOCTEST
        >>> from ibeis_cnn.test import *  # NOQA
    """

    ######################################################################################

    # Load the pretrained model if specified
    print('[model] loading pretrained weights from %s' % (weights_fpath))
    pretrained_weights = None
    with open(weights_fpath, 'rb') as pfile:
        kwargs = pickle.load(pfile)
        pretrained_weights = kwargs['best_weights']

    print('test kwargs = \n' + (ut.dict_str(kwargs, truncate=True)))

    # Build and print the model
    print('\n[model] building model...')
    #input_cases, input_height, input_width, input_channels = kwargs.get('model_shape', None)  # SHOULD ERROR IF NOT SET
    input_cases, input_height, input_width, input_channels = kwargs['model_shape']  # SHOULD ERROR IF NOT SET
    output_layer = model.build_model(
        kwargs.get('batch_size'), input_width, input_height,
        input_channels, kwargs.get('output_dims'))
    net_strs.print_layer_info(output_layer)

    # Set weights to model
    layers.set_all_param_values(output_layer, pretrained_weights)

    # Create the Theano primitives
    print('[model] creating Theano primitives...')
    learning_rate_theano = theano.shared(utils.float32(kwargs.get('learning_rate')))
    theano_funcs = batch.create_theano_funcs(learning_rate_theano,
                                             output_layer, model,
                                             request_backprop=False,
                                             request_predict=True,
                                             **kwargs)
    theano_backprop, theano_forward, theano_predict = theano_funcs

    # Begin testing with the neural network
    print('\n[test] starting testing with batch size %0.1f' % (kwargs.get('batch_size'), ))

    # Start timer
    t0 = time.time()

    pred_list, label_list, conf_list, prob_list = batch.process_predictions(
        X_test, theano_predict, model=model, showprog=True, **kwargs)

    # TODO: Rectify this code with the request_test section in train_harness
    # Maybe move all of test.py into train_harness.py
    if y_test is not None:
        test_results = batch.process_test(X_test, y_test, theano_forward,
                                          model=model, augment=None,
                                          rand=False, **kwargs)
        loss, accu_test, prob_list, auglbl_list, pred_list, conf_list = test_results
        if results_dpath is not None:
            #output_confusion_matrix(results_path, **kwargs)
            batch.output_confusion_matrix(X_test, results_dpath, test_results, model=model, **kwargs)
        print('Test accuracy for %d examples: %0.2f' % (len(X_test), accu_test, ))

    # End timer
    t1 = time.time()
    print('\n[test] prediction took %0.2f seconds' % (t1 - t0, ))
    return pred_list, label_list, conf_list, prob_list


def test_data2(X_test, y_test, model, weights_fpath, **kwargs):
    """
    Example:
        >>> # DISABLE_DOCTEST
        >>> from ibeis_cnn.test import *  # NOQA
    """

    ######################################################################################

    print('[model] loading pretrained weights from %s' % (weights_fpath))
    pretrained_weights = None
    with open(weights_fpath, 'rb') as pfile:
        kwargs = pickle.load(pfile)
        pretrained_weights = kwargs['best_weights']

    # Build and print the model
    print('\n[model] building model...')
    #input_cases, input_height, input_width, input_channels = kwargs.get('model_shape', None)  # SHOULD ERROR IF NOT SET
    input_cases, input_height, input_width, input_channels = kwargs['model_shape']  # SHOULD ERROR IF NOT SET
    output_layer = model.build_model(
        kwargs.get('batch_size'), input_width, input_height,
        input_channels, kwargs.get('output_dims'))
    net_strs.print_layer_info(output_layer)

    print('test kwargs = \n' + (ut.dict_str(kwargs, truncate=True)))
    # Set weights to model
    if pretrained_weights is not None:
        layers.set_all_param_values(output_layer, pretrained_weights)

    print('test kwargs = \n' + (ut.dict_str(kwargs, truncate=True)))

    # Create the Theano primitives
    print('[model] creating Theano primitives...')
    learning_rate_theano = theano.shared(utils.float32(kwargs.get('learning_rate')))
    theano_funcs = batch.create_theano_funcs(learning_rate_theano,
                                             output_layer, model,
                                             request_backprop=False,
                                             request_predict=True,
                                             **kwargs)
    theano_backprop, theano_forward, theano_predict = theano_funcs

    # Begin testing with the neural network
    print('\n[test] starting testing with batch size %0.1f' % (kwargs.get('batch_size'), ))

    # Start timer
    t0 = time.time()

    pred_list, label_list, conf_list, prob_list = batch.process_predictions(
        X_test, theano_predict, model=model, showprog=True, **kwargs)

    # End timer
    t1 = time.time()
    print('\n[test] prediction took %0.2f seconds' % (t1 - t0, ))
    return pred_list, label_list, conf_list, prob_list


def display_caffe_model(weights_model_path, results_path, **kwargs):
    """
    Driver function

    Args:
        data_fpath (?):
        labels_fpath (?):
        model (?):
        weights_fpath (?):
    """

    ######################################################################################

    # Load the pretrained model if specified
    print('[model] loading pretrained weights and model from %s' % (weights_model_path))
    pretrained_weights = None
    with open(weights_model_path, 'rb') as pfile:
        pretrained_weights = pickle.load(pfile)

    utils.show_convolutional_features(pretrained_weights, results_path, color=True, target=0)
    utils.show_convolutional_features(pretrained_weights, results_path, color=False, target=0)


def review_labels(id_path, data_fpath, labels_fpath, model, weights_fpath, **kwargs):
    print('\n[data] loading data...')
    print('data_fpath = %r' % (data_fpath,))
    ids_test = utils.load_ids(id_path)
    X_test, y_test = utils.load(data_fpath, labels_fpath)
    pred_list, label_list, conf_list = test_data(X_test, None, model, weights_fpath, **kwargs)

    new_y_test = []
    new_csv = []
    for y, label, id_, image in zip(y_test, label_list, ids_test, X_test):
        print(y, label, id_)
        if y != label:
            title = 'K: %s - S: %s' % (y, label, )
            key = None
            cv2.imshow(title, image)
            while key not in ['k', 's']:
                key = chr(cv2.waitKey()).lower()
                # print('    %s' % (key, ))
            cv2.destroyAllWindows()
            if key == 'k':
                print('    Keeping...')
            elif key == 's':
                print('    Switching...')
                y = label
            else:
                raise IOError('Specified an illegal character (can never happen)')
        new_y_test.append(y)
        new_csv.append('%s,%s' % (id_, y, ))

    new_y_test = np.hstack(new_y_test)
    np.save('y.npy', new_y_test)

    with open('labels.csv', 'w') as csv_file:
        new_csv = '\n'.join(new_csv)
        csv_file.write(new_csv)


def test_viewpoint_pz():
    r"""
    CommandLine:
        python -m ibeis_cnn.test --test-test_viewpoint_pz

    Example:
        >>> # DISABLE_DOCTEST
        >>> from ibeis_cnn.test import *  # NOQA
        >>> test_viewpoint_pz()
    """
    project_name            = 'viewpoint_pz'
    model                   = models.ViewpointModel()
    root                    = abspath(join('..', 'data'))

    test_data_fpath         = join(root, 'numpy',   project_name, 'X.npy')
    test_labels_fpath       = join(root, 'numpy',   project_name, 'y.npy')
    results_dpath           = join(root, 'results', project_name)
    weights_fpath           = join(root, 'nets',    project_name, 'ibeis_cnn_weights.pickle')
    config = {}
    test(test_data_fpath, model, weights_fpath, results_dpath, test_labels_fpath, **config)


def test_viewpoint():
    r"""
    CommandLine:
        python -m ibeis_cnn.test --test-test_viewpoint

    Example:
        >>> # DISABLE_DOCTEST
        >>> from ibeis_cnn.test import *  # NOQA
        >>> test_viewpoint()
    """
    project_name            = 'viewpoint'
    model                   = models.ViewpointModel()
    root                    = abspath(join('..', 'data'))

    test_data_fpath         = join(root, 'numpy',   project_name, 'X.npy')
    test_labels_fpath       = join(root, 'numpy',   project_name, 'y.npy')
    results_dpath           = join(root, 'results', project_name)
    weights_fpath           = join(root, 'nets',    project_name, 'ibeis_cnn_weights.pickle')
    config = {}

    test(test_data_fpath, model, weights_fpath, results_dpath, test_labels_fpath, **config)


def test_review():
    r"""
    CommandLine:
        python -m ibeis_cnn.test --test-test_review

    Example:
        >>> # DISABLE_DOCTEST
        >>> from ibeis_cnn.test import *  # NOQA
        >>> test_review()
    """
    project_name            = 'viewpoint'
    model                   = models.ViewpointModel()
    root                    = abspath(join('..', 'data'))

    test_ids_fpath          = join(root, 'numpy', project_name, 'ids.npy')
    test_data_fpath         = join(root, 'numpy', project_name, 'X.npy')
    test_labels_fpath       = join(root, 'numpy', project_name, 'y.npy')
    weights_fpath           = join(root, 'nets',  project_name, 'ibeis_cnn_weights.pickle')
    config = {}

    review_labels(test_ids_fpath, test_data_fpath, test_labels_fpath, model, weights_fpath, **config)


def test_display_caffenet():
    r"""
    CommandLine:
        python -m ibeis_cnn.test --test-test_display_caffenet

    Example:
        >>> # DISABLE_DOCTEST
        >>> from ibeis_cnn.test import *  # NOQA
        >>> test_display_caffenet()
    """
    project_name            = 'caffenet'
    root                    = abspath(join('..', 'data'))

    results_dpath           = join(root, 'results', project_name)
    weights_model_fpath     = join(root, 'nets',    project_name, 'caffenet.caffe.pickle')
    config = {}

    display_caffe_model(weights_model_fpath, results_dpath, **config)


def test_display_vgg():
    r"""
    CommandLine:
        python -m ibeis_cnn.test --test-test_display_vgg

    Example:
        >>> # DISABLE_DOCTEST
        >>> from ibeis_cnn.test import *  # NOQA
        >>> test_display_vgg()
    """
    project_name            = 'vgg'
    root                    = abspath(join('..', 'data'))

    results_dpath           = join(root, 'results', project_name)
    weights_model_fpath     = join(root, 'nets',    project_name, 'vgg.caffe.pickle')
    config = {}

    display_caffe_model(weights_model_fpath, results_dpath, **config)


if __name__ == '__main__':
    """
    CommandLine:
        python -m ibeis_cnn.test
        python -m ibeis_cnn.test --allexamples
        python -m ibeis_cnn.test --allexamples --noface --nosrc

    CommandLine:
        cd %CODE_DIR%/ibies_cnn/code
        cd $CODE_DIR/ibies_cnn/code
        code
        cd ibeis_cnn/code
        python test.py

    PythonPrereqs:
        pip install theano
        pip install git+https://github.com/Lasagne/Lasagne.git
        pip install git+git://github.com/lisa-lab/pylearn2.git
        #pip install lasagne
        #pip install pylearn2
        git clone git://github.com/lisa-lab/pylearn2.git
        git clone https://github.com/Lasagne/Lasagne.git
        cd pylearn2
        python setup.py develop
        cd ..
        cd Lesagne
        git checkout 8758ac1434175159e5c1f30123041799c2b6098a
        python setup.py develop
    """
    import multiprocessing
    multiprocessing.freeze_support()  # for win32
    import utool as ut  # NOQA
    ut.doctest_funcs()
