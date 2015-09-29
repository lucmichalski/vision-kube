# -*- coding: utf-8 -*-
# utils.py
# provides utilities for learning a neural network model
from __future__ import absolute_import, division, print_function
#import warnings
import time
import numpy as np
#import functools
#import operator
import theano
import theano.tensor as T
from lasagne import layers
import sklearn.cross_validation
import cv2
import cPickle as pickle
import utool as ut
import six
from ibeis_cnn import net_strs
#from six.moves import range, zip
print, rrr, profile = ut.inject2(__name__, '[ibeis_cnn.utils]')


VERBOSE_CNN = ut.get_argflag(('--verbose-cnn', '--verbcnn')) or ut.VERBOSE

RANDOM_SEED = None
# RANDOM_SEED = 42


def checkfreq(freqlike_, count):
    # checks frequency of param, also handles the case where it is specified
    # as a bool
    if ut.is_int(freqlike_):
        return (count % freqlike_) == 0
    else:
        return freqlike_ is True


def concatenate_hack(sequence, axis=0):
    # Hack to fix numpy bug. concatenate should do hstacks on 0-dim arrays
    if len(sequence) > 0 and len(sequence[1].shape) == 0:
        res = np.hstack(sequence)
    else:
        res = np.concatenate(sequence, axis=axis)
    return res


def multiaxis_reduce(ufunc, arr, startaxis=0):
    """ todo: clean and move to vtool
    used to get max/min over all axes after <startaxis>

    CommandLine:
        python -m ibeis_cnn.utils --test-multiaxis_reduce

    Example:
        >>> # ENABLE_DOCTEST
        >>> from ibeis_cnn.utils import *  # NOQA
        >>> np.random.seed(0)
        >>> arr = (np.random.rand(4, 3, 2, 1) * 255).astype(np.uint8)
        >>> ufunc = np.amax
        >>> startaxis = 1
        >>> out_ = multiaxis_reduce(ufunc, arr, startaxis)
        >>> result = out_
        >>> print(result)
        [182 245 236 249]
    """
    num_iters = len(arr.shape) - startaxis
    out_ = ufunc(arr, axis=startaxis)
    for _ in range(num_iters - 1):
        out_ = ufunc(out_, axis=1)
    return out_


def get_gpu_memory():
    """
    References:
        https://groups.google.com/forum/#!topic/theano-users/2EdclcmZazU
        https://gist.github.com/matpalm/9c0c7c6a6f3681a0d39d

    CommandLine:
        python -m ibeis_cnn.utils --test-get_gpu_memory

    Example:
        >>> # ENABLE_DOCTEST
        >>> from ibeis_cnn.utils import *  # NOQA
        >>> result = get_gpu_memory()
        >>> print(result)
    """
    return theano.sandbox.cuda.cuda_ndarray.cuda_ndarray.mem_info()


def _update(kwargs, key, value):
    #if key not in kwargs.keys():
    if key not in kwargs:
        kwargs[key] = value


def testdata_imglist(shape=(32, 32, 3)):
    """
    Returns 4 colored 32x32 test images, one is structured increasing numbers,
    an images with lines of a cartoon face, and two complex images of people.

    CommandLine:
        python -m ibeis_cnn.utils --test-testdata_imglist --show

    Example:
        >>> # ENABLE_DOCTEST
        >>> from ibeis_cnn.utils import *  # NOQA
        >>> (img_list, width, height, channels) = testdata_imglist()
        >>> ut.quit_if_noshow()
        >>> import plottool as pt
        >>> pt.imshow(img_list[0], pnum=(2, 2, 1))
        >>> pt.imshow(img_list[1], pnum=(2, 2, 2))
        >>> pt.imshow(img_list[2], pnum=(2, 2, 3))
        >>> pt.imshow(img_list[3], pnum=(2, 2, 4))
        >>> ut.show_if_requested()

    """
    import vtool as vt
    x = 32
    height, width, channels = shape
    img0 = np.arange(x ** 2 * 3, dtype=np.uint8).reshape(x, x, 3)
    img1 = vt.imread(ut.grab_test_imgpath('jeff.png'))
    img2 = vt.imread(ut.grab_test_imgpath('carl.jpg'))
    img3 = vt.imread(ut.grab_test_imgpath('lena.png'))
    img_list = [vt.padded_resize(img, (width, height)) for img in [img0, img1, img2, img3]]
    return img_list, width, height, channels


