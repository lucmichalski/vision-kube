# -*- coding: utf-8 -*-
"""
file model.py
allows the definition of different models to be trained
for initialization: Lasagne/lasagne/init.py
for nonlinearities: Lasagne/lasagne/nonlinearities.py
for layers: Lasagne/lasagne/layers/
"""
from __future__ import absolute_import, division, print_function
import lasagne  # NOQA
from lasagne import layers
from lasagne import nonlinearities
from lasagne import init
from ibeis_cnn import lasange_ext
import random
import functools
import six
import theano.tensor as T
import numpy as np
from ibeis_cnn import abstract_models
from ibeis_cnn import custom_layers
import utool as ut
print, rrr, profile = ut.inject2(__name__, '[ibeis_cnn.models]')
#from ibeis_cnn import utils
#import sklearn.preprocessing
#import cPickle as pickle
# from os.path import join
# import cPickle as pickle

Conv2DLayer = custom_layers.Conv2DLayer
MaxPool2DLayer = custom_layers.MaxPool2DLayer


@six.add_metaclass(ut.ReloadingMetaclass)
class DummyModel(abstract_models.AbstractCategoricalModel):
    def __init__(model, autoinit=False, batch_size=8, input_shape=(None, 1, 4, 4)):
        super(DummyModel, model).__init__()
        model.network_layers = None
        model.data_per_label = 1
        model.input_shape = input_shape
        model.output_dims = 5
        model.learning_rate = .001
        model.batch_size = 8
        model.momentum = .9
        if autoinit:
            model.initialize_architecture()

    def make_random_testdata(model, num=1000, seed=0):
        np.random.seed(seed)
        X_unshared = np.random.rand(num, * model.input_shape[1:]).astype(np.float32)
        y_unshared = (np.random.rand(num) * model.output_dims).astype(np.int32)
        if ut.VERBOSE:
            print('made random testdata')
            print('size(X_unshared) = %r' % (ut.get_object_size_str(X_unshared),))
            print('size(y_unshared) = %r' % (ut.get_object_size_str(y_unshared),))
        return X_unshared, y_unshared

    def make_prediction_expr(model, newtork_output):
        prediction = T.argmax(newtork_output, axis=1)
        prediction.name = 'prediction'
        return prediction

    def make_accuracy_expr(model, prediction, y_batch):
        accuracy = T.mean(T.eq(prediction, y_batch))
        accuracy.name = 'accuracy'
        return accuracy

    #def get_loss_function(model):
    #    return T.nnet.categorical_crossentropy

    def initialize_architecture(model, verbose=True):
        input_shape = model.input_shape
        _P = functools.partial
        network_layers_def = [
            _P(layers.InputLayer, shape=input_shape),
            _P(Conv2DLayer, num_filters=16, filter_size=(3, 3)),
            _P(Conv2DLayer, num_filters=16, filter_size=(2, 2)),
            _P(layers.DenseLayer, num_units=8),
            _P(layers.DenseLayer, num_units=model.output_dims,
               nonlinearity=nonlinearities.softmax,
               W=init.Orthogonal(),),
        ]
        network_layers = abstract_models.evaluate_layer_list(network_layers_def)
        model.network_layers = network_layers
        output_layer = model.network_layers[-1]
        if verbose:
            print('initialize_architecture')
        if ut.VERBOSE:
            model.print_architecture_str()
            model.print_layer_info()
        return output_layer

    #def initialize_symbolic_inputs(model):
    #    model.symbolic_index = T.lscalar(name='index')
    #    model.symbolic_X = T.tensor4(name='X')
    #    model.symbolic_y = T.ivector(name='y')

    #def initialize_symbolic_updates(model):
    #    pass

    #def initialize_symbolic_outputs(model):
    #    pass


