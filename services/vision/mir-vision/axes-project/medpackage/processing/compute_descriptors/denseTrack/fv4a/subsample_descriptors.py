
import argparse
import numpy as np
import os
import random
import sys

from yael import yael
from yael.threads import RunOnSet

from fisher_vector import NR_DESCRIPTORS
from fisher_vector import DESC_SIZE
from fisher_vector import compute_descriptors
from fisher_vector import correct_idxs

from utils import import_dataset


def extract_subset_of_descriptors(
    video_path, out_path, descriptor_type, proportion_descs, redo=False,
    verbose=0):
    """Extract random descriptors from the video."""

    if not redo and os.path.exists(out_path):
        return

    if verbose > 0: print "Working on", video_path

    nr_selected_descriptors = int(proportion_descs * NR_DESCRIPTORS)
    all_descriptors = []

    for _, descriptors in compute_descriptors(video_path, descriptor_type):

        nr_descriptors = descriptors.shape[0]

        if nr_descriptors < nr_selected_descriptors:
            all_descriptors.append(descriptors)
        else:
            random_idxs = random.sample(
                xrange(nr_descriptors), nr_selected_descriptors)
            all_descriptors.append(descriptors[random_idxs])

    np.vstack(all_descriptors).astype(np.float32).tofile(out_path)


def merge(
    subset_path, descriptors_path, subset_size, descriptor_type, verbose=0):

    def get_full_path(file_name):
        return os.path.join(descriptors_path, file_name)

    desc_size = DESC_SIZE[descriptor_type]
    all_descs = np.vstack([
        np.fromfile(ff, dtype=np.float32).reshape(-1, desc_size)
        for ff in map(get_full_path, os.listdir(descriptors_path))])

    nr_descriptors = all_descs.shape[0]
    if nr_descriptors >= subset_size:
        random_idxs = random.sample(xrange(nr_descriptors), subset_size)
        all_descs = all_descs[random_idxs]

    all_descs.tofile(subset_path)


def main():
    parser = argparse.ArgumentParser(
        description="Extracts a subset of low-level descriptors for "
        "PCA and GMM learning.")

    # Arguments for the `config` command.
    parser.add_argument(
        '-t', '--task', choices=['compute', 'merge'],
        help="what to do: extract low-level descriptors `compute` or merge "
        "existing low-level descriptors `merge`.")
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
        '-p', '--proportion_of_descriptors', type=float, default=0.001,
        help="proportion of kept descriptors.")
    parser.add_argument(
        '-ss', '--subsample', type=int, default=10,
        help="selects one out of `ss` videos.")
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
    
    if not 0 < args.proportion_of_descriptors < 1:
        print "The proportion of kept descriptors should be between 0 and 1."
        sys.exit(1)

    if args.task == 'compute':
        samples = dataset.get_samples(['train'])
        bb, ee = correct_idxs(args.begin_idx, args.end_idx, 0, len(samples))
        def worker(sample):
            video_path = dataset.get_video_path(sample)
            out_path = dataset.get_descriptor_path(sample)
            return extract_subset_of_descriptors(
                video_path, out_path, dataset.descriptor_type,
                args.proportion_of_descriptors, redo=args.redo,
                verbose=args.verbose)
        RunOnSet(args.nr_processes, samples[bb: ee: args.subsample], worker)
    elif args.task == 'merge':
        merge(
            dataset.subset_path, dataset.descriptor_base_path,
            dataset.subset_size, dataset.descriptor_type, verbose=args.verbose)


if __name__ == '__main__':
    main()

