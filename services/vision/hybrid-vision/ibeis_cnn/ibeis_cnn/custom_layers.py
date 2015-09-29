from __future__ import absolute_import, division, print_function
import numpy as np
import lasagne
import utool as ut
import warnings
import theano
import theano.tensor as T
import functools
#import six
from ibeis_cnn import utils
ut.noinject('custom_layers')


FORCE_CPU = False  # ut.get_argflag('--force-cpu')
try:
    if FORCE_CPU:
        raise ImportError('GPU is forced off')
    # use cuda_convnet for a speed improvement
    # will not be available without a GPU
    import lasagne.layers.cuda_convnet as convnet
    Conv2DLayer = convnet.Conv2DCCLayer
    MaxPool2DLayer = convnet.MaxPool2DCCLayer
    USING_GPU = True
except ImportError as ex:
    ut.printex(ex, 'WARNING: GPU seems unavailable', iswarning=True)
    Conv2DLayer = lasagne.layers.Conv2DLayer
    MaxPool2DLayer = lasagne.layers.MaxPool2DLayer
    USING_GPU = False


class L1NormalizeLayer(lasagne.layers.Layer):
    def __init__(self, input_layer, *args, **kwargs):
        super(L1NormalizeLayer, self).__init__(input_layer, *args, **kwargs)

    def get_output_for(self, input_, *args, **kwargs):
        ell1_norm = T.abs_(input_).sum(axis=1)
        output_ = input_ / ell1_norm[:, None]
        return output_


#@six.add_metaclass(ut.ReloadingMetaclass)
class L2NormalizeLayer(lasagne.layers.Layer):
    def __init__(self, input_layer, axis=1, **kwargs):
        super(L2NormalizeLayer, self).__init__(input_layer, **kwargs)
        self.axis = axis

    def get_output_for(self, input_, axis=None, T=T, **kwargs):
        """
        CommandLine:
            python -m ibeis_cnn.custom_layers --test-L2NormalizeLayer.get_output_for

        Example0:
            >>> # ENABLE_DOCTEST
            >>> from ibeis_cnn.custom_layers import *  # NOQA
            >>> # l2 normalization on batches of vector encodings
            >>> input_layer = testdata_input_layer(item_shape=(8,), batch_size=4)
            >>> inputdata_ = np.random.rand(*input_layer.shape).astype(np.float32)
            >>> axis = 1
            >>> self = L2NormalizeLayer(input_layer, axis=axis)
            >>> # Test numpy version
            >>> T = np
            >>> input_ = inputdata_
            >>> output_np = self.get_output_for(inputdata_, T=np)
            >>> assert np.all(np.isclose(np.linalg.norm(output_np, axis=axis), 1.0))
            >>> # Test theano version
            >>> T = theano.tensor
            >>> input_expr = input_ = T.matrix(name='vector_input')
            >>> output_expr = self.get_output_for(input_expr, T=T)
            >>> output_T = output_expr.eval({input_expr: inputdata_})
            >>> print(output_T)
            >>> assert np.all(np.isclose(output_T, output_np)), 'theano and numpy diagree'

        Example1:
            >>> # ENABLE_DOCTEST
            >>> from ibeis_cnn.custom_layers import *  # NOQA
            >>> # l2 normalization on batches of image filters
            >>> input_layer = testdata_input_layer(item_shape=(3, 2, 2), batch_size=4)
            >>> inputdata_ = np.random.rand(*input_layer.shape).astype(np.float32)
            >>> axis = 2
            >>> self = L2NormalizeLayer(input_layer, axis=axis)
            >>> # Test numpy version
            >>> T = np
            >>> input_ = inputdata_
            >>> output_np = self.get_output_for(inputdata_, T=np)
            >>> output_flat_np = output_np.reshape(np.prod(input_layer.shape[0:2]), np.prod(input_layer.shape[2:4]))
            >>> assert np.all(np.isclose(np.linalg.norm(output_flat_np, axis=1), 1.0))
            >>> # Test theano version
            >>> T = theano.tensor
            >>> input_expr = input_ = T.tensor4(name='image_filter_input')
            >>> output_expr = self.get_output_for(input_expr, T=T)
            >>> output_T = output_expr.eval({input_expr: inputdata_})
            >>> print(output_T)
            >>> assert np.all(np.isclose(output_T, output_np)), 'theano and numpy diagree'
            >>> #output_T = utils.evaluate_symbolic_layer(self.get_output_for, inputdata_, T.tensor4, T=theano.tensor)
        """
        if axis is None:
            axis = self.axis

        input_shape = input_.shape
        batch_shape = input_shape[0:axis]
        rest_shape = input_shape[axis:]
        batch_size = T.prod(batch_shape)
        rest_size = T.prod(rest_shape)

        # reshape to two dimensions
        input_reshaped_ = input_.reshape((batch_size, rest_size))
        #if T is np:
        #    #input_reshaped_ = input_.reshape(batch_shape + (rest_size,))
        #else:
        #    # hack because I don't know how to get ndim yet
        #    if axis == 1:
        #        input_reshaped_ = input_.reshape(batch_shape + (rest_size,), ndim=2)
        #    elif axis == 2:
        #        input_reshaped_ = input_.reshape(batch_shape + (rest_size,), ndim=3)

        ell2_norm = T.sqrt(T.power(input_reshaped_, 2).sum(axis=-1))
        if T is np:
            #outputreshaped_ = input_reshaped_ / ell2_norm[..., None]
            outputreshaped_ = input_reshaped_ / ell2_norm[:, None]
            output_ = outputreshaped_.reshape(input_shape)
        else:
            outputreshaped_ = input_reshaped_ / ell2_norm[:, None]
            output_ = outputreshaped_.reshape(input_shape)
            output_.name = 'l2normalized(%s)' % (input_.name)
            #.dimshuffle(0, 'x', 1)
        return output_