def convert_cv2_images_to_theano_images(img_list):
    """
    Converts a list of cv2-style images into a single numpy array of nonflat
    theano-style images.

    h=height, w=width, b=batchid, c=channel

    Args:
        img_list (list of ndarrays): a list of numpy arrays with shape [h, w, c]

    Returns:
        data: in the shape [b, (c x h x w)]

    CommandLine:
        python -m ibeis_cnn.utils --test-convert_cv2_images_to_theano_images

    Example:
        >>> # ENABLE_DOCTEST
        >>> from ibeis_cnn.utils import *  # NOQA
        >>> import vtool as vt
        >>> # build test data
        >>> # execute function
        >>> img_list, width, height, channels = testdata_imglist()
        >>> data = convert_cv2_images_to_theano_images(img_list)
        >>> data[0].reshape(3, 32, 32)[:, 0:2, 0:2]
        >>> subset = (data[0].reshape(3, 32, 32)[:, 0:2, 0:2])
        >>> #result = str(np.transpose(subset, (1, 2, 0)))
        >>> result = str(subset).replace('\n', '')
        >>> print(result)
        [[[  0   3]  [ 96  99]] [[  1   4]  [ 97 100]] [[  2   5]  [ 98 101]]]
    """
    #[img.shape for img in img_list]
    # format to [b, c, h, w]
    if len(img_list.shape) == 3:
        # ensure 4 dimensions
        img_list = img_list.reshape(img_list.shape + (1,))
    shape_list = [img.shape for img in img_list]
    assert ut.list_allsame(shape_list)
    theano_style_imgs = [np.transpose(img, (2, 0, 1))[None, :] for img in img_list]
    data = np.vstack(theano_style_imgs)
    #data = np.vstack([img[None, :] for img in img_list])
    return data


def convert_theano_images_to_cv2_images(data, *args):
    #width, height, channels):
    r"""
    Args:
        data (ndarray): in the shape [b, (c x h x w)]

    Returns:
        img_list (list of ndarrays): a list of numpy arrays with shape [h, w, c]

    CommandLine:
        python -m ibeis_cnn.utils --test-convert_data_to_imglist

    Example:
        >>> # ENABLE_DOCTEST
        >>> from ibeis_cnn.utils import *  # NOQA
        >>> # build test data
        >>> img_list, width, height, channels = testdata_imglist()
        >>> data = convert_imagelist_to_data(img_list)
        >>> img_list2 = convert_data_to_imglist(data, width, height, channels)
        >>> assert np.all(img_list == img_list2)
    """
    #num_imgs = data.shape[0]
    #newshape = (num_imgs, channels, width, height)
    #data_ = data.reshape(newshape)
    img_list = np.transpose(data, (0, 2, 3, 1))
    return img_list


def evaluate_symbolic_layer(get_output_for, inputdata_, input_type=T.tensor4, **kwargs):
    """ helper for testing lasagne layers """
    input_expr = input_type(name='test_input_expr')  # T.tensor4()
    output_expr = get_output_for(input_expr, **kwargs)
    func = theano.function(inputs=[input_expr], outputs=[output_expr])
    output_T = func(inputdata_)[0]
    return output_T


def get_current_time():
    return time.strftime('%Y-%m-%d %H:%M:%S')