class SiameseCenterSurroundModel(abstract_models.BaseModel):
    """
    Model for individual identification

    TODO:
        RBM / EBM  - http://deeplearning.net/tutorial/rbm.html
    """
    def __init__(model, autoinit=False, batch_size=128, input_shape=(None, 3, 64, 64)):
        super(SiameseCenterSurroundModel, model).__init__()
        model.network_layers = None
        model.input_shape = input_shape
        model.batch_size = batch_size
        model.output_dims = 1
        # bad name, says that this network will take
        # 2*N images in a batch and N labels that map to
        # two images a piece
        model.data_per_label = 2
        model.needs_padding = False
        if autoinit:
            model.initialize_architecture()

    def build_model(model, batch_size, input_width, input_height,
                    input_channels, output_dims, verbose=True, **kwargs):
        r"""
        """
        #print('build model may override settings')
        input_shape = (batch_size, input_channels, input_width, input_height)
        model.input_shape = input_shape
        model.batch_size = batch_size
        model.output_dims = 1
        model.initialize_architecture(verbose=verbose, **kwargs)
        output_layer = model.get_output_layer()
        return output_layer

    def augment(self, Xb, yb=None):
        """
        CommandLine:
            python -m ibeis_cnn.models --test-SiameseCenterSurroundModel.augment --show

        Example:
            >>> # DISABLE_DOCTEST
            >>> from ibeis_cnn.models import *  # NOQA
            >>> from ibeis_cnn import train, utils
            >>> data, labels = train.testdata_patchmatch()
            >>> cv2_data = utils.convert_theano_images_to_cv2_images(data)
            >>> batch_size = 128
            >>> Xb, yb = cv2_data[0:batch_size], labels[0:batch_size // 2]
            >>> self = SiameseCenterSurroundModel()
            >>> Xb1, yb1 = self.augment(Xb.copy(), yb.copy())
            >>> modified_indexes = np.where((Xb1 != Xb).sum(-1).sum(-1).sum(-1) > 0)[0]
            >>> ut.quit_if_noshow()
            >>> import plottool as pt
            >>> pt.imshow(Xb[modified_indexes[0]], pnum=(2, 2, 1))
            >>> pt.imshow(Xb1[modified_indexes[0]], pnum=(2, 2, 2))
            >>> pt.imshow(Xb[modified_indexes[1]], pnum=(2, 2, 3))
            >>> pt.imshow(Xb1[modified_indexes[1]], pnum=(2, 2, 4))
            >>> ut.show_if_requested()
        """
        import functools
        #Xb = Xb.copy()
        #if yb is not None:
        #    yb = yb.copy()
        Xb1, Xb2 = Xb[::2], Xb[1::2]
        rot_transforms  = [functools.partial(np.rot90, k=k) for k in range(1, 4)]
        flip_transforms = [np.fliplr, np.flipud]
        prob_rotate = .3
        prob_flip   = .3

        num = len(Xb1)

        # Determine which examples will be augmented
        rotate_flags = [random.uniform(0.0, 1.0) <= prob_rotate for _ in range(num)]
        flip_flags   = [random.uniform(0.0, 1.0) <= prob_flip for _ in range(num)]

        # Determine which functions to use
        rot_fn_list  = [random.choice(rot_transforms) if flag else None for flag in rotate_flags]
        flip_fn_list = [random.choice(flip_transforms) if flag else None for flag in flip_flags]

        for index, func_list in enumerate(zip(rot_fn_list, flip_fn_list)):
            for func in func_list:
                if func is not None:
                    pass
                    Xb1[index] = func(Xb1[index])
                    Xb2[index] = func(Xb2[index])
        return Xb, yb

    def get_2ch2stream_def(model, verbose=True, **kwargs):
        """
        Notes:

            (i) 2ch-2stream consists of two branches
                C(95, 5, 1)- ReLU-
                P(2, 2)-
                C(96, 3, 1)- ReLU-
                P(2, 2)-
                C(192, 3, 1)- ReLU-
                C(192, 3, 1)- ReLU,

                one for central and one for surround parts, followed by
                F(768)- ReLU-
                F(1)
        """
        raise NotImplementedError('The 2-channel part is not yet implemented')
        _P = functools.partial
        leaky_kw = dict(nonlinearity=nonlinearities.LeakyRectify(leakiness=(1. / 10.)))
        orthog_kw = dict(W=init.Orthogonal())
        neuron_initkw = ut.merge_dicts(orthog_kw, leaky_kw)

        network_layers_def = (
            [
                _P(layers.InputLayer, shape=model.input_shape),
                # TODO: Stack Inputs by making a 2 Channel Layer
                _P(custom_layers.CenterSurroundLayer),

                #layers.GaussianNoiseLayer,
                #caffenet.get_conv2d_layer(0, trainable=False, **leaky),
                #lasange_ext.freeze_params,
                _P(Conv2DLayer, num_filters=96, filter_size=(5, 5), stride=(1, 1), name='C0', **neuron_initkw),
                _P(MaxPool2DLayer, pool_size=(2, 2), stride=(2, 2), name='P0'),
                _P(Conv2DLayer, num_filters=96, filter_size=(3, 3), name='C1', **neuron_initkw),
                _P(MaxPool2DLayer, pool_size=(2, 2), stride=(2, 2), name='P1'),
                _P(Conv2DLayer, num_filters=192, filter_size=(3, 3), name='C2', **neuron_initkw),
                _P(Conv2DLayer, num_filters=192, filter_size=(3, 3), name='C3', **neuron_initkw),
                #_P(custom_layers.L2NormalizeLayer, axis=2),
                _P(custom_layers.SiameseConcatLayer, axis=1, data_per_label=4),  # 4 when CenterSurroundIsOn
                #_P(custom_layers.SiameseConcatLayer, data_per_label=2),
                _P(layers.DenseLayer, num_units=768, name='F1',  **neuron_initkw),
                _P(layers.DropoutLayer, p=0.5),
                _P(layers.DenseLayer, num_units=1, name='F2', **neuron_initkw),
            ]
        )
        return network_layers_def

    def get_siam2stream_def(model, verbose=True, **kwargs):
        """
        Notes:
            (viii) siam-2stream has 4 branches
                C(96, 4, 2)- ReLU-
                P(2, 2)-
                C(192, 3, 1)- ReLU-
                C(256, 3, 1)- ReLU-
                C(256, 3, 1)- ReLU

                (coupled in pairs for central and surround streams, and decision layer)

                F(512)-ReLU-
                F(1)
        """
        _P = functools.partial

        leaky_kw = dict(nonlinearity=nonlinearities.LeakyRectify(leakiness=(1. / 10.)))
        orthog_kw = dict(W=init.Orthogonal())
        neuron_initkw = ut.merge_dicts(orthog_kw, leaky_kw)
        if not kwargs.get('fresh_model', False):
            # FIXME: figure out better way of encoding that Orthogonal
            # Initialization doesnt need to happen. Or do initialization of
            # weights after the network has been built.
            # don't do fancy initializating unless training from scratch
            del neuron_initkw['W']

        network_layers_def = (
            [
                _P(layers.InputLayer, shape=model.input_shape),
                # TODO: Stack Inputs by making a 2 Channel Layer
                _P(custom_layers.CenterSurroundLayer),

                layers.GaussianNoiseLayer,
                #caffenet.get_conv2d_layer(0, trainable=False, **leaky),
                _P(Conv2DLayer, num_filters=96, filter_size=(4, 4), name='C0', **neuron_initkw),
                _P(MaxPool2DLayer, pool_size=(2, 2), stride=(2, 2), name='P0'),
                _P(Conv2DLayer, num_filters=192, filter_size=(3, 3), name='C2', **neuron_initkw),
                _P(Conv2DLayer, num_filters=256, filter_size=(3, 3), name='C3', **neuron_initkw),
                _P(Conv2DLayer, num_filters=256, filter_size=(3, 3), name='C3', **neuron_initkw),
                #_P(custom_layers.L2NormalizeLayer, axis=2),
                _P(custom_layers.SiameseConcatLayer, axis=1, data_per_label=4),  # 4 when CenterSurroundIsOn
                #_P(custom_layers.SiameseConcatLayer, data_per_label=2),
                _P(layers.DenseLayer, num_units=512, name='F1',  **neuron_initkw),
                _P(layers.DropoutLayer, p=0.5),
                _P(layers.DenseLayer, num_units=1, name='F2', **neuron_initkw),
            ]
        )
        #raise NotImplementedError('The 2-channel part is not yet implemented')
        return network_layers_def

    def initialize_architecture(model, verbose=True, **kwargs):
        r"""
        Notes:
            http://arxiv.org/pdf/1504.03641.pdf

            Performance of several models on the local image patches benchmark.
            The shorthand notation used was the following.
            C(n, k, s) is a convolutional layer with n filters of spatial size k x k applied with stride s.
            P(k, s) is a max-pooling layer of size k x k applied with stride s.
            F(n) denotes a fully connected linear layer with n output
            units.
            The models architecture is as follows:

            (ii)
                2ch-deep =
                    C(96, 4, 3)- Stack(96)- P(2, 2)- Stack(192)- F(1),
                Stack(n) =
                    C(n, 3, 1)- ReLU- C(n, 3, 1)- ReLU- C(n, 3, 1)- ReLU.

            (iii)
                2ch =
                    C(96, 7, 3)- ReLU- P(2, 2)- C(192, 5, 1)- ReLU- P(2, 2)- C(256, 3, 1)- ReLU- F(256)- ReLU- F(1)

            (iv)
                siam has two branches
                    C(96, 7, 3)- ReLU- P(2, 2)- C(192, 5, 1)- ReLU- P(2, 2)- C(256, 3, 1)- ReLU
                and decision layer
                    F(512)- ReLU- F(1)

            (v) siam-l2 reduces to a single branch of siam

            (vi) pseudo-siam is uncoupled version of siam

            (vii) pseudo-siam-l2 reduces to a single branch of pseudo-siam

            (ix) siam-2stream-l2 consists of one central and one surround
                branch of siam-2stream.

        CommandLine:
            python -m ibeis_cnn.models --test-SiameseCenterSurroundModel.initialize_architecture
            python -m ibeis_cnn.models --test-SiameseCenterSurroundModel.initialize_architecture --verbcnn
            python -m ibeis_cnn.train --test-train_patchmatch_pz --vtd --max-examples=5 --batch_size=128 --learning_rate .0000001 --verbcnn
            python -m ibeis_cnn.train --test-train_patchmatch_pz --vtd

        Example:
            >>> # DISABLE_DOCTEST
            >>> from ibeis_cnn.models import *  # NOQA
            >>> # build test data
            >>> batch_size = 128
            >>> input_shape = (batch_size, 3, 64, 64)
            >>> verbose = True
            >>> model = SiameseCenterSurroundModel(batch_size=batch_size, input_shape=input_shape)
            >>> # execute function
            >>> output_layer = model.initialize_architecture()
            >>> print('\n---- Arch Str')
            >>> model.print_architecture_str(sep='\n')
            >>> print('\n---- Layer Info')
            >>> model.print_layer_info()
            >>> print('\n---- HashID')
            >>> print('hashid=%r' % (model.get_architecture_hashid()),)
            >>> print('----')
            >>> # verify results
            >>> result = str(output_layer)
            >>> print(result)

        """
        # TODO: remove output dims
        #_P = functools.partial
        (_, input_channels, input_width, input_height) = model.input_shape
        if verbose:
            print('[model] Initialize center surround siamese model architecture')
            print('[model]   * batch_size     = %r' % (model.batch_size,))
            print('[model]   * input_width    = %r' % (input_width,))
            print('[model]   * input_height   = %r' % (input_height,))
            print('[model]   * input_channels = %r' % (input_channels,))
            print('[model]   * output_dims    = %r' % (model.output_dims,))

        network_layers_def = model.get_siam2stream_def(verbose=verbose, **kwargs)

        # connect and record layers
        network_layers = abstract_models.evaluate_layer_list(network_layers_def, verbose=verbose)
        model.network_layers = network_layers
        output_layer = model.network_layers[-1]
        model.output_layer = output_layer
        return output_layer

    def reinit_weights(model, W=init.Orthogonal()):
        """
        initailizes weights after the architecture has been defined.
        """
        weights_list = lasagne.layers.get_all_params(model.output_layer, regularizable=True, trainable=True)
        for weights in weights_list:
            shape = weights.get_value().shape
            new_values = W.sample(shape)
            weights.set_value(new_values)

    def loss_function(model, network_output, Y, T=T, verbose=True):
        """

        CommandLine:
            python -m ibeis_cnn.models --test-loss_function

        Example:
            >>> # DISABLE_DOCTEST
            >>> from ibeis_cnn.models import *  # NOQA
            >>> from ibeis_cnn import train
            >>> from ibeis_cnn import batch_processing as batch
            >>> data, labels = train.testdata_patchmatch()
            >>> model = SiameseCenterSurroundModel(autoinit=True, input_shape=(128,) + (data.shape[1:]))
            >>> theano_forward = batch.create_unbuffered_network_output_func(model)
            >>> batch_size = model.batch_size
            >>> Xb, yb = data[0:batch_size * model.data_per_label], labels[0:batch_size]
            >>> network_output = theano_forward(Xb)[0]
            >>> network_output = network_output
            >>> Y = yb
            >>> T = np
            >>> # execute function
            >>> verbose = True
            >>> avg_loss = model.loss_function(network_output, Y, T=T)
            >>> result = str(avg_loss)
            >>> print(result)
        """
        if verbose:
            print('[model] Build SiameseCenterSurroundModel loss function')
        # make y_i in {-1, 1} where -1 denotes non-matching and +1 denotes matching
        Y_ = (1 - (2 * Y))
        # Hinge-loss objective from Zagoruyko and Komodakis
        loss = T.maximum(0, 1 - (Y_ * network_output.T))
        avg_loss = T.mean(loss)
        loss.name = 'loss'
        avg_loss.name = 'avg_loss'
        return avg_loss

    #def build_objective(self):
    #    pass

    #def build_regularized_objective(self, loss, output_layer, regularization):
    #    """ L2 weight decay """
    #    import warnings
    #    with warnings.catch_warnings():
    #        warnings.filterwarnings('ignore', '.*get_all_non_bias_params.*')
    #        L2 = lasagne.regularization.l2(output_layer)
    #        regularized_loss = L2 * regularization
    #        regularized_loss.name = 'regularized_loss'
    #    return regularized_loss

        #avg_loss = lasange_ext.siamese_loss(G, Y_padded, data_per_label=2)
        #return avg_loss