class L2SquaredDistanceLayer(lasagne.layers.Layer):
    def __init__(self, input_layer, *args, **kwargs):
        super(L2SquaredDistanceLayer, self).__init__(input_layer, *args, **kwargs)

    def get_output_shape_for(self, input_shape):
        return (input_shape[0] // 2,) + input_shape[1:]

    def get_output_for(self, input_, *args, **kwargs):
        # Split batch into pairs
        G1, G2 = input_[0::2], input_[1::2]
        E = T.power((G1 - G2), 2).sum(axis=1)
        return E


class L1DistanceLayer(lasagne.layers.Layer):
    def __init__(self, input_layer, *args, **kwargs):
        super(L1DistanceLayer, self).__init__(input_layer, *args, **kwargs)

    def get_output_shape_for(self, input_shape):
        return (input_shape[0] // 2,) + input_shape[1:]

    def get_output_for(self, input_, *args, **kwargs):
        # Split batch into pairs
        G1, G2 = input_[0::2], input_[1::2]
        E = T.abs_((G1 - G2)).sum(axis=1)
        return E


def testdata_input_layer(item_shape=(3, 32, 32), batch_size=128):
    input_shape = (batch_size,) + item_shape
    input_layer = lasagne.layers.InputLayer(shape=input_shape)
    return input_layer


class SiameseConcatLayer(lasagne.layers.Layer):
    """

    TODO checkout layers.merge.ConcatLayer

    Takes two network representations in the batch and combines them along an axis.
    """
    def __init__(self, input_layer, data_per_label=2, axis=1, **kwargs):
        super(SiameseConcatLayer, self).__init__(input_layer, **kwargs)
        self.data_per_label = data_per_label
        self.axis = axis

    def get_output_shape_for(self, input_shape, axis=None):
        r"""

        Args:
            input_shape: shape being fed into this layer
            axis: overrideable for tests

        CommandLine:
            python -m ibeis_cnn.custom_layers --test-get_output_shape_for

        Example0:
            >>> # ENABLE_DOCTEST
            >>> from ibeis_cnn.custom_layers import *  # NOQA
            >>> input_layer = testdata_input_layer(item_shape=(3, 8, 16))
            >>> self = SiameseConcatLayer(input_layer)
            >>> input_shape = input_layer.shape
            >>> output_shape_list = [self.get_output_shape_for(input_shape, axis) for axis in [1, 2, 3, -3, -2, -1]]
            >>> result = str(output_shape_list[0:3]) + '\n' + str(output_shape_list[3:])
            >>> print(result)
            [(64, 6, 8, 16), (64, 3, 16, 16), (64, 3, 8, 32)]
            [(64, 6, 8, 16), (64, 3, 16, 16), (64, 3, 8, 32)]

        Example1:
            >>> # ENABLE_DOCTEST
            >>> from ibeis_cnn.custom_layers import *  # NOQA
            >>> input_layer = testdata_input_layer(item_shape=(1024,))
            >>> self = SiameseConcatLayer(input_layer)
            >>> input_shape = input_layer.shape
            >>> output_shape_list = [self.get_output_shape_for(input_shape, axis) for axis in [1, -1]]
            >>> result = output_shape_list
            >>> print(result)
            [(64, 2048), (64, 2048)]
        """
        if axis is None:
            # allow override for tests
            axis = self.axis
        assert self.axis != 0, 'self.axis=%r cannot be 0' % (self.axis,)
        new_batch_shape = (input_shape[0] // self.data_per_label,)
        new_shape_middle = (input_shape[axis]  * self.data_per_label,)
        if axis >= 0:
            shape_front = input_shape[1:axis]
            shape_end = input_shape[axis + 1:]
        else:
            shape_front = input_shape[1:axis]
            shape_end = input_shape[len(input_shape) + axis + 1:]
        output_shape = new_batch_shape + shape_front + new_shape_middle + shape_end
        return output_shape

    def get_output_for(self, input_, T=T, **kwargs):
        """
        CommandLine:
            python -m ibeis_cnn.custom_layers --test-SiameseConcatLayer.get_output_for:1 --show

        Example0:
            >>> # ENABLE_DOCTEST
            >>> from ibeis_cnn.custom_layers import *  # NOQA
            >>> #item_shape = (3, 32, 32,)
            >>> input_shape = (128,) + item_shape
            >>> input_layer = lasagne.layers.InputLayer(shape=input_shape)
            >>> self = SiameseConcatLayer(input_layer)
            >>> input_ = np.random.rand(128, *item_shape)
            >>> T = np
            >>> output_ = self.get_output_for(input_, T=T)
            >>> target_shape = self.get_output_shape_for(input_shape)
            >>> result = output_.shape
            >>> print(result)
            >>> assert target_shape == result
            (64, 2048)

        Example1:
            >>> # DISABLE_DOCTEST
            >>> from ibeis_cnn.custom_layers import *  # NOQA
            >>> from ibeis_cnn.custom_layers import *  # NOQA
            >>> from ibeis_cnn import utils
            >>> from ibeis_cnn import draw_net
            >>> import theano
            >>> import numpy as np
            >>> input_layer = lasagne.layers.InputLayer(shape=(4, 3, 32, 32))
            >>> cs_layer = CenterSurroundLayer(input_layer)
            >>> # Make sure that this can concat center surround properly
            >>> self = SiameseConcatLayer(cs_layer, axis=2, data_per_label=4)
            >>> data = utils.testdata_imglist()[0]
            >>> inputdata_ = utils.convert_cv2_images_to_theano_images(data)
            >>> outputdata_ = cs_layer.get_output_for(inputdata_, T=np)
            >>> input_ = outputdata_
            >>> output_ = self.get_output_for(input_, T=np)
            >>> ut.quit_if_noshow()
            >>> img_list = utils.convert_theano_images_to_cv2_images(output_)
            >>> interact_image_list(img_list, num_per_page=2)

        """
        data_per_label = self.data_per_label
        split_inputs = [input_[count::data_per_label] for count in range(data_per_label)]
        output_ =  T.concatenate(split_inputs, axis=self.axis)
        #input1, input2 = input_[0::2], input_[1::2]
        #output_ =  T.concatenate([input1, input2], axis=1)
        return output_


def interact_image_list(img_list, num_per_page=1):
    #from ibeis.viz import viz_helpers as vh
    import plottool as pt

    nRows, nCols = pt.get_square_row_cols(num_per_page)
    chunked_iter = list(ut.ichunks(img_list, num_per_page))
    for img_chunks in ut.InteractiveIter(chunked_iter, display_item=False):
        pnum_ = pt.make_pnum_nextgen(nRows, nCols)
        pt.figure(fnum=1, doclf=True)
        for img in img_chunks:
            pt.imshow(img, pnum=pnum_())
        #pt.draw_border(pt.gca(), color=vh.get_truth_color(label))
        #pt.imshow(patch2, pnum=(1, 2, 2))
        #pt.draw_border(pt.gca(), color=vh.get_truth_color(label))
        pt.update()


def testdata_centersurround(item_shape):
    input_layer = testdata_input_layer(item_shape)
    data = utils.testdata_imglist(item_shape)[0]
    self = CenterSurroundLayer(input_layer)
    inputdata_ = utils.convert_cv2_images_to_theano_images(data)
    return self, inputdata_


class CenterSurroundLayer(lasagne.layers.Layer):
    def __init__(self, input_layer, *args, **kwargs):
        super(CenterSurroundLayer, self).__init__(input_layer, *args, **kwargs)

    def get_output_shape_for(self, input_shape):
        batch_size, channels, height, width = input_shape
        if height % 2 == 1 or width % 2 == 1:
            warnings.warn("input layer to CenterSurroundLayer should ideally have an even width and height.")
        output_shape = (batch_size * 2, channels, height // 2, width // 2)
        return output_shape

    def get_output_for(self, input_expr, T=T, **kwargs):
        r"""
        CommandLine:
            python -m ibeis_cnn.custom_layers --test-CenterSurroundLayer.get_output_for:0 --show
            python -m ibeis_cnn.custom_layers --test-CenterSurroundLayer.get_output_for:1 --show

        Example0:
            >>> # DISABLE_DOCTEST
            >>> from ibeis_cnn.custom_layers import *  # NOQA
            >>> import theano
            >>> #item_shape = (32, 32, 3)
            >>> item_shape = (41, 41, 3)
            >>> self, inputdata_ = testdata_centersurround(item_shape)
            >>> # Test the actual symbolic expression
            >>> output_T = utils.evaluate_symbolic_layer(self.get_output_for, inputdata_, T.tensor4, T=theano.tensor)
            >>> output_T = output_T.astype(np.uint8)
            >>> ut.quit_if_noshow()
            >>> img_list = utils.convert_theano_images_to_cv2_images(output_T)
            >>> interact_image_list(img_list, num_per_page=8)

        Example1:
            >>> # DISABLE_DOCTEST
            >>> from ibeis_cnn.custom_layers import *  # NOQA
            >>> import numpy as np
            >>> #item_shape = (32, 32, 3)
            >>> item_shape = (41, 41, 3)
            >>> self, input_expr = testdata_centersurround(item_shape)
            >>> # Test using just numpy
            >>> output_np = self.get_output_for(input_expr, T=np)
            >>> print('results agree')
            >>> ut.quit_if_noshow()
            >>> img_list = utils.convert_theano_images_to_cv2_images(output_np)
            >>> interact_image_list(img_list, num_per_page=8)

        Ignore:
            from ibeis_cnn import draw_net
            #draw_net.draw_theano_symbolic_expression(result)
            assert np.all(output_np == output_T)
            np.stack = np.vstack
            T = np
        """
        # Create a center and surround for each input patch
        #return input_
        input_shape = input_expr.shape
        batch_size, channels, height, width = input_shape

        left_h = height // 4
        left_w = width // 4
        right_h = left_h * 3
        right_w = left_w * 3
        # account for odd patches
        total_h = left_h * 4
        total_w = left_w * 4

        center = input_expr[:, :, left_h:right_h, left_w:right_w]
        surround = input_expr[:, :, 0:total_h:2, 0:total_w:2]

        output_shape = self.get_output_shape_for(input_shape)

        if T is theano.tensor:
            center.name = 'center'
            surround.name = 'surround'
            # production theano version
            output_expr = T.alloc(0.0, *output_shape)
            output_expr.name = 'center_surround_alloc'
            set_subtensor = functools.partial(T.set_subtensor)
            #set_subtensor = functools.partial(T.set_subtensor, inplace=True, tolerate_inplace_aliasing=True)
            output_expr = set_subtensor(output_expr[::2], center)
            output_expr = set_subtensor(output_expr[1::2], surround)
            output_expr.name = 'center_surround_output'
            #from ibeis_cnn import draw_net
            #draw_net.draw_theano_symbolic_expression(output_expr)
        else:
            # debugging numpy version
            output_expr = np.empty(output_shape, dtype=input_expr.dtype)
            output_expr[::2] =  center
            output_expr[1::2] =  surround
        #output_expr = T.concatenate([center, surround], axis=0)
        return output_expr


class MultiImageSliceLayer(lasagne.layers.Layer):
    """
    orig CyclicSliceLayer
    References:
        https://github.com/benanne/kaggle-ndsb/blob/master/dihedral.py#L89

    This layer stacks rotations of 0, 90, 180, and 270 degrees of the input
    along the batch dimension.
    If the input has shape (batch_size, num_channels, r, c),
    then the output will have shape (4 * batch_size, num_channels, r, c).
    Note that the stacking happens on axis 0, so a reshape to
    (4, batch_size, num_channels, r, c) will separate the slice axis.
    """
    def __init__(self, input_layer):
        super(MultiImageSliceLayer, self).__init__(input_layer)

    def get_output_shape_for(self, input_shape):
        return (4 * input_shape[0],) + input_shape[1:]

    def get_output_for(self, input_, *args, **kwargs):
        return lasagne.utils.concatenate([
            #array_tf_0(input_),
            #array_tf_90(input_),
            #array_tf_180(input_),
            #array_tf_270(input_),
        ], axis=0)


class MultiImageRollLayer(lasagne.layers.Layer):
    """
    orig CyclicConvRollLayer


    This layer turns (n_views * batch_size, num_channels, r, c) into
    (n_views * batch_size, n_views * num_channels, r, c) by rolling
    and concatenating feature maps.
    It also applies the correct inverse transforms to the r and c
    dimensions to align the feature maps.

    References:
        https://github.com/benanne/kaggle-ndsb/blob/master/dihedral.py#L224
    """
    def __init__(self, input_layer):
        super(MultiImageRollLayer, self).__init__(input_layer)
        self.inv_tf_funcs = []  # array_tf_0, array_tf_270, array_tf_180, array_tf_90]
        self.compute_permutation_matrix()

    def compute_permutation_matrix(self):
        map_identity = np.arange(4)
        map_rot90 = np.array([1, 2, 3, 0])

        valid_maps = []
        current_map = map_identity
        for k in range(4):
            valid_maps.append(current_map)
            current_map = current_map[map_rot90]

        self.perm_matrix = np.array(valid_maps)

    def get_output_shape_for(self, input_shape):
        return (input_shape[0], 4 * input_shape[1]) + input_shape[2:]

    def get_output_for(self, input_, *args, **kwargs):
        s = input_.shape
        input_unfolded = input_.reshape((4, s[0] // 4, s[1], s[2], s[3]))

        permuted_inputs = []
        for p, inv_tf in zip(self.perm_matrix, self.inv_tf_funcs):
            input_permuted = inv_tf(input_unfolded[p].reshape(s))
            permuted_inputs.append(input_permuted)

        return lasagne.utils.concatenate(permuted_inputs, axis=1)  # concatenate long the channel axis


class CyclicPoolLayer(lasagne.layers.Layer):
    """
    Utility layer that unfolds the viewpoints dimension and pools over it.
    Note that this only makes sense for dense representations, not for
    feature maps (because no inverse transforms are applied to align them).
    """
    def __init__(self, input_layer, pool_function=T.mean):
        super(CyclicPoolLayer, self).__init__(input_layer)
        self.pool_function = pool_function

    def get_output_shape_for(self, input_shape):
        return (input_shape[0] // 4, input_shape[1])

    def get_output_for(self, input_, *args, **kwargs):
        unfolded_input = input_.reshape((4, input_.shape[0] // 4, input_.shape[1]))
        return self.pool_function(unfolded_input, axis=0)


if __name__ == '__main__':
    """
    CommandLine:
        python -m ibeis_cnn.custom_layers
        python -m ibeis_cnn.custom_layers --allexamples
        python -m ibeis_cnn.custom_layers --allexamples --noface --nosrc
    """
    import multiprocessing
    multiprocessing.freeze_support()  # for win32
    import utool as ut  # NOQA
    ut.doctest_funcs()
