#!/usr/bin/env python
"""
constructs the Theano optimization and trains a learning model,
optionally by initializing the network with pre-trained weights.
"""
from __future__ import absolute_import, division, print_function
from ibeis_cnn import utils
from ibeis_cnn import batch_processing as batch
from ibeis_cnn import draw_net
from six.moves import input
import sys
import time
import theano
import numpy as np
import cPickle as pickle
from lasagne import layers
import utool as ut
import six
from os.path import join, abspath, dirname, exists
print, rrr, profile = ut.inject2(__name__, '[ibeis_cnn.train_harness]')


def ensure_training_parameters(kwargs):
    # TODO: Separate these into their own categories
    #
    # Preprocessing parameters
    utils._update(kwargs, 'center',                  True)
    utils._update(kwargs, 'encode',                  True)  # only for categorization
    # Learning parameters
    utils._update(kwargs, 'learning_rate',           0.01)
    utils._update(kwargs, 'momentum',                0.9)
    utils._update(kwargs, 'batch_size',              128)
    utils._update(kwargs, 'regularization',          None)
    # Epoch parameters
    utils._update(kwargs, 'patience',                10)
    utils._update(kwargs, 'run_test',                10)  # Test every 10 epochs
    utils._update(kwargs, 'max_epochs',              kwargs.get('patience') * 10)
    utils._update(kwargs, 'output_dims',             None)
    # Visualization params
    utils._update(kwargs, 'show_confusion',          True)
    utils._update(kwargs, 'show_features',           False)


def ensure_training_state(X_train, kwargs):
    if kwargs.get('center'):
        print('[train] applying data centering...')
        utils._update(kwargs, 'center_mean', np.mean(X_train, axis=0))
        # utils._update(kwargs, 'center_std', np.std(X_train, axis=0))
        utils._update(kwargs, 'center_std', 255.0)
    else:
        utils._update(kwargs, 'center_mean', 0.0)
        utils._update(kwargs, 'center_std', 1.0)
    # Should these not be in kwargs?
    utils._update(kwargs, 'best_weights',        None)
    utils._update(kwargs, 'best_valid_accuracy', 0.0)
    utils._update(kwargs, 'best_test_accuracy',  0.0)
    utils._update(kwargs, 'best_train_loss',     np.inf)
    utils._update(kwargs, 'best_valid_loss',     np.inf)
    utils._update(kwargs, 'best_valid_accuracy', 0.0)
    utils._update(kwargs, 'best_test_accuracy',  0.0)


def print_data_label_info(data, labels):
    # print('[load] adding channels...')
    # data = utils.add_channels(data)
    print('[train] memory(data) = %r' % (ut.get_object_size_str(data),))
    print('[train] data.shape   = %r' % (data.shape,))
    print('[train] data.dtype   = %r' % (data.dtype,))
    print('[train] labels.shape = %r' % (labels.shape,))
    print('[train] labels.dtype = %r' % (labels.dtype,))
    labelhist = {key: len(val) for key, val in six.iteritems(ut.group_items(labels, labels))}
    print('[train] label histogram = \n' + ut.dict_str(labelhist))


def weight_initialization(model, output_layer, results_dpath, pretrained_weights_fpath, pretrained_kwargs, kwargs):
    try:
        arch_hashid = model.get_architecture_hashid()
    except Exception:
        arch_hashid = None

    if arch_hashid is not None:
        # TODO:
        # arch_hashid should show the architecture used for training
        # this should actually live outside and parallel to the data hash
        # Inside this directory will be something like:
        #  * release_state - shippable weights
        #  * master_state  - best current weights?
        #  * train_state_checkpoint<X> - older training checkpoints
        #  * training_state - most recent training checkpoint
        arch_dir = join(results_dpath, 'arch_' + arch_hashid)
        ut.ensuredir(arch_dir)
        # Try and load from where we left off
        training_state_fpath = join(arch_dir, 'training_state.cPkl')
        if ut.checkpath(training_state_fpath):
            print('[model] Using best weights from previous run')
            kwargs_state = ut.load_cPkl(training_state_fpath)
            best_weights = kwargs_state['best_weights']
            layers.set_all_param_values(output_layer, best_weights)
        else:
            print('[model] no pretrained weights')
    else:
        # OLD PRETRAINED WEIGHTS CODE
        # Load the pretrained model if specified
        if pretrained_weights_fpath is not None and exists(pretrained_weights_fpath):
            print('[model] loading pretrained weights from %s' % (pretrained_weights_fpath))
            # TODO: store model state in a class that is not kwargs
            with open(pretrained_weights_fpath, 'rb') as pfile:
                kwargs_state = pickle.load(pfile)
                pretrained_weights = kwargs_state.get('best_weights', None)
                layers.set_all_param_values(output_layer, pretrained_weights)
                if pretrained_kwargs:
                    kwargs = kwargs_state
        else:
            print('[model] no pretrained weights')
    return kwargs


