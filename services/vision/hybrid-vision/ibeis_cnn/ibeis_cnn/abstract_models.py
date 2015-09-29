# -*- coding: utf-8 -*-
from __future__ import absolute_import, division, print_function
import lasagne  # NOQA
from lasagne import init
import functools
import six
import theano.tensor as T
import numpy as np
#from lasagne import layers
from ibeis_cnn import net_strs
from ibeis_cnn import utils
from ibeis_cnn import custom_layers
import sklearn.preprocessing
import utool as ut
import warnings
ut.noinject('ibeis_cnn.abstract_models')


Conv2DLayer = custom_layers.Conv2DLayer
MaxPool2DLayer = custom_layers.MaxPool2DLayer


def evaluate_layer_list(network_layers_def, verbose=utils.VERBOSE_CNN):
    """ compiles a sequence of partial functions into a network """
    network_layers = []
    layer_fn_iter = iter(network_layers_def)
    with warnings.catch_warnings():
        warnings.filterwarnings(
            'ignore', '.*The uniform initializer no longer uses Glorot.*')
        try:
            count = 0
            if verbose:
                tt = ut.Timer(verbose=False)
                print('Evaluating layer %d' % (count,))
                tt.tic()
            prev_layer = six.next(layer_fn_iter)()
            network_layers.append(prev_layer)
            for count, layer_fn in enumerate(layer_fn_iter, start=1):
                if verbose:
                    print('  * took %.4s' % (tt.toc(),))
                    print('Evaluating layer %d' % (count,))
                    print('  * prev_layer = %r' % (prev_layer,))
                    tt.tic()
                prev_layer = layer_fn(prev_layer)
                network_layers.append(prev_layer)
        except Exception as ex:
            ut.printex(
                ex,
                ('Error buildling layers.\n'
                 'prev_layer.name=%r\n'
                 'count=%r') % (prev_layer, count))
            raise
    return network_layers


class _PretrainedLayerInitializer(init.Initializer):
    def __init__(self, pretrained_layer):
        self.pretrained_layer = pretrained_layer

    def sample(self, shape):
        fanout, fanin, height, width = shape
        fanout_, fanin_, height_, width_ = self.pretrained_layer.shape
        assert fanout <= fanout_, 'Cannot cast weights to a larger fan-out dimension'
        assert fanin  <= fanin_,  'Cannot cast weights to a larger fan-in dimension'
        assert height == height_, 'The height must be identical between the layer and weights'
        assert width  == width_,  'The width must be identical between the layer and weights'
        return lasagne.utils.floatX(self.pretrained_layer[:fanout, :fanin, :, :])


class PretrainedNetwork(object):
    """
    Intialize weights from a specified (Caffe) pretrained network layers

    Args:
        layer (int) : int

    CommandLine:
        python -m ibeis_cnn.models --test-PretrainedNetwork:0
        python -m ibeis_cnn.models --test-PretrainedNetwork:1

    Example0:
        >>> # DISABLE_DOCTEST
        >>> from ibeis_cnn.models import *  # NOQA
        >>> self = PretrainedNetwork('caffenet', show_network=True)
        >>> print('done')

    Example1:
        >>> # DISABLE_DOCTEST
        >>> from ibeis_cnn.models import *  # NOQA
        >>> self = PretrainedNetwork('vggnet', show_network=True)
        >>> print('done')
    """
    def __init__(self, modelkey=None, show_network=False):
        from ibeis_cnn._plugin_grabmodels import ensure_model
        self.modelkey = modelkey
        weights_path = ensure_model(modelkey)
        try:
            self.pretrained_weights = ut.load_cPkl(weights_path)
        except Exception:
            raise IOError('The specified model was not found: %r' %
                          (weights_path, ))
        if show_network:
            net_strs.print_pretrained_weights(
                self.pretrained_weights, weights_path)

    def get_num_layers(self):
        return len(self.pretrained_weights)

    def get_layer_num_filters(self, layer_index):
        assert layer_index <= len(self.pretrained_weights), (
            'Trying to specify a layer that does not exist')
        shape = self.pretrained_weights[layer_index].shape
        fanout, fanin, height, width = shape
        return fanout

    def get_layer_filter_size(self, layer_index):
        assert layer_index <= len(self.pretrained_weights), (
            'Trying to specify a layer that does not exist')
        shape = self.pretrained_weights[layer_index].shape
        fanout, fanin, height, width = shape
        return (height, width)

    def get_conv2d_layer(self, layer_index, name=None, **kwargs):
        """ Assumes requested layer is convolutional

        Returns:
            lasange.layers.Layer: Layer
        """
        if name is None:
            name = '%s_layer%r' % (self.modelkey, layer_index)
        W = self.get_pretrained_layer(layer_index)
        num_filters = self.get_layer_num_filters(layer_index)
        filter_size = self.get_layer_filter_size(layer_index)
        Layer = functools.partial(
            Conv2DLayer, num_filters=num_filters,
            filter_size=filter_size, W=W, name=name, **kwargs)
        return Layer

    def get_pretrained_layer(self, layer_index, rand=False):
        assert layer_index <= len(self.pretrained_weights), (
            'Trying to specify a layer that does not exist')
        pretrained_layer = self.pretrained_weights[layer_index]
        weights_initializer = _PretrainedLayerInitializer(pretrained_layer)
        if rand:
            np.random.shuffle(weights_initializer)
        return weights_initializer


