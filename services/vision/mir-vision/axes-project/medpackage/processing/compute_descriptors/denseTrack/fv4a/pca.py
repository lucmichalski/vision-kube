"""Functions that deal with the PCA computation for a set of descriptors."""
import argparse
import cPickle as pickle

from sklearn.decomposition import PCA
from sklearn.preprocessing import StandardScaler

from utils import import_dataset


def compute_pca(data, n_components):
    """Computes PCA on a subset of nr_samples of descriptors."""
    pca = PCA(n_components=n_components)
    pca.fit(data)
    return pca


def save_pca(pca, filename):
    """Pickles a PCA object to the specified filename."""
    with open(filename, 'w') as ff:
        pickle.dump(pca, ff)


def load_pca(filename):
    """Loads PCA object from file using cPickle."""
    with open(filename, 'r') as ff:
        pca = pickle.load(ff)
        return pca


def compute_pca_given_dataset(dataset_name):
    # Dynamically import the configuration instance.
    dataset = import_dataset(*dataset_name.split('.'))

    data = dataset.load_subset()
    n_components = dataset.nr_pca_components

    if n_components > 0:
        pca = compute_pca(data, n_components)
    else:
        scaler = StandardScaler(with_std=False)
        pca = scaler.fit(data)

    save_pca(pca, dataset.pca_path)


def main():
    parser = argparse.ArgumentParser(
        description="Computes PCA on a subset of descriptors.")

    parser.add_argument(
        '-d', '--dataset', required=True,
        help="name of the dataset configuration file.")

    args = parser.parse_args()
    compute_pca_given_dataset(args.dataset)


if __name__ == '__main__':
    main()

