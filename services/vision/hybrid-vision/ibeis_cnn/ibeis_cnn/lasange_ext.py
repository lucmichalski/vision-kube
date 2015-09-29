from __future__ import absolute_import, division, print_function
from lasagne import layers
import warnings
import theano
import theano.tensor as T
import utool as ut
ut.noinject('lasange_ext')


def l1(layer, include_biases=False):
    """ custom should move to regulariztion.lasange.l1

    NOT WORKING

    """
    raise NotImplementedError('not working')
    with warnings.catch_warnings():
        #warnings.simplefilter("ignore")
        warnings.filterwarnings('ignore', '.*topo.*')
        if include_biases:
            all_params = layers.get_all_params(layer)
        else:
            #all_params = layers.get_all_non_bias_params(layer)
            all_params = layers.get_all_params(regularizable=True)

    return sum(T.sum(T.abs_(p)) for p in all_params)


def testdata_contrastive_loss():
    import numpy as np
    batch_size = 128
    num_output = 256
    half_size = batch_size // 2
    quar_size = batch_size // 4
    eigh_size = batch_size // 8
    G = np.random.rand(batch_size, num_output)
    G = G / np.linalg.norm(G, axis=1, ord=2)[:, None]
    G[0] = G[1]
    G[half_size] = G[half_size + 1]
    G[0:eigh_size:2] = G[1:eigh_size:2] + np.random.rand(eigh_size / 2, num_output) * .00001
    Y_padded = np.ones(batch_size)
    Y_padded[0:half_size] = 1
    Y_padded[quar_size:half_size + quar_size]  = 0
    Y_padded[-half_size:] = -1
    return G, Y_padded


def siamese_loss(G, Y_padded, data_per_label=2, T=T):
    """
    Args:
        G : network output
        Y_padded: : target groundtruth labels (padded at the end with dummy values)

    References:
        https://www.cs.nyu.edu/~sumit/research/assets/cvpr05.pdf
        https://github.com/Lasagne/Lasagne/issues/168

    CommandLine:
        python -m ibeis_cnn.lasange_ext --test-siamese_loss
        # Train Network
        python -m ibeis_cnn.train --test-train_patchmatch_pz --vtd --max-examples=16 --batch_size=128 --learning_rate .0000001

    CommandLine:
        python -m ibeis_cnn.lasange_ext --test-siamese_loss

    Example:
        >>> # DISABLE_DOCTEST
        >>> from ibeis_cnn.lasange_ext import *  # NOQA
        >>> # numpy testing but in reality these are theano functions
        >>> verbose = True
        >>> G, Y_padded = testdata_contrastive_loss()
        >>> T = np
        >>> np.abs_ = np.abs
        >>> avg_loss = siamese_loss(G, Y_padded, T=T)
    """
    num_data = G.shape[0]
    num_labels = num_data // data_per_label
    # Mark same genuine pairs as 0 and imposter pairs as 1
    Y = (1 - Y_padded[0:num_labels])
    Y = (Y_padded[0:num_labels])

    L1_NORMALIZE = True
    if L1_NORMALIZE:
        # L1-normalize the output of the network
        G = G / T.abs_(G).sum(axis=1)[:, None]

    # Split batch into pairs
    G1, G2 = G[0::2], G[1::2]

    if T is theano.tensor:
        G1.name = 'G1'
        G2.name = 'G2'

    # Energy of training pairs
    if False:
        # Hack in a print
        G_ellone = T.abs_(G).sum(axis=1)
        G_ellone_printer = theano.printing.Print('ellone(G)')(G_ellone)
        G_ellone.name = 'G_ellone'
        G_ellone_printer.name = 'G_ellone_printer'
        E = T.abs_((G1 - G2)).sum(axis=1) + (G_ellone - G_ellone_printer)[:, None]
    else:
        E = T.abs_((G1 - G2)).sum(axis=1)

    if T is theano.tensor:
        E.name = 'E'

    # Q is a constant that is the upper bound of E
    if L1_NORMALIZE:
        Q = 2
    else:
        Q = 20
    # Contrastive loss function
    genuine_loss = (1 - Y) * (2 / Q) * (E ** 2)
    imposter_loss = (Y) * 2 * Q * T.exp((-2.77 * E) / Q)
    loss = genuine_loss + imposter_loss
    avg_loss = T.mean(loss)

    if T is theano.tensor:
        loss.name = 'loss'
        avg_loss.name = 'avg_loss'
    return avg_loss


def freeze_params(layer):
    """
    makes a layer untrainable

    References:
        https://github.com/Lasagne/Lasagne/pull/261
    """
    for param in layer.params:
        layer.params[param].discard('trainable')
    return layer
