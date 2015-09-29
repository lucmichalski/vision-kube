# -*- coding: utf-8 -*-
from __future__ import absolute_import, division, print_function
import warnings
import functools
import utool as ut
print, rrr, profile = ut.inject2(__name__, '[ibeis_cnn.net_strs]')


def make_layer_str(layer):
    r"""
    Args:
        layer (lasange.Layer): a network layer
    """
    # filter_size is a scalar in Conv2DLayers because non-uniform shapes is not supported
    common_attrs = ['shape', 'num_filters', 'num_units', 'ds', 'filter_shape', 'stride', 'strides', 'p', 'axis']
    ignore_attrs = ['nonlinearity', 'b', 'W']
    #func_attrs = ['get_output_shape']
    func_attrs = []
    layer_attrs_dict = {
        'DropoutLayer': ['p'],
        'InputLayer': ['shape'],
        'Conv2DCCLayer': ['num_filters', 'filter_size', 'stride'],
        'MaxPool2DCCLayer': ['stride', 'pool_size'],
        'DenseLayer': ['num_units'],
        'L2NormalizeLayer': ['axis'],
    }
    layer_type = '{0}'.format(layer.__class__.__name__)
    request_attrs = sorted(list(set(layer_attrs_dict.get(layer_type, []) + common_attrs)))
    isvalid_list = [hasattr(layer, attr) for attr in request_attrs]
    attr_key_list = ut.list_compress(request_attrs, isvalid_list)

    DEBUG = False
    if DEBUG:
        #if layer_type == 'Conv2DCCLayer':
        #    ut.embed()
        print('---')
        print(' * ' + layer_type)
        print(' * does not have keys: %r' % (ut.filterfalse_items(request_attrs, isvalid_list),))
        print(' * missing keys: %r' % ((set(layer.__dict__.keys()) - set(ignore_attrs)) - set(attr_key_list),))
        print(' * has keys: %r' % (attr_key_list,))

    attr_val_list = [getattr(layer, attr) for attr in attr_key_list]
    attr_str_list = ['%s=%r' % item for item in zip(attr_key_list, attr_val_list)]

    for func_attr in func_attrs:
        if hasattr(layer, func_attr):
            attr_str_list.append('%s=%r' % (func_attr, getattr(layer, func_attr).__call__()))

    layer_name = getattr(layer, 'name', None)
    if layer_name is not None:
        attr_str_list = ['name=%s' % (layer_name,)] + attr_str_list

    if hasattr(layer, 'nonlinearity'):
        try:
            nonlinearity = layer.nonlinearity.__name__
        except AttributeError:
            nonlinearity = layer.nonlinearity.__class__.__name__
        attr_str_list.append('nonlinearity={0}'.format(nonlinearity))
    attr_str = ','.join(attr_str_list)

    layer_str = layer_type + '(' + attr_str + ')'
    return layer_str


def print_pretrained_weights(pretrained_weights, lbl=''):
    r"""
    Args:
        pretrained_weights (list of ndarrays): represents layer weights
        lbl (str): label
    """
    print('Initialization network: %r' % (lbl))
    print('Total memory: %s' % (ut.get_object_size_str(pretrained_weights)))
    for index, layer_ in enumerate(pretrained_weights):
        print(' layer {:2}: shape={:<18}, memory={}'.format(index, layer_.shape, ut.get_object_size_str(layer_)))


def print_layer_info(output_layer):
    import operator
    import lasagne
    with warnings.catch_warnings():
        warnings.filterwarnings('ignore', '.*topo.*')
        nn_layers = lasagne.layers.get_all_layers(output_layer)
        print('\n[info] Network Structure:')

        name_column = [layer.__class__.__name__ for layer in nn_layers]
        max_namecol_len = max(list(map(len, name_column)))
        fmtstr = '[info]  {:>3}  {:<' + str(max_namecol_len) + '}   {:<20}   produces {:>7,} outputs'

        for index, layer in enumerate(nn_layers):
            #print([p.get_value().shape for p in layer.get_params()])
            output_shape = lasagne.layers.get_output_shape(layer)  # .get_output_shape()
            num_outputs = functools.reduce(operator.mul, output_shape[1:])
            str_ = fmtstr.format(
                index,
                layer.__class__.__name__,
                str(output_shape),
                int(num_outputs),
            )
            print(str_)
            #ut.embed()
        print('[info] ...this model has {:,} learnable parameters\n'.format(
            lasagne.layers.count_params(output_layer)
        ))