class SiameseModel(abstract_models.BaseModel):
    """
    Model for individual identification
    """
    def __init__(model):
        super(SiameseModel, model).__init__()
        model.network_layers = None
        # bad name, says that this network will take
        # 2*N images in a batch and N labels that map to
        # two images a piece
        model.data_per_label = 2
        model.needs_padding = True

    def build_model(model, batch_size, input_width, input_height,
                    input_channels, output_dims, verbose=True):
        r"""
        CommandLine:
            python -m ibeis_cnn.models --test-SiameseModel.build_model

        Example:
            >>> # DISABLE_DOCTEST
            >>> from ibeis_cnn.models import *  # NOQA
            >>> # build test data
            >>> model = SiameseModel()
            >>> batch_size = 128
            >>> input_width, input_height, input_channels = 64, 64, 3
            >>> output_dims = None
            >>> verbose = True
            >>> output_layer = model.build_model(batch_size, input_width, input_height, input_channels, output_dims, verbose)
            >>> print('----')
            >>> model.print_architecture_str(sep='\n')
            >>> print('hashid=%r' % (model.get_architecture_hashid()),)
            >>> print('----')
            >>> result = str(output_layer)
            >>> print(result)
        """
        input_shape = (None, input_channels, input_width, input_height)
        model.input_shape = input_shape
        model.batch_size = batch_size
        model.output_dims = 256
        model.initialize_architecture(verbose=verbose)
        output_layer = model.get_output_layer()
        return output_layer

    def initialize_architecture(model, verbose=True):
        # TODO: remove output dims
        _P = functools.partial
        (_, input_channels, input_width, input_height) = model.input_shape
        model.output_dims
        if verbose:
            print('[model] Initialize siamese model architecture')
            print('[model]   * batch_size     = %r' % (model.batch_size,))
            print('[model]   * input_width    = %r' % (input_width,))
            print('[model]   * input_height   = %r' % (input_height,))
            print('[model]   * input_channels = %r' % (input_channels,))
            print('[model]   * output_dims    = %r' % (model.output_dims,))

        leaky = dict(nonlinearity=nonlinearities.LeakyRectify(leakiness=(1. / 10.)))
        neuron_initkw = dict(W=init.Orthogonal(), **leaky)

        vggnet = abstract_models.PretrainedNetwork('vggnet')
        #caffenet = abstract_models.PretrainedNetwork('caffenet')

        network_layers_def = [
            _P(layers.InputLayer, shape=model.input_shape),

            vggnet.get_conv2d_layer(0, **leaky),

            _P(Conv2DLayer, num_filters=16, filter_size=(3, 3), **neuron_initkw),

            _P(MaxPool2DLayer, pool_size=(2, 2), stride=(2, 2)),
            _P(Conv2DLayer, num_filters=16, filter_size=(3, 3), **neuron_initkw),

            _P(MaxPool2DLayer, pool_size=(2, 2), stride=(2, 2)),

            _P(layers.DenseLayer, num_units=256, **neuron_initkw),
            _P(layers.DropoutLayer, p=0.5),
        ]

        # connect and record layers
        network_layers = abstract_models.evaluate_layer_list(network_layers_def)
        model.network_layers = network_layers
        output_layer = model.network_layers[-1]
        model.output_layer = output_layer
        return output_layer

    def loss_function(self, G, Y_padded, T=T, verbose=True):
        if verbose:
            print('[model] Build center surround siamese loss function')
        avg_loss = lasange_ext.siamese_loss(G, Y_padded, data_per_label=2)
        return avg_loss