class BaseModel(object):
    """
    Abstract model providing functionality for all other models to derive from
    """
    def __init__(self):
        self.network_layers = None
        self.output_layer = None
        self.output_dims = None
        # bad name, says that this network will take
        # 2*N images in a batch and N labels that map to
        # two images a piece
        self.data_per_label = 1
        self.net_dir = '.'  # TODO

    # --- initialization steps

    def initialize_architecture(self):
        raise NotImplementedError('reimlement')

    # --- utility

    def draw_architecture(self):
        from ibeis_cnn import draw_net
        filename = 'tmp.png'
        draw_net.draw_to_file(self.network_layers, filename)
        ut.startfile(filename)

    def get_architecture_hashid(self):
        architecture_str = self.get_architecture_str()
        hashid = ut.hashstr(architecture_str, alphabet=ut.ALPHABET_16, hashlen=16)
        return hashid

    def print_layer_info(self):
        net_strs.print_layer_info(self.get_output_layer())

    def get_architecture_str(self, sep='_'):
        # TODO: allow for removal of layers without any parameters
        if getattr(self, 'network_layers', None) is None:
            if getattr(self, 'output_layer', None) is None:
                return None
            else:
                network_layers = lasagne.layers.get_all_layers(self.output_layer)
        else:
            network_layers = self.network_layers
        layer_str_list = [net_strs.make_layer_str(layer) for layer in network_layers]
        architecture_str = sep.join(layer_str_list)
        return architecture_str

    def print_architecture_str(self, sep='\n  '):
        architecture_str = self.get_architecture_str(sep=sep)
        if architecture_str is None:
            architecture_str = 'UNMANGAGED'
        print('\nArchitecture:' + sep + architecture_str)

    def get_output_layer(self):
        if self.output_layer is not None:
            return self.output_layer
        else:
            assert self.network_layers is not None, 'need to initialize architecture first'
            output_layer = self.network_layers[-1]
            return output_layer

    def learning_rate_update(model, x):
        return x / 2.0

    def learning_rate_shock(model, x):
        return x * 2.0


class AbstractCategoricalModel(BaseModel):
    """ base model for catagory classifiers """

    def __init__(self):
        super(AbstractCategoricalModel, self).__init__()
        self.encoder = None

    def initialize_encoder(self, labels):
        print('[model] encoding labels')
        self.encoder = sklearn.preprocessing.LabelEncoder()
        self.encoder.fit(labels)
        self.output_dims = len(list(np.unique(labels)))
        print('[model] self.output_dims = %r' % (self.output_dims,))

    def loss_function(model, output, truth):
        return T.nnet.categorical_crossentropy(output, truth)
