from __future__ import absolute_import, division, print_function
from ibeis_cnn import utils, draw_net
import utool as ut
import numpy as np
import lasagne
from lasagne import objectives
import theano.tensor as T
from lasagne import layers
import warnings
import theano
print, rrr, profile = ut.inject2(__name__, '[ibeis_cnn.batch_processing]')


def batch_iterator(X, y, batch_size, encoder=None, rand=False, augment=None,
                   center_mean=None, center_std=None, model=None, X_is_cv2_native=True, **kwargs):
    r"""
    Args:
        X (ndarray):
        y (ndarray):
        batch_size (int):
        encoder (None):
        rand (bool):
        augment (None):
        center_mean (None):
        center_std (None):

    CommandLine:
        python -m ibeis_cnn.utils --test-batch_iterator

    Example:
        >>> # DISABLE_DOCTEST
        >>> from ibeis_cnn.utils import *  # NOQA
        >>> # build test data
        >>> X = np.random.rand(64, 3, 5, 4)
        >>> y = (np.random.rand(64) * 4).astype(np.int32)
        >>> batch_size = 16
        >>> encoder = None
        >>> rand = True
        >>> augment = None
        >>> center_mean = None
        >>> center_std = None
        >>> data_per_label = 2
        >>> model = None
        >>> # execute function
        >>> result = batch_iterator(X, y, batch_size, encoder, rand, augment, center_mean, center_std)
        >>> # verify results
        >>> print(next(result))
    """
    verbose = kwargs.get('verbose', ut.VERYVERBOSE)
    data_per_label = getattr(model, 'data_per_label', 1) if model is not None else 1
    # divides X and y into batches of size bs for sending to the GPU
    if rand:
        # Randomly shuffle data
        X, y = utils.data_label_shuffle(X, y, data_per_label)
    if verbose:
        print('[batchiter] BEGIN')
        print('[batchiter] X.shape %r' % (X.shape, ))
        if y is not None:
            print('[batchiter] y.shape %r' % (y.shape, ))
    if y is not None:
        assert X.shape[0] == (y.shape[0] * data_per_label), 'bad data / label alignment'
    num_batches = (X.shape[0] + batch_size - 1) // batch_size
    if verbose:
        print('[batchiter] num_batches = %r' % (num_batches,))
    for batch_index in range(num_batches):
        # Get batch slice
        Xb, yb = utils.slice_data_labels(X, y, batch_size, batch_index, data_per_label)
        # Whiten
        Xb = utils.whiten_data(Xb, center_mean, center_std)
        # Augment
        if augment is not None:
            Xb_ = np.copy(Xb)
            yb_ = None if yb is None else np.copy(yb)
            Xb, yb = augment(Xb_, yb_)
        # Encode
        if yb is not None:
            if encoder is not None:
                yb = encoder.transform(yb)
            # Get corret dtype for y (after encoding)
            if data_per_label > 1:
                # TODO: FIX data_per_label ISSUES
                yb_buffer = -np.ones(len(yb) * (data_per_label - 1), np.int32)
                yb = np.hstack((yb, yb_buffer))
            yb = yb.astype(np.int32)
        # Convert cv2 format to Lasagne format for batching
        if X_is_cv2_native:
            Xb = Xb.transpose((0, 3, 1, 2))
        if verbose:
            print('[batchiter] Yielding batch:')
            print('[batchiter]   * Xb.shape = %r' % (Xb.shape,))
            print('[batchiter]   * yb.shape = %r' % (yb.shape,))
        # Ugg, we can't have data and labels of different lengths
        yield Xb, yb
    if verbose:
        print('[batchiter] END')


