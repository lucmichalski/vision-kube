
"""Extracts Fisher vectors and spatial Fisher vectors spatial pyramids based on
the stabilized dense trajectories (HOG, HOF, MBH)."""

import argparse
import cPickle
from itertools import product
import numpy as np
import pdb
import os
import subprocess
import sys

from yael import yael
from yael import ynumpy

MED_BASEDIR=os.getenv("MED_BASEDIR")
#sys.path.insert(0, MED_BASEDIR+'usr/fv4a')
sys.path.insert(0, MED_BASEDIR+'compute_descriptors/denseTrack/fv4a')
from fisher_vector import descriptors_to_fisher_vector
from fisher_vector import descriptors_to_spatial_fisher_vector
from utils import grouper
from utils import get_video_infos
from utils import regular_multidim_digitize


# Number of descriptors that are read at once from DenseTrack.
NR_DESCRIPTORS = 10000
#DENSE_TRACK = '/home/lear/oneata/experiments/trecvid14/DenseTrack_ijcv14_stab_320'
#DENSE_TRACK = MED_BASEDIR + 'usr/bin/DenseTrackStab'
DENSE_TRACK = 'DenseTrackStab'

DESCRIPTOR_TYPES = ['hog', 'hof', 'mbhx', 'mbhy']
DESC_SIZE = {
    'hog':  96,
    'hof': 108,
    'mbhx': 96,
    'mbhy': 96,
}


# Set ranges for the HOG, HOF, MBH descriptors.
start_from = 40
DESC_IDXS = {}
for desc in 'hog', 'hof', 'mbhx', 'mbhy':
    DESC_IDXS[desc] = range(start_from, start_from + DESC_SIZE[desc]) 
    start_from = start_from + DESC_SIZE[desc]


SPLITS = (
    'train',
    'validation',
    'background',
    'test')


# Parameters for the Fisher vectors and spatial Fisher vectors.
MODELS = {
    'FV': {
        'full_name': 'fisher_vectors',
        'descs_to_features': descriptors_to_fisher_vector,
        'get_feature_dim': lambda gmm: gmm.k + 2 * gmm.d * gmm.k,
    },
    'SFV': {
        'full_name': 'spatial_fisher_vectors',
        'descs_to_features': descriptors_to_spatial_fisher_vector,
        'get_feature_dim': lambda gmm: 2 * 3 * gmm.k,
    },
}

def parse_scenecutfile(scenecutfile):
    """Reads scenecutfile, returns list with shot boundaries."""
    f = open(scenecutfile)
    l = f.readline() # first line: number of shots
    l = f.readline() # second line: shot boundaries
    shots = l.split()
    
    for i in range(len(shots)):
        shots[i] = int(shots[i])
    
    f.close()
    return shots

def compute_descriptors(infile, descriptor_types):
    """Reads low-level descriptors from DenseTracks."""

    LEN_LINE = 436

    POS_IDXS = [1, 2, 0]        # Position coordinates (X, Y, T).
    NORM_POS_IDXS = [7, 8, 9]   # Normalized position coordinates (X, Y, T).

    dense_tracks = subprocess.Popen(
        [DENSE_TRACK, infile],
        stdout=subprocess.PIPE)

    for lines in grouper(dense_tracks.stdout, NR_DESCRIPTORS):
        all_descs = np.vstack([
            map(float, line.split())
            for line in lines
            if line is not None]
        ).astype(np.float32)

        assert all_descs.shape[0] <= NR_DESCRIPTORS
        assert all_descs.shape[1] == LEN_LINE

        positions = all_descs[:, POS_IDXS]
        normalized_positions = all_descs[:, NORM_POS_IDXS]
        descriptors = {
            desc_type: all_descs[:, DESC_IDXS[desc_type]]
            for desc_type in descriptor_types}

        yield positions, normalized_positions, descriptors