def testdata_xy(data_per_label=2, factor=20, seed=0):
    img_list, width, height, channels = testdata_imglist()
    data = np.array((img_list * factor))
    randstate = np.random.RandomState(seed)
    labels = randstate.rand(len(data) / data_per_label) > .5
    #data_per_label = 2
    return data, labels, data_per_label


def train_test_split(X, y, eval_size, data_per_label=1, shuffle=True):
    r"""
    used to split datasets into two parts.
    Preserves class distributions using Stratified K-Fold sampling

    Args:
        X (ndarray):
        y (ndarray):
        eval_size (?):

    Returns:
        tuple: (X_train, y_train, X_valid, y_valid)

    CommandLine:
        python -m ibeis_cnn.utils --test-train_test_split

    Example:
        >>> # DISABLE_DOCTEST
        >>> from ibeis_cnn.utils import *  # NOQA
        >>> # build test data
        >>> data, labels, data_per_label = testdata_xy()
        >>> X = data
        >>> y = labels
        >>> eval_size = .2
        >>> # execute function
        >>> (X_train, y_train, X_valid, y_valid) = train_test_split(X, y, eval_size, data_per_label)
        >>> # verify results
        >>> result = str((X_train, y_train, X_valid, y_valid))
        >>> print(result)
    """
    # take the data and label arrays, split them preserving the class distributions
    assert len(X) == len(y) * data_per_label
    nfolds = round(1. / eval_size)
    kf = sklearn.cross_validation.StratifiedKFold(y, nfolds, shuffle=shuffle)
    train_indices, valid_indices = six.next(iter(kf))
    data_train_indicies = expand_data_indicies(train_indices, data_per_label)
    data_valid_indicies = expand_data_indicies(valid_indices, data_per_label)
    X_train = X.take(data_train_indicies, axis=0)
    X_valid = X.take(data_valid_indicies, axis=0)
    y_train = y.take(train_indices, axis=0)
    y_valid = y.take(valid_indices, axis=0)
    return X_train, y_train, X_valid, y_valid


def write_data_and_labels(data, labels, data_fpath, labels_fpath):
    print('[write_data_and_labels] np.shape(data) = %r' % (np.shape(data),))
    print('[write_data_and_labels] np.shape(labels) = %r' % (np.shape(labels),))
    # to resize the images back to their 2D-structure:
    # X = images_array.reshape(-1, 3, 48, 48)
    print('[write_data_and_labels] writing training data to %s...' % (data_fpath))
    with open(data_fpath, 'wb') as ofile:
        np.save(ofile, data)

    print('[write_data_and_labels] writing training labels to %s...' % (labels_fpath))
    with open(labels_fpath, 'wb') as ofile:
        np.save(ofile, labels)


def load(data_fpath, labels_fpath=None):
    # Load X matrix (data)
    data = np.load(data_fpath, mmap_mode='r')
    # Load y vector (labels)
    labels = None
    if labels_fpath is not None:
        labels = np.load(labels_fpath, mmap_mode='r')
    # Return data
    return data, labels


def load_ids(id_fpath, labels_fpath=None):
    # Load X matrix (data)
    ids = np.load(id_fpath, mmap_mode='r')
    # Return data
    return ids