def process_batch(X_train, y_train, batch_size, theano_fn, **kwargs):
    """
    compute the loss over all training batches

    Jon, if you get to this before I do, please fix. -J

    CommandLine:
        python -m ibeis_cnn.batch_processing --test-process_batch

    Example:
        >>> # DISABLE_DOCTEST
        >>> from ibeis_cnn.batch_processing import *  # NOQA
        >>> from ibeis_cnn import models
        >>> model = models.DummyModel(autoinit=True)
        >>> X_train, y_train = model.make_random_testdata()
        >>> theano_fn = create_unbuffered_iter_funcs_train2(model)
        >>> kwargs = {'X_is_cv2_native': False}
        >>> batch_size = model.batch_size
        >>> (loss, accu, prob_list, albl_list, pred_list, conf_list) = process_batch(X_train, y_train, batch_size, theano_fn)
        >>> result = str((loss, accu, prob_list, albl_list, pred_list, conf_list))
        >>> print(result)

    Ignore:
        Xb, yb = batch_iter.next()
        assert Xb.shape == (8, 1, 4, 4)
        yb.shape == (8,)
    """
    batch_output_list = []  # NOQA
    output_names = [op.variable.name for op in theano_fn.outputs]  # NOQA
    albl_list = []  # [a]ugmented [l]a[b]e[l] list
    show = False
    batch_iter = batch_iterator(X_train, y_train, batch_size, **kwargs)
    for Xb, yb in batch_iter:
        # Runs a batch through the network and updates the weights. Just returns what it did
        batch_output = theano_fn(Xb, yb)
        albl_list.append(yb)
        batch_output_list.append(batch_output)

        if show:
            # Print the network output for the first batch
            print('--------------')
            print(ut.list_str(zip(output_names, batch_output)))
            print('Correct: ', yb)
            print('--------------')
            show = False
    # Convert to numpy array

    # get outputs of each type

    def concatenate_hack(sequence, axis=0):
        # Hack to fix numpy bug. concatenate should do hstacks on 0-dim arrays
        if len(_output_unstacked) > 0 and len(_output_unstacked[1].shape) == 0:
            res = np.hstack(_output_unstacked)
        else:
            res = np.concatenate(_output_unstacked, axis=axis)
        return res

    unstacked_output_gen = ([bop[count] for bop in batch_output_list] for count, name in enumerate(output_names))
    stacked_output_list  = [concatenate_hack(_output_unstacked, axis=-1) for _output_unstacked in unstacked_output_gen]

    albl_list = np.hstack(albl_list)

    # Calculate performance
    loss_index = ut.listfind(output_names, 'loss_train')
    if loss_index is not None:
        loss_list = stacked_output_list[loss_index]
        loss = np.mean(loss_list)

    pred_index = ut.listfind(output_names, 'prediction')
    if pred_index is not None:
        pred_list = stacked_output_list[pred_index]
        accu = np.mean(np.equal(albl_list, pred_list))

    # Return
    return loss, accu, prob_list, albl_list, pred_list, conf_list


def predict_batch(X_train, theano_fn, **kwargs):
    """
        compute the loss over all training batches

        Jon, if you get to this before I do, please fix. -J
    """
    prob_list = []
    pred_list = []
    conf_list = []
    for Xb, _ in batch_iterator(X_train, None, **kwargs):
        # Runs a batch through the network and updates the weights. Just returns what it did
        prob, pred, conf = theano_fn(Xb)
        prob_list.append(prob)
        pred_list.append(pred)
        conf_list.append(conf)
    # Convert to numpy array
    prob_list = np.vstack(prob_list)
    pred_list = np.hstack(pred_list)
    conf_list = np.hstack(conf_list)
    # Return
    return prob_list, pred_list, conf_list


def process_train(X_train, y_train, theano_fn, **kwargs):
    """ compute the loss over all training batches """
    results = process_batch(X_train, y_train, theano_fn, **kwargs)
    loss, accu, prob_list, albl_list, pred_list, conf_list = results
    # Return whatever metrics we want
    return loss


def process_valid(X_valid, y_valid, theano_fn, **kwargs):
    """ compute the loss over all validation batches """
    results = process_batch(X_valid, y_valid, theano_fn, **kwargs)
    loss, accu, prob_list, albl_list, pred_list, conf_list = results
    # rRturn whatever metrics we want
    return loss, accu


def process_test(X_test, y_test, theano_fn, results_path=None, **kwargs):
    """ compute the loss over all test batches """
    results = process_batch(X_test, y_test, theano_fn, **kwargs)
    loss, accu, prob_list, albl_list, pred_list, conf_list = results
    # Output confusion matrix
    if results_path is not None:
        # Grab model
        model = kwargs.get('model', None)
        mapping_fn = None
        if model is not None:
            mapping_fn = getattr(model, 'label_order_mapping', None)
        # TODO: THIS NEEDS TO BE FIXED
        label_list = list(range(kwargs.get('output_dims')))
        # Encode labels if avaialble
        encoder = kwargs.get('encoder', None)
        if encoder is not None:
            label_list = encoder.inverse_transform(label_list)
        # Make confusion matrix (pass X to write out failed cases)
        draw_net.show_confusion_matrix(albl_list, pred_list, label_list, results_path,
                                       mapping_fn, X_test)
    return accu


def process_predictions(X_test, theano_fn, **kwargs):
    """ compute the loss over all test batches """
    results = predict_batch(X_test, theano_fn, **kwargs)
    prob_list, pred_list, conf_list = results
    # Find whatever metrics we want
    encoder = kwargs.get('encoder', None)
    if encoder is not None:
        label_list = encoder.inverse_transform(pred_list)
    else:
        label_list = [None] * len(pred_list)
    return pred_list, label_list, conf_list