def sample_train_valid_test(model, data, labels):
    # TODO: make this less memory intensive
    print('[train] creating train, validation datasaets...')
    memtrack = ut.MemoryTracker(disable=True)
    memtrack.report('sample_data0')
    data_per_label = getattr(model, 'data_per_label', 1)
    train_split = .2
    #train_split = .4
    #train_split = .5
    _tup = utils.train_test_split(data, labels, eval_size=train_split, data_per_label=data_per_label)
    X_train, y_train, X_valid, y_valid = _tup
    memtrack.report('sample_data1')
    _tup = utils.train_test_split(X_train, y_train, eval_size=0.1, data_per_label=data_per_label)
    X_train, y_train, X_test, y_test = _tup
    memtrack.report('sample_data2')
    #memtrack.report_obj(X_train, 'X_train')
    #memtrack.report_obj(X_valid, 'X_valid')
    #memtrack.report_obj(X_test, 'X_test')
    #memtrack.report_obj(data, 'data')
    print('len(X_train) = %r' % (len(X_train),))
    print('len(X_valid) = %r' % (len(X_valid),))
    print('len(X_test)  = %r' % (len(X_test),))
    return X_train, y_train, X_valid, y_valid, X_test, y_test


# ---------------


def train(model, data_fpath, labels_fpath, weights_fpath, results_dpath,
          pretrained_weights_fpath=None, pretrained_kwargs=False, **kwargs):
    """
    Driver function

    Args:
        data_fpath (str):
        labels_fpath (str):
        model (Model class):
        weights_fpath (str):
        pretrained_weights_fpath (None):
        pretrained_kwargs (bool):
    """

    ######################################################################################

    # Load the data
    print('\n[train] --- LOADING DATA ---')
    sys.stdout.flush()
    print('data_fpath = %r' % (data_fpath,))
    print('labels_fpath = %r' % (labels_fpath,))
    data, labels = utils.load(data_fpath, labels_fpath)

    # Training parameters defaults
    ensure_training_parameters(kwargs)

    # Ensure results dir
    weights_dpath = dirname(abspath(weights_fpath))
    ut.ensuredir(weights_dpath)
    ut.ensuredir(results_dpath)
    if pretrained_weights_fpath is not None:
        pretrained_weights_dpath = dirname(abspath(pretrained_weights_fpath))
        ut.ensuredir(pretrained_weights_dpath)

    print_data_label_info(data, labels)

    print('[train] kwargs = \n' + (ut.dict_str(kwargs)))

    # Build and print the model
    print('\n[train] --- BUILDING MODEL ---')
    if len(data.shape) == 3:
        # add channel dimension for implicit grayscale
        data.shape = data.shape + (1,)
    kwargs['model_shape'] = data.shape
    # Encoding labels
    if hasattr(model, 'initialize_encoder'):
        model.initialize_encoder(labels)
    kwargs['output_dims'] = model.output_dims
    input_cases, input_height, input_width, input_channels = kwargs['model_shape']  # SHOULD ERROR IF NOT SET
    batch_size  = kwargs['batch_size']
    output_dims = kwargs['output_dims']
    # Build architectuer
    # TODO: pass in fresh_model=True if this model is being trained from scratch.
    output_layer = model.build_model(
        batch_size, input_width, input_height,
        input_channels, output_dims, fresh_model=False)

    print('\n[train] --- MODEL INFO ---')
    if hasattr(model, 'print_layer_info'):
        model.print_architecture_str()
        model.print_layer_info()
    else:
        utils.print_layer_info(output_layer)

    # Create the Theano primitives
    # create theano symbolic expressions that define the network
    print('\n[train] --- COMPILING SYMBOLIC THEANO FUNCTIONS ---')
    print('[model] creating Theano primitives...')
    learning_rate_theano = theano.shared(utils.float32(kwargs['learning_rate']))
    theano_funcs = batch.create_theano_funcs(learning_rate_theano, output_layer, model, **kwargs)

    print('\n[train] --- WEIGHT INITIALIZATION ---')
    kwargs = weight_initialization(model, output_layer, results_dpath, pretrained_weights_fpath, pretrained_kwargs, kwargs)

    # draw_net.show_image_from_data(data)
    # TODO: Change this to use indices, # this causes too much data copying
    # Split the dataset into training and validation
    print('\n[train] --- SAMPLING DATA ---')
    X_train, y_train, X_valid, y_valid, X_test, y_test = sample_train_valid_test(model, data, labels)

    # Center the data by subtracting the mean (AFTER KWARGS UPDATE)
    ensure_training_state(X_train, kwargs)

    #ut.embed()
    #input('start training?')

    # Start the main training loop
    training_loop(model, X_train, y_train, X_valid, y_valid, X_test, y_test,
                  theano_funcs, output_layer, results_dpath, weights_fpath,
                  learning_rate_theano, kwargs)