def get_printcolinfo(requested_headers=None):
    r"""
    Example:
        >>> # ENABLE_DOCTEST
        >>> from ibeis_cnn.utils import *  # NOQA
        >>> requested_headers = None
        >>> printcol_info = get_printcolinfo(requested_headers)
    """
    if requested_headers is None:
        requested_headers = ['epoch', 'train_loss', 'valid_loss', 'trainval_rat', 'valid_acc', 'test_acc', 'duration']
    header_dict = {
        'epoch'        : '   Epoch ',
        'train_loss'   : '  Train Loss (determ)  ',
        'valid_loss'   : '  Valid Loss  ',
        'trainval_rat' : '  Train / Val (determ)  ',
        'valid_acc'    : '  Valid Acc ',
        'test_acc'     : '  Test Acc  ',
        'duration'     : '  Dur ',
    }

    def datafmt(str_, align='>', precision=None, type_='', colored=False, lbl=''):
        precision_str = '.%d' % (precision,) if precision is not None else ''
        num_nonspace = len(str_.strip())
        num_lspace = len(str_.rstrip()) - num_nonspace
        num_rspace = len(str_.lstrip()) - num_nonspace
        middle_fmt = '{:%s%d%s%s}%s' % (align, num_nonspace, precision_str, type_, lbl)
        lspace = (' ' * num_lspace)
        rspace = (' ' * num_rspace)
        sep = '{}' if colored else ''
        return sep.join((lspace, middle_fmt, rspace))

    format_dict = {
        'epoch'        : datafmt(header_dict['epoch'], '>'),
        'train_loss'   : datafmt(header_dict['train_loss'], '<', colored=True),
        'valid_loss'   : datafmt(header_dict['valid_loss'], '>', 6, 'f', colored=True),
        'trainval_rat' : datafmt(header_dict['trainval_rat'], '<', colored=True),
        'valid_acc'    : datafmt(header_dict['valid_acc'], '>', colored=True),
        'test_acc'     : datafmt(header_dict['test_acc'], '>', colored=True),
        'duration'     : datafmt(header_dict['duration'], '>', 1, 'f', lbl='s'),
    }
    data_fmt_list = ut.dict_take(format_dict, requested_headers)
    header_nice_list = ut.dict_take(header_dict, requested_headers)
    printcol_info = {
        'requested_headers': requested_headers,
        'header_nice_list': header_nice_list,
        'data_fmt_list': data_fmt_list,
    }
    return printcol_info


def print_header_columns(printcol_info):
    header_nice_list = printcol_info['header_nice_list']
    header_line_list = ['-' * len(nice) for nice in header_nice_list]
    header_line1 = '[info] ' + '|'.join(header_nice_list)
    header_line2 = '[info] ' + '|'.join(header_line_list)
    header_str = ('\n' + header_line1 + '\n' + header_line2)
    #print(header_str)
    #header_str = ut.codeblock(
    #    '''
    #    [info]   Epoch |  Train Loss (determ)  |  Valid Loss  |  Train / Val (determ)  |  Valid Acc  |  Test Acc   |  Dur
    #    [info] --------|-----------------------|--------------|------------------------|-------------|-------------|------\
    #    '''
    #)
    print(header_str)


