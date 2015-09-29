
import argparse
import numpy as np
from sklearn.preprocessing import StandardScaler

from gmm import load_gmm
from utils import import_dataset


def compute_L2_normalization(xx):
    """ Computes the L2 norm along the rows, i.e. for each example.

    Input
    -----
    xx: array [N, D]
        Data.

    Output
    ------
    Z: array [N]
        Normalization terms.

    """
    return np.sum(xx ** 2, axis=1)


def L2_normalize(xx):
    """ L2-normalizes each row of the data xx.

    Input
    -----
    xx: array [N, D]
        Data.

    Output
    ------
    yy: array [N, D]
        L2-normlized data.

    """
    Zx = compute_L2_normalization(xx)
    return xx / np.sqrt(Zx[:, np.newaxis])


def power_normalize(xx, alpha):
    """ Computes a alpha-power normalization for the matrix xx. """
    return np.sign(xx) * np.abs(xx) ** alpha


def data_to_kernels(load_data_, ssqrt=True, l2_norm=True, verbose=0):
    """ Get kernels from data. The argument `load_data_` is a callable that
    takes a string argument ("train" or "test") indicating the split which it
    will load. If `l2_norm` is True the kernel matrices are L2 normalized,
    otherwised the L2-norm of the data is returned and it can be later used.

    """
    if verbose > 0: print 'Loading train data.'
    tr_data, tr_names, tr_labels = load_data_('train')
    if verbose > 0: print '\tData shape %dx%d.' % tr_data.shape

    if verbose > 0: print '\tData normalization.'
    scaler = StandardScaler()
    tr_data = scaler.fit_transform(tr_data)
    if ssqrt: tr_data = power_normalize(tr_data, 0.5)
    if l2_norm: tr_data = L2_normalize(tr_data)
    tr_Z = compute_L2_normalization(tr_data)

    if verbose > 0: print '\tComputing kernel.'
    tr_kernel = np.dot(tr_data, tr_data.T)

    if verbose > 0: print 'Loading test data.'
    te_data, te_names, te_labels = load_data_('test')
    if verbose > 0: print '\tData shape %dx%d.' % te_data.shape

    if verbose > 0: print '\tData normalization.'
    te_data = scaler.transform(te_data)
    if ssqrt: te_data = power_normalize(te_data, 0.5)
    if l2_norm: te_data = L2_normalize(te_data)
    te_Z = compute_L2_normalization(te_data)

    if verbose > 0: print '\tComputing kernel.'
    te_kernel = np.dot(te_data, tr_data.T)

    return (tr_kernel, tr_names, tr_labels, tr_Z,
            te_kernel, te_names, te_labels, te_Z)


def load_kernels(loader, spms, models):
    """Loads kernel matrices. Does normalization on each """

    tr_kernel = None
    for spm in spms:
        for bin in xrange(np.prod(spm)):

            tr_kernel_enc = None
            for model in models:

                print spm, bin, model

                (tr_kernel_, tr_names_, tr_labels_, tr_Z_,
                 te_kernel_, te_names_, te_labels_, te_Z_) = data_to_kernels(
                     lambda split: loader(
                         split, spm=spm, model=model, bin=bin),
                     ssqrt=True, l2_norm=False)
                
                if tr_kernel_enc is None:
                    # Initialization.
                    tr_kernel_enc = tr_kernel_
                    te_kernel_enc = te_kernel_
                    tr_Z = tr_Z_
                    te_Z = te_Z_
                else:
                    tr_kernel_enc += tr_kernel_
                    te_kernel_enc += te_kernel_
                    tr_Z += tr_Z_
                    te_Z += te_Z_

                if tr_kernel is None:
                    tr_names = tr_names_
                    te_names = te_names_
                    tr_labels = tr_labels_
                    te_labels = te_labels_
                else:
                    # Check that everything is properly aligned.
                    assert np.all(tr_names == tr_names_)
                    assert np.all(te_names == te_names_)
                    assert np.all(tr_labels == tr_labels_)
                    assert np.all(te_labels == te_labels_)

            tr_kernel_enc /= np.sqrt(tr_Z[:, np.newaxis] * tr_Z[np.newaxis])
            te_kernel_enc /= np.sqrt(te_Z[:, np.newaxis] * tr_Z[np.newaxis])

            if tr_kernel is None:
                tr_kernel = tr_kernel_enc
                te_kernel = te_kernel_enc
            else:
                tr_kernel += tr_kernel_enc
                te_kernel += te_kernel_enc

    return tr_kernel, tr_names, tr_labels, te_kernel, te_names, te_labels


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        '-d', '--dataset', required=True,
        help="name of the dataset configuration file; it should have the "
        "format <file>.<class_instance> (e.g., 'hollywood2.small'.")
    args = parser.parse_args()

    # Dynamically import the configuration instance.
    dataset = import_dataset(*args.dataset.split('.'))

    gmm = load_gmm(dataset.gmm_path)
    dims = {
        'FV': gmm.k + 2 * gmm.d * gmm.k,
        'SFV': 2 * 3 * gmm.k,
    }

    def loader(split, spm, model, bin):
        samples_to_labels = dataset.get_samples_with_labels([split])
        samples = samples_to_labels.keys()
        labels = samples_to_labels.values()
        data = np.zeros((len(samples), dims[model]), dtype=np.float32)
        for ii, sample in enumerate(samples_to_labels.keys()):
            filename = dataset.get_feature_path(sample, model, spm)
            fv = np.fromfile(filename, dtype=np.float32)
            data[ii] = fv.reshape(-1, dims[model])[bin]
        return data, samples, labels

    tr_kernel, _, tr_labels, te_kernel, _, te_labels = load_kernels(
        loader, dataset.spms, dataset.models)
    clf = dataset.get_classifier()
    clf.fit(tr_kernel, tr_labels)
    print "\tAccuracy: %3.2f" % (100 * np.mean(clf.score(te_kernel, te_labels)))


if __name__ == '__main__':
    main()