@profile
def training_loop(model, X_train, y_train, X_valid, y_valid, X_test, y_test,
                  theano_funcs, output_layer, results_dpath, weights_fpath,
                  learning_rate_theano, kwargs):
    print('\n[train] --- TRAINING LOOP ---')
    # Begin training the neural network
    print('\n[train] starting training at %s with learning rate %.9f' %
          (utils.get_current_time(), kwargs['learning_rate']))
    printcol_info = utils.get_printcolinfo(kwargs.get('requested_headers', None))
    utils.print_header_columns(printcol_info)
    theano_backprop, theano_forward, theano_predict = theano_funcs
    epoch = 0
    epoch_marker  = epoch
    show_times    = kwargs.get('print_timing', False)
    show_features = kwargs.get('show_features', False)
    run_test      = kwargs.get('run_test', None)
    # Get the augmentation function, if there is one for this model
    augment_fn = getattr(model, 'augment', None)
    #draw_target_layers = [0, 1]
    draw_target_layers = [0]

    #save_on_best = True
    #show_on_best = True

    #training_state = {}
    # number of non-best iterations after, that triggers a best save
    save_after_best_wait_epochs = 5
    save_after_best_countdown = None

    while True:
        try:
            # Start timer
            t0 = time.time()

            # ---------------------------------------

            # Show first weights before any training
            if utils.checkfreq(show_features, epoch):
                with ut.Timer('show_features1', verbose=show_times):
                    draw_net.show_convolutional_layers(output_layer, results_dpath, target=draw_target_layers, epoch=epoch)

            # compute the loss over all training and validation batches
            with ut.Timer('train', verbose=show_times):
                train_loss = batch.process_train(X_train, y_train, theano_backprop,
                                                 model=model, augment=augment_fn,
                                                 rand=True, **kwargs)

            # TODO: only check validation once every <valid_freq> epochs
            with ut.Timer('validate', verbose=show_times):
                # TODO: generalize accuracy to arbitrary metrics
                data_valid = batch.process_valid(X_valid, y_valid, theano_forward,
                                                 model=model, augment=None,
                                                 rand=False, **kwargs)
                valid_loss, valid_accuracy = data_valid

            # If the training loss is nan, the training has diverged
            if np.isnan(train_loss):
                print('\n[train] training diverged\n')
                break

            # ---------------------------------------

            # Increment the epoch
            epoch += 1

            # Is this model the best we've ever seen?
            best_found = valid_loss < kwargs.get('best_valid_loss')
            if best_found:
                kwargs['best_epoch'] = epoch
                epoch_marker = epoch
                kwargs['best_weights'] = layers.get_all_param_values(output_layer)
                save_after_best_countdown = save_after_best_wait_epochs

            # compute the loss over all testing batches
            # if request_test or best_found:
            # Calculate request_test before adding to the epoch counter
            request_test = utils.checkfreq(run_test, epoch - 1)
            if request_test:
                # TODO: rectify this code with that in test.py
                with ut.Timer('test', verbose=show_times):
                    train_determ_loss = batch.process_train(X_train, y_train, theano_forward,
                                                            model=model, augment=augment_fn,
                                                            rand=False, **kwargs)

                    # If we want to output the confusion matrix, give the results path
                    test_results = batch.process_test(
                        X_test, y_test, theano_forward,
                        model=model, augment=None, rand=False, **kwargs)
                    loss, test_accuracy, prob_list, auglbl_list, pred_list, conf_list = test_results
                    if kwargs.get('show_confusion', False):
                        #output_confusion_matrix(results_path, **kwargs)
                        batch.output_confusion_matrix(X_test, results_dpath, test_results, model=model, **kwargs)
            else:
                train_determ_loss = None
                test_accuracy = None

            # TODO: allow for general metrics beyond train loss and valid loss
            # Running tab for what the best model
            if train_loss < kwargs.get('best_train_loss'):
                kwargs['best_train_loss'] = train_loss
            if train_determ_loss < kwargs.get('best_determ_loss'):
                kwargs['best_determ_loss'] = train_determ_loss
            if valid_loss < kwargs.get('best_valid_loss'):
                kwargs['best_valid_loss'] = valid_loss
            if valid_accuracy > kwargs.get('best_valid_accuracy'):
                kwargs['best_valid_accuracy'] = valid_accuracy
            if test_accuracy > kwargs.get('best_test_accuracy'):
                kwargs['best_test_accuracy'] = test_accuracy

            # Learning rate schedule update
            if epoch >= epoch_marker + kwargs.get('patience'):
                epoch_marker = epoch
                utils.set_learning_rate(learning_rate_theano, model.learning_rate_update)
                utils.print_header_columns(printcol_info)

            # End timer
            t1 = time.time()

            # Print the epoch
            duration = t1 - t0
            epoch_info = {
                'train_loss'          : train_loss,
                'train_determ_loss'   : train_determ_loss,
                'valid_loss'          : valid_loss,
                'valid_accuracy'      : valid_accuracy,
                'test_accuracy'       : test_accuracy,
                'epoch'               : epoch,
                'duration'            : duration,
            }
            keys_ = ['best_train_loss', 'best_valid_loss', 'best_valid_accuracy', 'best_test_accuracy']
            epoch_info.update(ut.dict_subset(kwargs, keys_))

            utils.print_epoch_info(printcol_info, epoch_info)

            if save_after_best_countdown is not None:
                if save_after_best_countdown == 0:
                    ## Callbacks on best found
                    utils.save_model(kwargs, weights_fpath)
                    draw_net.show_convolutional_layers(output_layer, results_dpath, target=draw_target_layers, epoch=epoch)
                    save_after_best_countdown = None
                else:
                    save_after_best_countdown -= 1

            # Break on max epochs
            if epoch >= kwargs.get('max_epochs'):
                print('\n[train] maximum number of epochs reached\n')
                break
        except KeyboardInterrupt:
            # We have caught the Keyboard Interrupt, figure out what resolution mode
            print('\n[train] Caught CRTL+C')
            resolution = ''
            while not (resolution.isdigit()):
                print('\n[train] What do you want to do?')
                print('[train]     0 - Continue')
                print('[train]     1 - Shock weights')
                print('[train]     2 - Save best weights')
                print('[train]     3 - View training directory')
                print('[train]     4 - Embed into IPython')
                print('[train]     5 - Draw current weights')
                print('[train]     6 - Show training state')
                print('[train]  ELSE - Stop network training')
                resolution = input('[train] Resolution: ')
            resolution = int(resolution)
            # We have a resolution
            if resolution == 0:
                print('resuming training...')
            elif resolution == 1:
                # Shock the weights of the network
                utils.shock_network(output_layer)
                epoch_marker = epoch
                utils.set_learning_rate(learning_rate_theano, model.learning_rate_shock)
                utils.print_header_columns(printcol_info)
            elif resolution == 2:
                # Save the weights of the network
                #ut.save_cPkl(weights_fpath, kwargs)
                utils.save_model(kwargs, weights_fpath)
            elif resolution == 3:
                ut.view_directory(results_dpath)
            elif resolution == 4:
                ut.embed()
            elif resolution == 5:
                output_fpath_list = draw_net.show_convolutional_layers(output_layer, results_dpath, target=draw_target_layers, epoch=epoch)
                for fpath in output_fpath_list:
                    ut.startfile(fpath)
            elif resolution == 6:
                print(ut.dict_str(kwargs, truncate=True, sorted_=True))
            else:
                # Terminate the network training
                raise

    # Save the best network
    utils.save_model(kwargs, weights_fpath)