class MNISTModel(abstract_models.AbstractCategoricalModel):
    """
    Toy model for testing and playing with mnist
    """
    def __init__(self):
        super(MNISTModel, self).__init__()

    def get_mnist_model_def1(model, input_shape, output_dims):
        _P = functools.partial
        leaky = dict(nonlinearity=nonlinearities.LeakyRectify(leakiness=(1. / 10.)))
        orthog = dict(W=init.Orthogonal())
        neuron_initkw = ut.merge_dicts(orthog, leaky)

        network_layers_def = (
            [
                _P(layers.InputLayer, shape=input_shape),
                layers.GaussianNoiseLayer,

                _P(Conv2DLayer, num_filters=32, filter_size=(5, 5), stride=(1, 1), name='C0', **neuron_initkw),
                _P(layers.DropoutLayer, p=0.1),
                _P(MaxPool2DLayer, pool_size=(2, 2), stride=(2, 2), name='P0'),

                _P(Conv2DLayer, num_filters=32, filter_size=(5, 5), stride=(1, 1), name='C1', **neuron_initkw),
                _P(MaxPool2DLayer, pool_size=(2, 2), stride=(2, 2), name='P1'),

                _P(layers.DenseLayer, num_units=256, name='F1',  **neuron_initkw),
                _P(layers.FeaturePoolLayer, pool_size=2),  # maxout
                _P(layers.DropoutLayer, p=0.5),

                _P(layers.DenseLayer, num_units=output_dims, nonlinearity=nonlinearities.softmax, W=init.Orthogonal())
            ]
        )
        return network_layers_def

    def get_mnist_model_def2(model, input_shape, output_dims):
        _P = functools.partial
        leaky = dict(nonlinearity=nonlinearities.LeakyRectify(leakiness=(1. / 10.)))
        orthog = dict(W=init.Orthogonal())
        neuron_initkw = ut.merge_dicts(orthog, leaky)
        initkw = neuron_initkw
        #initkw = {}

        # def to test failures
        network_layers_def = (
            [
                _P(layers.InputLayer, shape=input_shape),
                #layers.GaussianNoiseLayer,

                _P(Conv2DLayer, num_filters=16, filter_size=(7, 7), stride=(1, 1), name='C0', **initkw),
                _P(MaxPool2DLayer, pool_size=(3, 3), stride=(3, 3), name='P0'),
                _P(Conv2DLayer, num_filters=16, filter_size=(3, 3), stride=(1, 1), name='C1', **initkw),
                _P(Conv2DLayer, num_filters=16, filter_size=(3, 3), stride=(1, 1), name='C2', **initkw),
                _P(Conv2DLayer, num_filters=16, filter_size=(3, 3), stride=(1, 1), name='C3', **initkw),
                _P(Conv2DLayer, num_filters=16, filter_size=(2, 2), stride=(1, 1), name='C4', **initkw),
                _P(Conv2DLayer, num_filters=16, filter_size=(1, 1), stride=(1, 1), name='C5', **initkw),
                _P(Conv2DLayer, num_filters=16, filter_size=(2, 2), stride=(1, 1), name='C6', **initkw),

                _P(layers.DenseLayer, num_units=32, name='F1',  **initkw),
                _P(layers.DenseLayer, num_units=output_dims, nonlinearity=nonlinearities.softmax)
            ]
        )
        return network_layers_def

    def build_model(self, batch_size, input_width, input_height, input_channels, output_dims):
        input_shape = (None, input_channels, input_width, input_height)
        network_layers_def = self.get_mnist_model_def1(input_shape, output_dims)
        network_layers = abstract_models.evaluate_layer_list(network_layers_def)
        l_out = network_layers[-1]
        self.output_layer = l_out
        return l_out


