""" Functions that deal with the GMM computation for a set of descriptors. """
import argparse
import numpy as np

from yael import yael
from yael.yael import numpy_to_fvec_ref, gmm_learn, gmm_read, gmm_write
from yael.yael import GMM_FLAGS_W

from pca import load_pca
from utils import import_dataset


def compute_gmm(data, nr_clusters, nr_iterations, nr_threads, seed, nr_redos):
    """Computes GMM using yael functions."""
    N, D = data.shape
    data = np.ascontiguousarray(data)
    return gmm_learn(
        D, N, nr_clusters, nr_iterations, numpy_to_fvec_ref(data), nr_threads,
        seed, nr_redos, GMM_FLAGS_W)


def save_gmm(gmm, filename):
    """Saves the GMM object to file using yael."""
    with open(filename, 'w') as _file:
        gmm_write(gmm, _file)


def load_gmm(filename):
    """Loads GMM object from file using yael."""
    with open(filename, 'r') as _file:
        gmm = gmm_read(_file)
        return gmm


def compute_gmm_given_dataset(dataset_name, nr_threads):

    SEED = 1
    NR_REDOS = 4
    NR_ITERATIONS = 100

    # Dynamically import the configuration instance.
    dataset = import_dataset(*dataset_name.split('.'))

    data = dataset.load_subset()

    pca = load_pca(dataset.pca_path)
    pca_data = pca.transform(data)

    # Do the computation.
    gmm = compute_gmm(
        pca_data, dataset.nr_clusters, NR_ITERATIONS, nr_threads, SEED,
        NR_REDOS)
    save_gmm(gmm, dataset.gmm_path)


def main():
    parser = argparse.ArgumentParser(
        description="Computes GMM on a subset of descriptors.")

    parser.add_argument(
        '-d', '--dataset', required=True,
        help="name of the dataset configuration file.")
    parser.add_argument(
        '-np', '--nr_processes', type=int, default=yael.count_cpu(),
        help="number of GMM clusters.")

    args = parser.parse_args()
    compute_gmm_given_dataset(args.dataset, args.nr_processes)


if __name__ == '__main__':
    main()