def print_epoch_info(printcol_info, epoch_info):
    requested_headers = printcol_info['requested_headers']

    (train_loss, train_determ_loss, valid_loss, valid_accuracy, test_accuracy,
     epoch, duration, best_train_loss, best_valid_loss, best_valid_accuracy,
     best_test_accuracy) = ut.dict_take(epoch_info, [
         'train_loss', 'train_determ_loss', 'valid_loss', 'valid_accuracy',
         'test_accuracy', 'epoch', 'duration', 'best_train_loss',
         'best_valid_loss', 'best_valid_accuracy', 'best_test_accuracy'])

    best_train      = train_loss == best_train_loss
    best_valid      = valid_loss == best_valid_loss
    best_valid_accuracy = valid_accuracy == best_valid_accuracy
    best_train_accuracy = test_accuracy == best_test_accuracy
    ratio           = train_loss / valid_loss
    ratio_determ    = None if train_determ_loss is None else train_determ_loss / valid_loss
    unhealthy_ratio = ratio <= 0.5 or 2.0 <= ratio
    data_fmt_list = printcol_info['data_fmt_list']
    data_fmtstr = '[info] ' +  '|'.join(data_fmt_list)
    #data_fmtstr = ('[info]  {:>5}  |  {}{:<19}{}  |  {}{:>10.6f}{}  '
    #               '|  {}{:<20}{}  |  {}{:>9}{}  |  {}{:>9}{}  |  {:>3.1f}s')
    import colorama
    ANSI = colorama.Fore

    # NOTE: can use pygments or colorama (which supports windows) instead
    #class ANSI(object):
    #    RED     = '\033[91m'
    #    GREEN   = '\033[92m'
    #    BLUE    = '\033[94m'
    #    CYAN    = '\033[96m'
    #    WHITE   = '\033[97m'
    #    YELLOW  = '\033[93m'
    #    MAGENTA = '\033[95m'
    #    GREY    = '\033[90m'
    #    BLACK   = '\033[90m'
    #    # DEFAULT = '\033[99m'
    #    RESET   = '\033[0m'

    def epoch_str():
        return (epoch_info['epoch'],)

    def train_loss_str():
        train_loss = epoch_info['train_loss']
        best_train_loss = epoch_info['best_train_loss']
        train_determ_loss = epoch_info['train_determ_loss']
        best_train      = train_loss == best_train_loss
        return (
            ANSI.BLUE if best_train else '',
            '%0.6f' % (train_loss, ) if train_determ_loss is None else '%0.6f (%0.6f)' % (train_loss, train_determ_loss),
            ANSI.RESET if best_train else '',
        )

    def valid_loss_str():
        return (ANSI.GREEN if best_valid else '', valid_loss, ANSI.RESET if best_valid else '',)

    def trainval_rat_str():
        return (
            ANSI.RED if unhealthy_ratio else '',
            '%0.6f' % (ratio, ) if ratio_determ is None else '%0.6f (%0.6f)' % (ratio, ratio_determ),
            ANSI.RESET if unhealthy_ratio else '',)

    def valid_acc_str():
        return (ANSI.MAGENTA if best_valid_accuracy else '',
                '{:.2f}%'.format(valid_accuracy * 100),
                ANSI.RESET if best_valid_accuracy else '',)

    def test_acc_str():
        return (ANSI.CYAN if best_train_accuracy else '',
                '{:.2f}%'.format(test_accuracy * 100) if test_accuracy is not None else '',
                ANSI.RESET if best_train_accuracy else '',)

    def duration_str():
        return (duration,)

    # Hack to build up the format data
    locals_ = locals()
    func_list = [locals_[prefix + '_str'] for prefix in requested_headers]
    fmttup = tuple()
    for func in func_list:
        fmttup += func()
    #fmttup = (
    #    epoch,
    #    ANSI.BLUE if best_train else '',
    #    '%0.6f' % (train_loss, ) if train_determ_loss is None else '%0.6f (%0.6f)' % (train_loss, train_determ_loss),
    #    ANSI.RESET if best_train else '',
    #    ANSI.GREEN if best_valid else '',
    #    valid_loss,
    #    ANSI.RESET if best_valid else '',
    #    ANSI.RED if unhealthy_ratio else '',
    #    '%0.6f' % (ratio, ) if ratio_determ is None else '%0.6f (%0.6f)' % (ratio, ratio_determ),
    #    ANSI.RESET if unhealthy_ratio else '',
    #    ANSI.MAGENTA if best_valid_accuracy else '',
    #    '{:.2f}%'.format(valid_accuracy * 100),
    #    ANSI.RESET if best_valid_accuracy else '',
    #    ANSI.CYAN if best_train_accuracy else '',
    #    '{:.2f}%'.format(test_accuracy * 100) if test_accuracy is not None else '',
    #    ANSI.RESET if best_train_accuracy else '',
    #    duration,
    #)
    epoch_info_str = data_fmtstr.format(*fmttup)
    print(epoch_info_str)


def float32(k):
    return np.cast['float32'](k)


def expand_data_indicies(indices, data_per_label=1):
    expanded_indicies = [indices * data_per_label + count for count in range(data_per_label)]
    data_indices = np.vstack(expanded_indicies).T.flatten()
    return data_indices