class ViewpointModel(abstract_models.AbstractCategoricalModel):
    def __init__(self):
        super(ViewpointModel, self).__init__()

    def augment(self, Xb, yb=None):
        # Invert label function
        def _invert_label(label):
            label = label.replace('LEFT',  '^L^')
            label = label.replace('RIGHT', '^R^')
            label = label.replace('^R^', 'LEFT')
            label = label.replace('^L^', 'RIGHT')
            return(label)
        # Map
        points, channels, height, width = Xb.shape
        for index in range(points):
            if random.uniform(0.0, 1.0) <= 0.5:
                Xb[index] = Xb[index, :, ::-1]
                if yb is not None:
                    yb[index] = _invert_label(yb[index])
        return Xb, yb

    def label_order_mapping(self, category_list):
        if len(category_list) == 8:
            species_list = [
                'ZEBRA_PLAINS',
            ]
        else:
            species_list = [
                'ZEBRA_PLAINS',
                'ZEBRA_GREVYS',
                'ELEPHANT_SAVANNA',
                'GIRAFFE_RETICULATED',
                'GIRAFFE_MASAI',
            ]
        viewpoint_mapping = {
            'LEFT':        0,
            'FRONT_LEFT':  1,
            'FRONT':       2,
            'FRONT_RIGHT': 3,
            'RIGHT':       4,
            'BACK_RIGHT':  5,
            'BACK':        6,
            'BACK_LEFT':   7,
        }
        viewpoints = len(viewpoint_mapping.keys())
        category_mapping = {}
        for index, species in enumerate(species_list):
            for viewpoint, value in viewpoint_mapping.iteritems():
                key = '%s:%s' % (species, viewpoint, )
                base = viewpoints * index
                category_mapping[key] = base + value
        return category_mapping

    def learning_rate_update(self, x):
        return x / 2.0

    def learning_rate_shock(self, x):
        return x * 2.0

    def build_model(self, batch_size, input_width, input_height, input_channels, output_dims):
        _CaffeNet = abstract_models.PretrainedNetwork('caffenet')

        l_in = layers.InputLayer(
            # variable batch size (None), channel, width, height
            shape=(None, input_channels, input_width, input_height)
        )

        l_noise = layers.GaussianNoiseLayer(
            l_in,
        )

        l_conv0 = Conv2DLayer(
            l_noise,
            num_filters=32,
            filter_size=(11, 11),
            # nonlinearity=nonlinearities.rectify,
            nonlinearity=nonlinearities.LeakyRectify(leakiness=(1. / 10.)),
            W=_CaffeNet.get_pretrained_layer(0),
        )

        l_conv0_dropout = layers.DropoutLayer(l_conv0, p=0.10)

        l_conv1 = Conv2DLayer(
            l_conv0_dropout,
            num_filters=32,
            filter_size=(5, 5),
            # nonlinearity=nonlinearities.rectify,
            nonlinearity=nonlinearities.LeakyRectify(leakiness=(1. / 10.)),
            W=_CaffeNet.get_pretrained_layer(2),
        )

        l_pool1 = MaxPool2DLayer(
            l_conv1,
            pool_size=(2, 2),
            stride=(2, 2),
        )

        l_conv2_dropout = layers.DropoutLayer(l_pool1, p=0.10)

        l_conv2 = Conv2DLayer(
            l_conv2_dropout,
            num_filters=64,
            filter_size=(3, 3),
            # nonlinearity=nonlinearities.rectify,
            nonlinearity=nonlinearities.LeakyRectify(leakiness=(1. / 10.)),
            W=init.Orthogonal(),
        )

        l_pool2 = MaxPool2DLayer(
            l_conv2,
            pool_size=(2, 2),
            stride=(2, 2),
        )

        l_conv3_dropout = layers.DropoutLayer(l_pool2, p=0.30)

        l_conv3 = Conv2DLayer(
            l_conv3_dropout,
            num_filters=128,
            filter_size=(3, 3),
            # nonlinearity=nonlinearities.rectify,
            nonlinearity=nonlinearities.LeakyRectify(leakiness=(1. / 10.)),
            W=init.Orthogonal(),
        )

        l_pool3 = MaxPool2DLayer(
            l_conv3,
            pool_size=(2, 2),
            stride=(2, 2),
        )

        l_conv4_dropout = layers.DropoutLayer(l_pool3, p=0.30)

        l_conv4 = Conv2DLayer(
            l_conv4_dropout,
            num_filters=128,
            filter_size=(3, 3),
            # nonlinearity=nonlinearities.rectify,
            nonlinearity=nonlinearities.LeakyRectify(leakiness=(1. / 10.)),
            W=init.Orthogonal(),
        )

        l_pool4 = MaxPool2DLayer(
            l_conv4,
            pool_size=(2, 2),
            stride=(2, 2),
        )

        l_hidden1 = layers.DenseLayer(
            l_pool4,
            num_units=512,
            # nonlinearity=nonlinearities.rectify,
            nonlinearity=nonlinearities.LeakyRectify(leakiness=(1. / 10.)),
            W=init.Orthogonal(),
        )

        l_hidden1_maxout = layers.FeaturePoolLayer(
            l_hidden1,
            pool_size=2,
        )

        l_hidden1_dropout = layers.DropoutLayer(l_hidden1_maxout, p=0.5)

        l_hidden2 = layers.DenseLayer(
            l_hidden1_dropout,
            num_units=512,
            # nonlinearity=nonlinearities.rectify,
            nonlinearity=nonlinearities.LeakyRectify(leakiness=(1. / 10.)),
            W=init.Orthogonal(),
        )

        l_hidden2_maxout = layers.FeaturePoolLayer(
            l_hidden2,
            pool_size=2,
        )

        l_hidden2_dropout = layers.DropoutLayer(l_hidden2_maxout, p=0.5)

        l_out = layers.DenseLayer(
            l_hidden2_dropout,
            num_units=output_dims,
            nonlinearity=nonlinearities.softmax,
            W=init.Orthogonal(),
        )

        self.output_layer = l_out
        return l_out