def create_theano_funcs(learning_rate_theano, output_layer, model, momentum=0.9,
                          input_type=T.tensor4, output_type=T.ivector,
                          regularization=None, **kwargs):
    """
    build the Theano functions (symbolic expressions) that will be used in the
    optimization refer to this link for info on tensor types:

    References:
        http://deeplearning.net/software/theano/library/tensor/basic.html
    """
    X = input_type('x')
    y = output_type('y')
    X_batch = input_type('x_batch')
    y_batch = output_type('y_batch')

    # Defaults that are overwritable by a model
    loss_function = utils.multinomial_nll
    if model is not None and hasattr(model, 'loss_function'):
        loss_function = model.loss_function

    # we are minimizing the multi-class negative log-likelihood
    print('Building symbolic loss function')
    objective = objectives.Objective(output_layer, loss_function=loss_function)
    loss = objective.get_loss(X_batch, target=y_batch)
    print('Building symbolic loss function (determenistic)')
    loss_determ = objective.get_loss(X_batch, target=y_batch, deterministic=True)

    #theano.printing.pydotprint(loss, outfile="./symbolic_graph_opt.png", var_with_name_simple=True)
    #ut.startfile('./symbolic_graph_opt.png')
    #ut.embed()

    # Regularize
    if regularization is not None:
        L2 = lasagne.regularization.l2(output_layer)
        loss += L2 * regularization

    # Run inference and get performance_outputs
    probabilities = output_layer.get_output(X_batch, deterministic=True)
    predictions = T.argmax(probabilities, axis=1)
    confidences = probabilities.max(axis=1)
    # accuracy = T.mean(T.eq(predictions, y_batch))
    performance_outputs = [probabilities, predictions, confidences]

    # Define how to update network parameters based on the training loss
    parameters = layers.get_all_params(output_layer)
    updates = lasagne.updates.nesterov_momentum(loss, parameters, learning_rate_theano, momentum)

    theano_backprop = theano.function(
        inputs=[theano.Param(X_batch), theano.Param(y_batch)],
        outputs=[loss] + performance_outputs,
        updates=updates,
        givens={
            X: X_batch,
            y: y_batch,
        },
    )

    theano_forward = theano.function(
        inputs=[theano.Param(X_batch), theano.Param(y_batch)],
        outputs=[loss_determ] + performance_outputs,
        updates=None,
        givens={
            X: X_batch,
            y: y_batch,
        },
    )

    theano_predict = theano.function(
        inputs=[theano.Param(X_batch)],
        outputs=performance_outputs,
        updates=None,
        givens={
            X: X_batch,
        },
    )

    return theano_backprop, theano_forward, theano_predict


def create_unbuffered_iter_funcs_train2(model):
    # Initialize symbolic input variables
    X_batch = T.tensor4(name='X_batch')
    y_batch = T.ivector(name='y_batch')
    # weird, idk why X and y exist
    X = T.tensor4(name='X_batch')
    y = T.ivector(name='y_batch')

    givens = {
        X: X_batch,
        y: y_batch,
    }

    output_layer = model.get_output_layer()

    # Build expression to evalute network output without dropout
    newtork_output = output_layer.get_output(X_batch, deterministic=True)
    newtork_output.name = 'network_output'

    # Build expression to evaluate loss
    objective = objectives.Objective(output_layer, loss_function=model.loss_function)
    loss_train = objective.get_loss(X_batch, target=y_batch)  # + 0.0001 * lasagne.regularization.l2(output_layer)
    loss_train.name = 'loss_train'

    # Build expression to evaluate updates
    with warnings.catch_warnings():
        warnings.filterwarnings('ignore', '.*topo.*')
        all_params = layers.get_all_params(output_layer)
    updates = lasagne.updates.nesterov_momentum(loss_train, all_params, model.learning_rate, model.momentum)

    # Get performance indicator outputs:

    # Build expression to convert network output into a prediction
    prediction = model.make_prediction_expr(newtork_output)

    # Build expression to compute accuracy
    accuracy = model.make_accuracy_expr(prediction, y_batch)

    theano_backprop = theano.function(
        inputs=[theano.Param(X_batch), theano.Param(y_batch)],
        outputs=[loss_train, newtork_output, prediction, accuracy],
        updates=updates,
        givens=givens
    )
    theano_backprop.name += ':theano_backprob:unbuffered'

    # TODO: Rectify

    return theano_backprop