def compute_features(
    video_path, descriptor_types, pcas, gmms, models, spm,
    slices, verbose=0):
    """Computes Fisher vectors and/or spatial Fisher vectors."""
    

    # Helper functions.
    def apply_pca(X, pca):

        # Whitening data.
        X -= pca['mean']
        X /= pca['var']

        X = np.dot(X, pca['pca'])
        X = np.array(X).astype(np.float32)

        return X

    def preallocate_array(nr_slices, Ks, _1d=False):
        return {
            kk: {
                desc_type: {
                    model: np.zeros((
                        nr_slices,
                        nr_bins,
                        1 if _1d
                        else MODELS[model]['get_feature_dim'](gmms[kk][desc_type])),
                        dtype=np.float32)
                    for model in models}
                for desc_type in descriptor_types}
            for kk in Ks}

    def slicer(T):
        # The usual trajectory length is 15 frames; but, in this case, we are
        # skipping each second frame so the actual trajectory length is 30
        # frames.
        TRACK_LEN = 15 * 2
        #assert os.path.basename(DENSE_TRACK) == 'DenseTrack_ijcv14_stab_320'
        #slices = np.arange(slice_size, nr_frames, slice_size)
        
        # `T` are the end frames of the trajectories. Subtract `TRACK_LEN` to
        # obtain the beginning of the trajectory.
        return np.digitize(T - TRACK_LEN, slices)

    nr_bins = np.prod(spm) 
    #nr_slices = int(np.ceil(float(nr_frames) / slice_size))
    nr_slices = len(slices) + 1

    Ks = sorted(gmms.keys())

    features = preallocate_array(nr_slices, Ks)
    nr_descriptors = preallocate_array(nr_slices, Ks, _1d=True)

    if verbose > 0:
        print "Working on video", video_path

    for positions, normalized_positions, descriptors in compute_descriptors(
        video_path, descriptor_types):

        time_indices = slicer(positions[:, 2])
        spm_indices = regular_multidim_digitize(
            normalized_positions, spm, (0, 0, 0), (1, 1, 1))

        for desc_type in descriptor_types:

            pca_descriptors = apply_pca(
                descriptors[desc_type], pcas[desc_type])

            for ti, si in product(xrange(nr_slices), xrange(nr_bins)):

                # Find subset of descriptors.
                idxs = np.logical_and(time_indices == ti, spm_indices == si)
                N = sum(idxs)

                if N == 0:
                    continue

                for kk, model in product(Ks, models):

                    features[kk][desc_type][model][ti, si] += (
                        N *  # Undo normalization
                        MODELS[model]['descs_to_features'](
                            pca_descriptors[idxs],
                            gmm=gmms[kk][desc_type],
                            ll=normalized_positions[idxs]))
                    nr_descriptors[kk][desc_type][model][ti, si] += N

    emptyShot = False
    # Normalize by the number of descriptors.
    for kk, desc_type, model in product(Ks, descriptor_types, models):
        """
        print "-----------"
        print "kk:", kk, "; desc_type:", desc_type, "model:", model
        
        print "nr_descriptors[kk][desc_type][model] =", nr_descriptors[kk][desc_type][model]
        print "features[kk][desc_type][model] =", features[kk][desc_type][model]
        """
        
        if nr_descriptors[kk][desc_type][model][0][0][0] > 0.000001 or nr_descriptors[kk][desc_type][model][0][0][0] < -0.000001:
            features[kk][desc_type][model] /= nr_descriptors[kk][desc_type][model]
        else:
            emptyShot = True
        
        # Replace NaN's with zeros.
        nan_idxs = np.isnan(features[kk][desc_type][model])
        features[kk][desc_type][model][nan_idxs] = 0
        
    if emptyShot:
        sys.stderr.write("Empty shot detected\n")

    return features, nr_descriptors


def save_features(
    features, nr_descriptors, video_name, feature_path, descriptor_types,
    models, what_to_save):

    def aggregate(X, N, axis):
        Nagg = N.sum(axis=axis)
        Xagg = (X * N).sum(axis=axis) / Nagg
        Xagg[np.isnan(Xagg)] = 0  # Avoid NaNs.
        return Xagg, Nagg

    def transform(X, N, aggregate_slices):
        return aggregate(X, N, axis=(0, )) if aggregate_slices else (X, N)

    for kk, desc_type, model, video_or_slice in product(
        sorted(features.keys()),
        descriptor_types,
        models,
        what_to_save):

        X, N = transform(
            features[kk][desc_type][model],
            nr_descriptors[kk][desc_type][model],
            aggregate_slices=(video_or_slice == 'video'))

        expanded_feature_path = feature_path % { 'descriptor': desc_type, 'model': MODELS[model]['full_name'], 'K': kk, 'slice_or_video': video_or_slice, 'video_name': video_name}
        
        # Save descriptors.
        ynumpy.fvecs_write(
            expanded_feature_path,
            np.reshape(X, (-1, X.shape[-1])))

        """
        # Save counts.
        ynumpy.fvecs_write(
            feature_path % {
                'descriptor': desc_type,
                'model': MODELS[model]['full_name'],
                'K': kk,
                'slice_or_video': video_or_slice,
                'video_name': video_name + '_counts'},
            np.reshape(N, (-1, 1)))
        """