class QualityModel(abstract_models.AbstractCategoricalModel):
    def __init__(self):
        super(QualityModel, self).__init__()

    def label_order_mapping(self, category_list):
        quality_mapping = {
            'JUNK':      0,
            'POOR':      1,
            'GOOD':      2,
            'OK':        3,
            'EXCELLENT': 4,
        }
        return quality_mapping

    def learning_rate_update(self, x):
        return x / 2.0

    def learning_rate_shock(self, x):
        return x * 2.0

    def build_model(self, batch_size, input_width, input_height, input_channels, output_dims):
        _CaffeNet = abstract_models.PretrainedNetwork('caffenet')

        l_in = layers.InputLayer(
            # variable batch size (None), channel, width, height
            shape=(None, input_channels, input_width, input_height)
        )

        l_noise = layers.GaussianNoiseLayer(
            l_in,
        )

        l_conv0 = Conv2DLayer(
            l_noise,
            num_filters=32,
            filter_size=(11, 11),
            # nonlinearity=nonlinearities.rectify,
            nonlinearity=nonlinearities.LeakyRectify(leakiness=(1. / 10.)),
            W=_CaffeNet.get_pretrained_layer(0),
        )

        l_conv0_dropout = layers.DropoutLayer(l_conv0, p=0.10)

        l_conv1 = Conv2DLayer(
            l_conv0_dropout,
            num_filters=32,
            filter_size=(5, 5),
            # nonlinearity=nonlinearities.rectify,
            nonlinearity=nonlinearities.LeakyRectify(leakiness=(1. / 10.)),
            W=_CaffeNet.get_pretrained_layer(2),
        )

        l_pool1 = MaxPool2DLayer(
            l_conv1,
            pool_size=(2, 2),
            stride=(2, 2),
        )

        l_conv2_dropout = layers.DropoutLayer(l_pool1, p=0.10)

        l_conv2 = Conv2DLayer(
            l_conv2_dropout,
            num_filters=64,
            filter_size=(3, 3),
            # nonlinearity=nonlinearities.rectify,
            nonlinearity=nonlinearities.LeakyRectify(leakiness=(1. / 10.)),
            W=init.Orthogonal(),
        )

        l_pool2 = MaxPool2DLayer(
            l_conv2,
            pool_size=(2, 2),
            stride=(2, 2),
        )

        l_conv3_dropout = layers.DropoutLayer(l_pool2, p=0.30)

        l_conv3 = Conv2DLayer(
            l_conv3_dropout,
            num_filters=128,
            filter_size=(3, 3),
            # nonlinearity=nonlinearities.rectify,
            nonlinearity=nonlinearities.LeakyRectify(leakiness=(1. / 10.)),
            W=init.Orthogonal(),
        )

        l_pool3 = MaxPool2DLayer(
            l_conv3,
            pool_size=(2, 2),
            stride=(2, 2),
        )

        l_conv4_dropout = layers.DropoutLayer(l_pool3, p=0.30)

        l_conv4 = Conv2DLayer(
            l_conv4_dropout,
            num_filters=128,
            filter_size=(3, 3),
            # nonlinearity=nonlinearities.rectify,
            nonlinearity=nonlinearities.LeakyRectify(leakiness=(1. / 10.)),
            W=init.Orthogonal(),
        )

        l_pool4 = MaxPool2DLayer(
            l_conv4,
            pool_size=(2, 2),
            stride=(2, 2),
        )

        l_hidden1 = layers.DenseLayer(
            l_pool4,
            num_units=512,
            # nonlinearity=nonlinearities.rectify,
            nonlinearity=nonlinearities.LeakyRectify(leakiness=(1. / 10.)),
            W=init.Orthogonal(),
        )

        l_hidden1_maxout = layers.FeaturePoolLayer(
            l_hidden1,
            pool_size=2,
        )

        l_hidden1_dropout = layers.DropoutLayer(l_hidden1_maxout, p=0.5)

        l_hidden2 = layers.DenseLayer(
            l_hidden1_dropout,
            num_units=512,
            # nonlinearity=nonlinearities.rectify,
            nonlinearity=nonlinearities.LeakyRectify(leakiness=(1. / 10.)),
            W=init.Orthogonal(),
        )

        l_hidden2_maxout = layers.FeaturePoolLayer(
            l_hidden2,
            pool_size=2,
        )

        l_hidden2_dropout = layers.DropoutLayer(l_hidden2_maxout, p=0.5)

        l_out = layers.DenseLayer(
            l_hidden2_dropout,
            num_units=output_dims,
            nonlinearity=nonlinearities.softmax,
            W=init.Orthogonal(),
        )
        self.output_layer = l_out
        return l_out


if __name__ == '__main__':
    """
    CommandLine:
        python -m ibeis_cnn.models
        python -m ibeis_cnn.models --allexamples
        python -m ibeis_cnn.models --allexamples --noface --nosrc
    """
    import multiprocessing
    multiprocessing.freeze_support()  # for win32
    import utool as ut  # NOQA
    ut.doctest_funcs()