def make_random_indicies(num, seed=RANDOM_SEED):
    r"""
    Args:
        num (int):
        seed (hashable):

    Returns:
        ndarray: random_indicies

    CommandLine:
        python -m ibeis_cnn.utils --test-make_random_indicies

    Example:
        >>> # DISABLE_DOCTEST
        >>> from ibeis_cnn.utils import *  # NOQA
        >>> num = 10
        >>> seed = 42
        >>> random_indicies = make_random_indicies(num, seed)
        >>> result = str(random_indicies)
        >>> print(result)
        [8 1 5 0 7 2 9 4 3 6]
    """
    randstate = np.random.RandomState(seed)
    random_indicies = np.arange(num)
    randstate.shuffle(random_indicies)
    return random_indicies


def data_label_shuffle(X, y, data_per_label=1, seed=RANDOM_SEED):
    r"""
    CommandLine:
        python -m ibeis_cnn.utils --test-data_label_shuffle

    Example:
        >>> # ENABLE_DOCTEST
        >>> from ibeis_cnn.utils import *  # NOQA
        >>> X, y, data_per_label = testdata_xy()
        >>> data_per_label = 2
        >>> seed = 42
        >>> result = data_label_shuffle(X, y, data_per_label, seed=seed)
        >>> print(result)
    """
    num_labels = len(X) // data_per_label
    if y is not None:
        assert num_labels == len(y), 'misaligned len(X)=%r, len(y)=%r' % (len(X), len(y))
    random_indicies = make_random_indicies(num_labels, seed=seed)
    data_random_indicies = expand_data_indicies(random_indicies, data_per_label)
    X = X.take(data_random_indicies, axis=0)
    X = np.ascontiguousarray(X)
    if y is not None:
        y =  y.take(random_indicies, axis=0)
        y =  np.ascontiguousarray(y)
    return X, y


