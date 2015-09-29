
import argparse
from itertools import product
import multiprocessing as mp
import numpy as np
import os
import pdb
import subprocess
import sys

from yael import yael
from yael.threads import RunOnSet

from pca import load_pca
from gmm import load_gmm

from utils import get_video_infos
from utils import grouper
from utils import import_dataset
from utils import regular_multidim_digitize


# Number of descriptors that are read at once.
NR_DESCRIPTORS = 10000
DESC_SIZE = {
    'HOG':  96,
    'HOF': 108,
    'MBH': 192,
}
previous = 40
DESC_IDXS = {}
for desc in 'HOG', 'HOF', 'MBH':
    DESC_IDXS[desc] = range(previous, previous + DESC_SIZE[desc]) 
    previous = previous + DESC_SIZE[desc]


def compute_descriptors(infile, descriptor_type):
    """Reads low-level descriptors from DenseTracks."""

    LEN_LINE = 436
    POS_IDXS = [1, 2, 0]  # Positional coordinates (X, Y, T).

    dense_tracks = subprocess.Popen(
        ['./DenseTrack', infile], stdout=subprocess.PIPE)
    descriptor_idxs = DESC_IDXS[descriptor_type]

    for lines in grouper(dense_tracks.stdout, NR_DESCRIPTORS):

        all_descs = np.vstack([
            map(float, line.split())
            for line in lines
            if line is not None]
        ).astype(np.float32)

        assert all_descs.shape[0] <= NR_DESCRIPTORS
        assert all_descs.shape[1] == LEN_LINE

        yield all_descs[:, POS_IDXS], all_descs[:, descriptor_idxs]


def gmm_predict_proba(xx, gmm):
    """Computes posterior probabilities using yael."""
    N = xx.shape[0]
    K = gmm.k

    Q_yael = yael.fvec_new(N * K)
    yael.gmm_compute_p(
        N, yael.numpy_to_fvec_ref(xx), gmm, Q_yael, yael.GMM_FLAGS_W)
    Q = yael.fvec_to_numpy(Q_yael, N * K).reshape(N, K)
    yael.free(Q_yael)

    return Q


def descriptors_to_fisher_vector(xx, gmm, **kwargs):
    """Computes the Fisher vector on a set of descriptors.

    Parameters
    ----------
    xx: array_like, shape (N, D) or (D, )
        The set of descriptors

    gmm: instance of yael object gmm
        Gauassian mixture model of the descriptors.

    Returns
    -------
    fv: array_like, shape (K + 2 * D * K, )
        Fisher vector (derivatives with respect to the mixing weights, means
        and variances) of the given descriptors.

    Reference
    ---------
    J. Krapac, J. Verbeek, F. Jurie.  Modeling Spatial Layout with Fisher
    Vectors for Image Categorization.  In ICCV, 2011.
    http://hal.inria.fr/docs/00/61/94/03/PDF/final.r1.pdf

    """
    # yael assumes that the data is in C-contiguous format.
    xx = np.ascontiguousarray(np.atleast_2d(xx))

    N = xx.shape[0]
    K = gmm.k
    D = gmm.d

    # Compute posterior probabilities using yael.
    Q = gmm_predict_proba(xx, gmm)  # NxK

    # Get parameters and reshape them.
    pi = yael.fvec_to_numpy(gmm.w, K)                          # 1xK
    mu = yael.fvec_to_numpy(gmm.mu, K * D).reshape(K, D)       # DxK
    sigma = yael.fvec_to_numpy(gmm.sigma, K * D).reshape(K, D) # DxK

    # Compute the sufficient statistics of descriptors.
    Q_sum = np.sum(Q, 0)[:, np.newaxis] / N  # Kx1
    Q_xx = np.dot(Q.T, xx) / N               # KxD
    Q_xx_2 = np.dot(Q.T, xx ** 2) / N        # KxD

    # Compute derivatives with respect to mixing weights, means and variances.
    d_pi = Q_sum.squeeze() - pi
    d_mu = Q_xx - Q_sum * mu
    d_sigma = - Q_xx_2 - Q_sum * mu ** 2 + Q_sum * sigma + 2 * Q_xx * mu

    # Merge derivatives into a vector.
    return np.hstack((d_pi, d_mu.flatten(), d_sigma.flatten()))


