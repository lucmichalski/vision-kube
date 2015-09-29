
Code for obtaining mid-level representations for videos with the purpose of action classification or detection. For more details, see our [paper](http://hal.inria.fr/hal-00873662/en "Action and Event Recognition with Fisher Vectors on a Compact Feature Set"). The code can compute:

- Fisher vector encoding on top of the [dense trajectories](http://lear.inrialpes.fr/people/wang/dense_trajectories) code
- [Spatial Fisher vectors](http://hal.inria.fr/inria-00612277/PDF/final.r1.pdf "Modeling Spatial Layout with Fisher Vectors for Image Categorization")
- [Spatial pyramid grids](http://hal.inria.fr/docs/00/54/86/47/PDF/pyramid_chapter.pdf "Spatial Pyramid Matching")

Dependencies
------------

The code is written in Python (version 2.7) and depends on the following packages:

- The [Yael](https://gforge.inria.fr/projects/yael/) package (compiled using the `--enable-numpy` option).
- The [NumPy](http://www.numpy.org/) and [scikit-learn](http://scikit-learn.org/stable/) Python packages.
- The [dense trajectories](http://lear.inrialpes.fr/people/wang/dense_trajectories) code. Please make sure there is a symbolic link to the `DenseTrack` executable in the current directory.

License information
-------------------

See the file LICENSE.txt for information on the history of this software, terms & conditions for usage.

Tutorial
--------

Here is an introduction on how the code works.

### Configuration files

In order to use the code, you have to create a configuration file in the `datasets` folder.
The role of the configuration files is to keep track of various parameters (_e.g._, descriptor type, spatial grid) and paths to videos, features, GMM and PCA models.
The configuration files are Python files so that it is easier to define different train-test splits or multiple parameter combinations.
The Python configuration file should define an instance of a class that is able to respond to certain attributes and methods (these are specified below).
To get an idea of what a configuration file should look like, have a look at the example from `datasets` folder, the `hollywood2.py` file.

The instance of the class should provide the following:

- An attribute `descriptor_type` that specifies the type of the descriptor (can be either HOG, HOF or MBH).
- An attribute `models` that specifies the encodings: FV (Fisher vectors), SFV (spatial Fisher vectors); `models` should be a list.
- An attribute `spms` that specifies the spatial pyramids in the format `Nx_Ny_Nt`, where `Nx`, `Ny`, `Nt` are the number of cells on the horizontal, vertical, temporal axis.
- An attribute `nr_pca_components` that specifies the number of dimensions to keep for the PCA projection.
- An attribute `nr_clusters` that specifies the number of GMM clusters.
- An attribute `pca_path` that contains the path to the PCA object location.
- An attribute `gmm_path` that contains the path to the GMM object location.
- An attribute `descriptor_base_path` that specifies the path where the low-level local descriptors are stored.
- An attribute `subset_path` that specifies the path to the subset of low-level local descriptors.
- An attribute `subset_size` that specifies the number of descriptors to use for learning PCA and GMM.
- An attribute `pca_path` that contains the path to the PCA object location.
- An attribute `gmm_path` that contains the path to the GMM object location.
- A method `get_samples()` that returns a list of identifiers of the videos.
- A method `get_feature_path(sample, model, spm)` that takes the identifier of a video `sample` and returns the path to the location of the Fisher vectors.
- A method `get_video_path(sample)` that takes the identifier of a video `sample` returns path to the video file.
- A method `load_subset()` that loads the subset of selected low-level descriptors.

The dataset configuration name is passed to the other scripts in the following format `<filename>.<class_instance>`, _e.g._ `hollywood2.small`.

### Extract a subset of descriptors

In order to train a PCA or GMM model, you need to extract a set of local low-level descriptors.
The script `subsample_descriptors.py` selects a uniform subset of train videos and for each video it saves a small random proportion of low-level descriptors.
For example, the command `python subsample_descriptors.py -t compute -d hollywood2.small -ss 5 -p 0.001` randomly selects a proportion of 0.001 descriptors from one fifth of the training data.
After the descriptors are extracted for the selected train videos, you can merge the low-level descriptors into a single file:
    
    python subsample_descriptors.py -t merge -d hollywood2.small -ss 5 -p 0.001

### Train PCA and GMM

Training the PCA and GMM models is straightforward once the subset of descriptors is extracted:

    python pca.py -d hollywood2.small
    python gmm.py -d hollywood2.small

### Extract Fisher vectors

The primary purpose of the code is extracting Fisher vectors from videos.
This done using the `fisher_vector.py` script; for example:

    python fisher_vector.py -d hollywood2.small -b 0 -e 1 -np 1 -vv

### Example of classification

The code also includes the script `evaluate.py` that can evaluate the extracted features.
You will need your instance to have:

- A method `get_classifier()` that returns an instance that has two methods: `fit(kernel, labels)` and `score(kernel)`.
- A method `get_samples_with_labels()` that returns a `dict` that maps a sample to a label.

Have a look at the `datasets/hollywood2.py`.

Test
----

To check that the Fisher extraction works correctly, here is simple test case.
In order for this to run, create the directories and download the data as indicated below.

    # Create folders.
    mkdir -p data/hollywood2/{videos,filelists,features}
    mkdir -p data/hollywood2/features/dense5.track15mbh/{gmm,pca,statistics_k_50}
    mkdir -p data/hollywood2/features/dense5.track15mbh/statistics_k_50/{fv,sfv}/spm_{111,112,131}

    # Get data:
    # - PCA model
    wget http://pascal.inrialpes.fr/data2/oneata/code/fv4a/data/pca_64 -P data/hollywood2/features/dense5.track15mbh/pca
    # - GMM model
    wget http://pascal.inrialpes.fr/data2/oneata/code/fv4a/data/gmm_50 -P data/hollywood2/features/dense5.track15mbh/gmm
    # - filelists
    wget http://pascal.inrialpes.fr/data2/oneata/code/fv4a/data/{train,test}.list -P data/hollywood2/filelists/
    # - a video
    wget http://pascal.inrialpes.fr/data2/oneata/code/fv4a/data/actioncliptrain00808.avi -P data/hollywood2/videos/

    # Extract FV and SFV for the given video.
    python fisher_vector.py -d hollywood2.small -b 0 -e 1 -np 1 -vv

    # Get the expected descriptors.
    mkdir -p data/hollywood2/features/dense5.track15mbh/expected_statistics_k_50/{fv,sfv}/spm_{111,112,131}
    for m in fv sfv; do \
        for g in 111 112 131; do \
            wget http://pascal.inrialpes.fr/data2/oneata/code/fv4a/data/${m}/spm_${g}/actioncliptrain00808.dat -P data/hollywood2/features/dense5.track15mbh/expected_statistics_k_50/${m}/spm_${g}; \
        done; \
    done

    # Check that the expected descriptors match the given
    nosetests -s tests/check.py


TODO
----

- Make an abstract class `Dataset` from which all other datasets should inherit.
- Add an option to select either the stabilized or non-stabilized version of dense trajectories code.
- Add an option to extract Fisher vectors on temporal slices of video, so that the code is useful also for temporal action localization.
- Remove dependency on Yael.
- Extend code such that it works on multiple descriptor types at the same time.
- Clean the code from `utils.py`.
- ~~Simplify the code for the Fisher vector and spatial Fisher vector in `fisher_vector.py`.~~