def slice_data_labels(X, y, batch_size, batch_index, data_per_label, wraparound=False, verbose=False):
    r"""
    CommandLine:
        python -m ibeis_cnn.utils --test-slice_data_labels

    Example:
        >>> # DISABLE_DOCTEST
        >>> from ibeis_cnn.utils import *  # NOQA
        >>> X = np.random.rand(67, 3, 5, 4)
        >>> y = (np.random.rand(67) * 4).astype(np.int32)
        >>> batch_size = 32
        >>> batch_index = 2
        >>> data_per_label = 1
        >>> wraparound = True
        >>> verbose = False
        >>> Xb, yb = slice_data_labels(X, y, batch_size, batch_index, data_per_label, wraparound, verbose)
        >>> result = str(Xb.shape)
        >>> print(result)
        (32, 3, 5, 4)
    """
    start_x = batch_index * batch_size
    end_x = (batch_index + 1) * batch_size
    #x_sl = slice(start_y * data_per_label, end_y * data_per_label)
    #y_sl = slice(start_y, end_y)
    # Take full batch of images and take the fraction of labels if data_per_label > 1
    x_sl = slice(start_x, end_x)
    y_sl = slice(start_x // data_per_label, end_x // data_per_label)
    Xb = X[x_sl]
    if y is not None:
        yb = y[y_sl]
    else:
        yb = None
    if wraparound:
        if Xb.shape[0] != batch_size:
            extra = batch_size - Xb.shape[0]
            Xb_extra = X[slice(0, extra)]
            Xb = np.concatenate([Xb, Xb_extra], axis=0)
            if yb is not None:
                yb_extra = y[slice(0, extra // data_per_label)]
                yb = np.concatenate([yb, yb_extra], axis=0)
            #print('WRAP')
    # Get corret dtype for X
    Xb = Xb.astype(np.float32)
    if verbose:
        print('[batchiter]   * x_sl = %r' % (x_sl,))
        print('[batchiter]   * y_sl = %r' % (y_sl,))
    return Xb, yb


def whiten_data(Xb, center_mean, center_std):
    Xb = Xb.copy()
    if center_mean is not None and center_std is not None and center_std != 0.0:
        Xb -= center_mean
        Xb /= center_std
    return Xb


def multinomial_nll(x, t):
    #coding_dist=x, true_dist=t
    return T.nnet.categorical_crossentropy(x, t)


def add_channels(data):
    add = 5 + 3 + 3 + 2
    points, channels, height, width = data.shape
    dtype = data.dtype
    data_channels = np.empty((points, channels + add, height, width), dtype=dtype)
    data_channels[:, :channels, :, :] = data
    for index in range(points):
        image     = cv2.merge(data[index])
        hsv       = cv2.cvtColor(image, cv2.COLOR_BGR2HSV)
        lab       = cv2.cvtColor(image, cv2.COLOR_BGR2LAB)
        grayscale = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
        sobelx    = cv2.Sobel(grayscale, -1, 1, 0)
        sobely    = cv2.Sobel(grayscale, -1, 0, 1)
        sobelxx   = cv2.Sobel(sobelx, -1, 1, 0)
        sobelyy   = cv2.Sobel(sobely, -1, 0, 1)
        sobelxy   = cv2.Sobel(sobelx, -1, 0, 1)
        data_channels[index, 3:6, :, :] = np.array(cv2.split(hsv))
        data_channels[index, 6:9, :, :] = np.array(cv2.split(lab))
        data_channels[index, 6:9, :, :] = np.array(cv2.split(lab))
        data_channels[index, 9, :, :]   = grayscale
        data_channels[index, 10, :, :]  = 255.0 - grayscale
        data_channels[index, 11, :, :]  = sobelx
        data_channels[index, 12, :, :]  = sobely
        data_channels[index, 13, :, :]  = sobelxx
        data_channels[index, 14, :, :]  = sobelyy
        data_channels[index, 15, :, :]  = sobelxy
    return data_channels


def show_image_from_data(data):
    def add_to_template(template, x, y, image_):
        template[y * h : (y + 1) * h, x * h : (x + 1) * w] = image_

    def replicate(channel, index=None):
        temp = np.zeros((3, h, w), dtype=np.uint8)
        if index is None:
            temp[0] = channel
            temp[1] = channel
            temp[2] = channel
        else:
            temp[index] = channel
        return cv2.merge(temp)

    template_h, template_w = (5, 5)
    image = data[0]
    c, h, w = image.shape

    # Create temporary copies for displaying
    bgr_   = cv2.merge(image[0:3])
    hsv_   = cv2.merge(image[3:6])
    lab_   = cv2.merge(image[6:9])

    template = np.zeros((template_h * h, template_w * w, 3), dtype=np.uint8)
    add_to_template(template, 0, 0, replicate(image[0]))
    add_to_template(template, 1, 0, replicate(image[1]))
    add_to_template(template, 2, 0, replicate(image[2]))
    add_to_template(template, 3, 0, bgr_)

    add_to_template(template, 0, 1, replicate(image[3]))
    add_to_template(template, 1, 1, replicate(image[4]))
    add_to_template(template, 2, 1, replicate(image[5]))
    add_to_template(template, 3, 1, hsv_)

    add_to_template(template, 0, 2, replicate(image[6]))
    add_to_template(template, 1, 2, replicate(image[7]))
    add_to_template(template, 2, 2, replicate(image[8]))
    add_to_template(template, 3, 2, lab_)

    add_to_template(template, 0, 3, replicate(image[9]))
    add_to_template(template, 1, 3, replicate(image[10]))

    add_to_template(template, 0, 4, replicate(image[11]))
    add_to_template(template, 1, 4, replicate(image[12]))
    add_to_template(template, 2, 4, replicate(image[13]))
    add_to_template(template, 3, 4, replicate(image[14]))
    add_to_template(template, 4, 4, replicate(image[15]))

    cv2.imshow('template', template)
    cv2.waitKey(0)
    cv2.destroyAllWindows()


def save_model(kwargs, weights_file):
    print('[model] saving best weights to %s' % (weights_file))
    with open(weights_file, 'wb') as pfile:
        pickle.dump(kwargs, pfile, protocol=pickle.HIGHEST_PROTOCOL)
    print('[model] ...saved\n')


def shock_network(output_layer, voltage=0.10):
    print('[model] shocking the network with voltage: %0.2f%%' % (voltage, ))
    current_weights = layers.get_all_param_values(output_layer)
    for index in range(len(current_weights)):
        temp = current_weights[index] * voltage
        shock = np.random.uniform(low=-1.0, high=1.0, size=temp.shape)
        temp *= shock
        # Apply shock
        current_weights[index] += temp
    layers.set_all_param_values(output_layer, current_weights)
    print('[model] ...shocked')


def set_learning_rate(learning_rate_theano, update):
    new_learning_rate_theano = update(learning_rate_theano.get_value())
    learning_rate_theano.set_value(float32(new_learning_rate_theano))
    print('\n[train] setting learning rate to %.9f' % (new_learning_rate_theano))


def save_pretrained_weights_slice(pretrained_weights, weights_path, slice_=slice(None)):
    """
    Used to save a slice of pretrained weights. The doctest will publish a new set of weights

    CommandLine:
        python -m ibeis_cnn.utils --test-save_pretrained_weights_slice --net='vggnet_full' --slice='slice(0,6)'
        python -m ibeis_cnn.utils --test-save_pretrained_weights_slice --net='vggnet_full' --slice='slice(0,30)'
        python -m ibeis_cnn.utils --test-save_pretrained_weights_slice --net='caffenet_full' --slice='slice(0,6)'
        python -m ibeis_cnn.utils --test-save_pretrained_weights_slice --net='caffenet_full' --slice='slice(0,?)'

    Example:
        >>> # DISABLE_DOCTEST
        >>> # Build a new subset of an existing model
        >>> from ibeis_cnn.models import *  # NOQA
        >>> from ibeis_cnn._plugin_grabmodels import ensure_model
        >>> # Get base model weights
        >>> modelname = ut.get_argval('--net', type_=str, default='vggnet_full')
        >>> weights_path = ensure_model(modelname)
        >>> pretrained_weights = ut.load_cPkl(weights_path)
        >>> # Get the slice you want
        >>> slice_str = ut.get_argval('--slice', type_=str, default='slice(0, 6)')
        >>> slice_ = eval(slice_str, globals(), locals())
        >>> # execute function
        >>> sliced_weights_path = save_pretrained_weights_slice(pretrained_weights, weights_path, slice_)
        >>> # PUT YOUR PUBLISH PATH HERE
        >>> publish_fpath = ut.truepath('~/Dropbox/IBEIS')
        >>> ut.copy(sliced_weights_path, publish_fpath)
    """
    # slice and save
    suffix = '.slice_%r_%r_%r' % (slice_.start, slice_.stop, slice_.step)
    sliced_weights_path = ut.augpath(weights_path, suffix)
    sliced_pretrained_weights = pretrained_weights[slice_]
    ut.save_cPkl(sliced_weights_path, sliced_pretrained_weights)
    # print info
    net_strs.print_pretrained_weights(pretrained_weights, weights_path)
    net_strs.print_pretrained_weights(sliced_pretrained_weights, sliced_weights_path)
    return sliced_weights_path


if __name__ == '__main__':
    """
    CommandLine:
        python -m ibeis_cnn.utils
        python -m ibeis_cnn.utils --allexamples
        python -m ibeis_cnn.utils --allexamples --noface --nosrc
    """
    import multiprocessing
    multiprocessing.freeze_support()  # for win32
    import utool as ut  # NOQA
    ut.doctest_funcs()