def descriptors_to_spatial_fisher_vector(xx, ll, gmm):
    """Computes the spatial Fisher vector from a set of descriptors and their
    corresponding locations.

    Parameters
    ----------
    xx: array_like, shape (N, D) or (D, )
        The set of descriptors.

    ll: array_like, shape (N, 3) or (3, )
        Relative locations of descriptors in the video; the values should
        be from 0 to 1.

    gmm: instance of yael object gmm
        Gauassian mixture model of the descriptors.

    Returns
    -------
    sfv: array_like, shape (2 * 3 * K, )
        Spatial Fisher vector (derivatives with respect to means and variances)
        of the given descriptors.

    Reference
    ---------
    J. Krapac, J. Verbeek, F. Jurie.  Modeling Spatial Layout with Fisher
    Vectors for Image Categorization.  In ICCV, 2011.
    http://hal.inria.fr/docs/00/61/94/03/PDF/final.r1.pdf

    """
    xx = np.ascontiguousarray(np.atleast_2d(xx))
    ll = np.ascontiguousarray(np.atleast_2d(ll))

    N = ll.shape[0]   # Number of descriptors
    K = gmm.k         # Vocabulary size.

    # GMM paramters of locations are set, not learnt.
    mm = np.array([0.5, 0.5, 0.5])             # Mean.
    S = np.array([1. / 12, 1. / 12, 1. / 12])  # Variance.

    mm = mm[np.newaxis]  # 1x3
    S = S[np.newaxis]    # 1x3

    # Compute posterior probabilities using yael.
    Q = gmm_predict_proba(xx, gmm)  # NxK

    # Compute sufficient statistics of locations with respect to the
    # posterior probabilities.
    Q_sum = np.sum(Q, 0)[:, np.newaxis] / N  # Kx1
    Q_ll = np.dot(Q.T, ll) / N               # Kx3
    Q_ll_2 = np.dot(Q.T, ll ** 2) / N        # Kx3

    # Compute derivatives with respect to means and variances.
    d_mm = Q_ll - Q_sum * mm
    d_S = - Q_ll_2 - Q_sum * mm ** 2 + Q_sum * S + 2 * Q_ll * mm

    # Merge derivatives into a vector.
    return np.hstack((d_mm.flatten(), d_S.flatten())).astype(np.float32)


def compute_features(
    video_path, get_feature_path, descriptor_type, pca, gmm,
    redo=False, models=['FV'], spms=[(1, 1, 1)], verbose=0):
    """Computes the spatial Fisher vector from a set of descriptors and their
    corresponding locations.

    Parameters
    ----------
    video_path: str
        The path to the input video file.

    get_feature_path: callable
        Given the `model` and `spm`, returns a `str` representing the path
        where to store computed features.

    descriptor_type: str, {'HOG', 'HOF', 'MBH'}
        Type of the descriptors to be extracted.

    pca: instance of sklearn PCA object
        Principal components analysis object to reduce the dimensionality of
        the descriptors.

    gmm: instance of yael object gmm
        Gauassian mixture model of the descriptors.

    models: list of str, {'FV', 'SFV'}
        The type of features to compute, Fisher vectors (FV) and/or spatial
        Fisher vectors.

    spms: list of tuples
        Spatial pyramid grids.

    """

    DESCS_TO_FEATS = {
        'FV': descriptors_to_fisher_vector,
        'SFV': descriptors_to_spatial_fisher_vector,
    }

    # Helper functions.
    def get_feature_dimension(model, gmm):
        if model == 'FV':
            return gmm.k + 2 * gmm.d * gmm.k
        elif model == 'SFV':
            # Do not keep the `K` derivatives with respect to the mixing
            # weights for the spatial Fisher vector since they are already
            # encoded by the Fisher vector.
            return 2 * 3 * gmm.k 
        else:
            assert False, ("Unknown model %s." % model)

    def get_video_dimensions(input_video):
        input_video_info = get_video_infos(input_video)
        width, height = input_video_info['img_size']
        nr_frames = input_video_info['duration'] * input_video_info['fps']
        return width, height, nr_frames

    def normalize_positions(positions, video_dims):
        """ Normalizes the positions to [0..1]. """
        positions = np.minimum(positions, video_dims)
        return positions / video_dims 

    def save_features(features, nr_descriptors):
        for model, spm in product(models, spms):
            outfile = get_feature_path(model=model, spm=spm)
            # Normalize by the number of descriptors in each spatial cell
            # (ignore empty cell, i.e., zero descriptors).
            nz = (nr_descriptors[model][spm] != 0)[:, 0]
            features[model][spm][nz] /= nr_descriptors[model][spm][nz]
            features[model][spm].tofile(outfile)

    def preallocate_array(models, spms, dims):
        return {
            model: {
                spm: np.zeros((np.prod(spm), dims[model]), dtype=np.float32)
                for spm in spms}
            for model in models}

    def update_sstats(descs, pos, model, spm):
        idxs = regular_multidim_digitize(pos, spm, (0, 0, 0), (1, 1, 1))
        for ii in xrange(np.prod(spm)):

            bin_idxs = idxs == ii
            nr_descs_ii = len(idxs[bin_idxs])

            if nr_descs_ii == 0:
                continue

            features[model][spm][ii] += (
                nr_descs_ii *
                DESCS_TO_FEATS[model](descs[bin_idxs], gmm=gmm, ll=pos[bin_idxs]))
            nr_descriptors[model][spm][ii] += nr_descs_ii

    # Skip work if all the feature files already exist.
    if not redo and np.all([
        os.path.exists(get_feature_path(model=model, spm=spm))
        for model in models
        for spm in spms]):
        return

    dims = {model: get_feature_dimension(model, gmm) for model in models}
    features = preallocate_array(models, spms, dims)
    nr_descriptors = preallocate_array(models, spms, {model: 1 for model in models})
    video_dims = get_video_dimensions(video_path)

    if verbose > 0:
        print "Working on video", video_path

    for positions, descriptors in compute_descriptors(
        video_path, descriptor_type):

        pca_descriptors = pca.transform(descriptors)
        normalized_positions = normalize_positions(positions, video_dims)

        for model, spm in product(models, spms):
            update_sstats(pca_descriptors, normalized_positions, model, spm)

    save_features(features, nr_descriptors)