def main():

    def all_files_exist(feature_path, video_name, what_to_save):
        return np.all([
            os.path.exists(
                feature_path % {
                    'descriptor': desc_type,
                    'model': MODELS[model]['full_name'],
                    'K': kk,
                    'slice_or_video': slice_or_video,
                    'video_name': video_name})
            for kk in Ks
            for desc_type in DESCRIPTOR_TYPES
            for slice_or_video in what_to_save
            for model in MODELS.keys()])
    """
    def get_nr_frames(filelist_path, video_name):
        with open(filelist_path, 'r') as ff:
            for line in ff.readlines():
                if line.startswith(video_name):
                    sample, _ = line.split()
                    return int(sample.split('-')[-1])
            assert False, "Could not find the number of frames for video %s" % video_name
    """
    parser = argparse.ArgumentParser(description=__doc__)

    parser.add_argument('--video', help="video name")
    parser.add_argument('--videodir', default=['videos'], help="video directory (default: 'videos')")
    parser.add_argument('-k', '--nr_clusters', default=['64'], nargs='+', help="number of GMM clusters.")
    parser.add_argument('-s', '--split', choices=SPLITS, default='train', help="which split of the data to use.")
    parser.add_argument('--redo', default=False, action='store_true')
    parser.add_argument('--h3spm', default=False, action='store_true', help="use H3 spatial pyramid.")
    parser.add_argument('--save', default=['video'], nargs='+', choices=('video', 'slice'), help="what to store: video descriptor or slice descriptors.")
    parser.add_argument('--slice', default=None, type=int, help="slice size (in number of frames).")
    parser.add_argument('--scenecut', default=None, help="Full path to scenecut file, contains shot boundaries.")
    parser.add_argument('--featurepath', default=None, help="Full path to where features are saved.")
    parser.add_argument('-v', '--verbose', action='count', help="verbosity level.")

    args = parser.parse_args()
    
    if args.scenecut and (not os.path.exists(args.scenecut)):
        print "Scenecut file '%s' does not exist" % args.scenecut
        sys.exit(1)

    #BASEPATH = "/home/lear/oneata/data/thumos14"
    PCA = MED_BASEDIR + "compute_descriptors/denseTrack/vocab/MED15/pca/%s/features_pca_k256.npz"
    GMM = MED_BASEDIR + "compute_descriptors/denseTrack/vocab/MED15/gmm/%s/features_k256.gmm"

    #video_path = os.path.join(BASEPATH, 'videos', args.split, args.video)
    video_path = os.path.join(
                args.videodir,
                args.video)
    #filelist_path = os.path.join(BASEPATH, 'filelists', '%s.list' % args.split)
    """
    feature_path = os.path.join(
        BASEPATH,
        'features',
        'dense5.track15%(descriptor)s.stab.320px',
        '%(model)s_k_%(K)d',
        '%(slice_or_video)s_descriptors' + ('_h3spm' if args.h3spm else ''),
        '%(video_name)s.fvecs')
    """
    if args.featurepath == None:
        print "Feature path not specified"
        sys.exit(1)
        
    feature_path = os.path.join(
        args.featurepath,
        '%(descriptor)s_%(model)s_k_%(K)d_descriptor.fvecs'
    )
    """
    feature_path = os.path.join(
        'processing',
        'videos_workdir',
        '%(video_name)s',
        '%(descriptor)s_%(model)s_k_%(K)d_descriptor.fvecs'
    )
    """
    print "Video path:", video_path
    print "Feature path:", feature_path    
    
    assert (args.slice is None) == ('slice' not in args.save), "Use either both the `--slice` and `--save slice` or none of them."

    Ks = map(int, args.nr_clusters)
    spm = (1, 3, 1) if args.h3spm else (1, 1, 1)
    slice_size = args.slice or 1e5

    what_to_save = [
        ('slice_%d' % slice_size if sv == 'slice' else sv)
        for sv in args.save]

    # Skip the work if all the feature files already exist.
    if not args.redo and all_files_exist(feature_path, args.video, what_to_save):
        if args.verbose > 0:
            print "All feature file exist for video", args.video
        sys.exit(0)

    # Load PCA files.
    pcas = {
        desc_type: np.load(PCA % desc_type)
        for desc_type in DESCRIPTOR_TYPES}

    # Load GMM files.
    gmms = {
        kk: {
            desc_type: yael.gmm_read(open(GMM % (desc_type), 'r'))
            for desc_type in DESCRIPTOR_TYPES}
        for kk in Ks}

    #nr_frames = get_nr_frames(filelist_path, args.video)
    #if args.verbose > 2:
    #    print "Video has %d frames." % nr_frames
        
    if args.scenecut:
        slices = parse_scenecutfile(args.scenecut)
    else:
        slices = []
    #slices = np.array([150])
    
    features, nr_descriptors = compute_features(
        video_path, DESCRIPTOR_TYPES, pcas, gmms, MODELS.keys(),
        spm, slices, verbose=args.verbose)

    save_features(
        features, nr_descriptors, args.video, feature_path, DESCRIPTOR_TYPES,
        MODELS.keys(), what_to_save)


if __name__ == '__main__':
    main()