def create_buffered_iter_funcs_train2(model, X_unshared, y_unshared):
    """
    WIP: NEW IMPLEMENTATION WITH PRELOADING GPU DATA

    build the Theano functions (symbolic expressions) that will be used in the
    optimization refer to this link for info on tensor types:

    References:
        http://deeplearning.net/software/theano/library/tensor/basic.html
        http://deeplearning.net/software/theano/tutorial/aliasing.html#borrowing-when-creating-shared-variables
        http://deeplearning.net/tutorial/lenet.html
        # TODO: Deal with batching to the GPU by setting the value of the shared variables.

    CommandLine:
        python -m ibeis_cnn.batch_processing --test-create_buffered_iter_funcs_train2

    Example:
        >>> # DISABLE_DOCTEST
        >>> from ibeis_cnn.batch_processing import *  # NOQA
        >>> from ibeis_cnn import draw_net
        >>> from ibeis_cnn import models
        >>> model = models.DummyModel(autoinit=True)
        >>> X_unshared, y_unshared = model.make_random_testdata()
        >>> train_iter = create_buffered_iter_funcs_train2(model, X_unshared, y_unshared)
        >>> print(train_iter)
        >>> loss_train, newtork_output, prediction, accuracy = train_iter(0)
        >>> print('loss = %r' % (loss,))
        >>> print('net_out = %r' % (outvec,))
        >>> print('newtork_output = %r' % (newtork_output,))
        >>> print('accuracy = %r' % (accuracy,))
        >>> #draw_net.draw_theano_symbolic_expression(train_iter)
        >>> assert outvec.shape == (model.batch_size, model.output_dims)
    """
    # Attempt to load data on to the GPU
    # Labels to go into the GPU as float32 and then cast to int32 once inside
    X_unshared = np.asarray(X_unshared, dtype=theano.config.floatX)
    y_unshared = np.asarray(y_unshared, dtype=theano.config.floatX)

    X_shared = theano.shared(X_unshared, borrow=True)
    y_shared = T.cast(theano.shared(y_unshared, borrow=True), 'int32')

    # Build expressions which sample a batch
    batch_size = model.batch_size

    # Initialize symbolic input variables
    index = T.lscalar(name='index')
    X_batch = T.tensor4(name='X_batch')
    y_batch = T.ivector(name='y_batch')

    WHITEN = True
    if WHITEN:
        # We might be able to perform some data augmentation here symbolicly
        data_mean = X_unshared.mean()
        data_std = X_unshared.std()
        givens = {
            X_batch: (X_shared[index * batch_size: (index + 1) * batch_size] - data_mean) / data_std,
            y_batch: y_shared[index * batch_size: (index + 1) * batch_size],
        }
    else:
        givens = {
            X_batch: X_shared[index * batch_size: (index + 1) * batch_size],
            y_batch: y_shared[index * batch_size: (index + 1) * batch_size],
        }

    output_layer = model.get_output_layer()

    # Build expression to evalute network output without dropout
    newtork_output = output_layer.get_output(X_batch, deterministic=True)
    newtork_output.name = 'network_output'

    # Build expression to evaluate loss
    objective = objectives.Objective(output_layer, loss_function=model.loss_function)
    loss_train = objective.get_loss(X_batch, target=y_batch)  # + 0.0001 * lasagne.regularization.l2(output_layer)
    loss_train.name = 'loss_train'

    # Build expression to evaluate updates
    with warnings.catch_warnings():
        warnings.filterwarnings('ignore', '.*topo.*')
        all_params = layers.get_all_params(output_layer)
    updates = lasagne.updates.nesterov_momentum(loss_train, all_params, model.learning_rate, model.momentum)

    # Get performance indicator outputs:

    # Build expression to convert network output into a prediction
    prediction = model.make_prediction_expr(newtork_output)

    # Build expression to compute accuracy
    accuracy = model.make_accuracy_expr(prediction, y_batch)

    theano_backprop = theano.function(
        inputs=[index],
        outputs=[loss_train, newtork_output, prediction, accuracy],
        updates=updates,
        givens=givens
    )
    theano_backprop.name += ':theano_backprob:indexed'

    #performance_outputs = [probabilities, predictions, confidences]

    #theano_backprop = theano.function(
    #    inputs=[theano.Param(X_batch), theano.Param(y_batch)],
    #    outputs=[loss] + performance_outputs,
    #    updates=updates,
    #    givens={
    #        X: X_batch,
    #        y: y_batch,
    #    },
    #)

    #theano_forward = theano.function(
    #    inputs=[theano.Param(X_batch), theano.Param(y_batch)],
    #    outputs=[loss_determ] + performance_outputs,
    #    updates=None,
    #    givens={
    #        X: X_batch,
    #        y: y_batch,
    #    },
    #)

    #theano_predict = theano.function(
    #    inputs=[theano.Param(X_batch)],
    #    outputs=performance_outputs,
    #    updates=None,
    #    givens={
    #        X: X_batch,
    #    },
    #)

    return theano_backprop


if __name__ == '__main__':
    """
    CommandLine:
        python -m ibeis_cnn.batch_processing
        python -m ibeis_cnn.batch_processing --allexamples
        python -m ibeis_cnn.batch_processing --allexamples --noface --nosrc
    """
    import multiprocessing
    multiprocessing.freeze_support()  # for win32
    import utool as ut  # NOQA
    ut.doctest_funcs()