def correct_idxs(begin, end, min_val, max_val):
    new_begin = min_val if begin is None else np.maximum(min_val, begin)
    new_end = max_val if end is None else np.minimum(max_val, end)
    assert new_begin <= new_end, (
        "The start index should be less or equal than the end index.")
    return new_begin, new_end


def main():
    VALID_DESCRIPTORS = ['HOG', 'HOF', 'MBH']
    VALID_MODELS = ['FV', 'SFV']

    parser = argparse.ArgumentParser(
        description="Extract Fisher vector on dense trajectory descriptors.")

    # Arguments for the `config` command.
    parser.add_argument(
        '-d', '--dataset', required=True,
        help="name of the dataset configuration file; it should have the "
        "format <file>.<class_instance> (e.g., 'hollywood2.small'.")
    parser.add_argument(
        '-b', '--begin_idx', type=int, default=None,
        help="index of the fisrt sample to process.")
    parser.add_argument(
        '-e', '--end_idx', type=int, default=None,
        help="index of the last sample to process.")
    parser.add_argument(
        '-np', '--nr_processes', type=int, default=yael.count_cpu(),
        help="number of processes to launch.")
    parser.add_argument(
        '--redo', default=False, action='store_true',
        help="recompute Fisher vectors even if they already exist.")
    parser.add_argument(
        '-v', '--verbose', action='count', help="verbosity level.")

    args = parser.parse_args()

    # Dynamically import the configuration instance.
    dataset = import_dataset(*args.dataset.split('.'))

    # Check dataset parameters.
    if dataset.descriptor_type not in VALID_DESCRIPTORS:
        print "Descriptor type %s is unknown." % dataset.descriptor_type
        sys.exit(1)

    if any([model not in VALID_MODELS for model in dataset.models]):
        print "The models are unknown."
        sys.exit(1)

    samples = dataset.get_samples()
    bb, ee = correct_idxs(args.begin_idx, args.end_idx, 0, len(samples))

    pca = load_pca(dataset.pca_path)
    gmm = load_gmm(dataset.gmm_path)

    def worker(sample):
        video_path = dataset.get_video_path(sample)
        def get_feature_path(**kwargs):
            return dataset.get_feature_path(sample, **kwargs)
        return compute_features(
            video_path, get_feature_path, dataset.descriptor_type, pca,
            gmm, redo=args.redo, models=dataset.models, spms=dataset.spms,
            verbose=args.verbose)

    RunOnSet(args.nr_processes, samples[bb: ee], worker)


if __name__ == '__main__':
    main()

