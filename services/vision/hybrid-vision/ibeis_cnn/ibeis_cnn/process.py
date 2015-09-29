#!/usr/bin/env python
"""

"""
from __future__ import absolute_import, division, print_function
from detecttools.directory import Directory
from os.path import join, abspath, exists, basename
import utool as ut
import cv2
import numpy as np


def process_image_directory(project_name, size, reset=True):
    # Raw folders
    raw_path = abspath(join('..', 'data', 'raw'))
    processed_path = abspath(join('..', 'data', 'processed'))
    # Project folders
    project_raw_path = join(raw_path, project_name)
    project_processed_path = join(processed_path, project_name)

    # Load raw data
    direct = Directory(project_raw_path, include_extensions='images')

    # Reset / create paths if not exist
    if exists(project_processed_path) and reset:
        ut.remove_dirs(project_processed_path)
    ut.ensuredir(project_processed_path)

    # Process by resizing the images into the desired shape
    for file_path in direct.files():
        file_name = basename(file_path)
        print('Processing %r' % (file_name, ))
        image = cv2.imread(file_path)
        image = cv2.resize(image, size, interpolation=cv2.INTER_LANCZOS4)
        dest_path = join(project_processed_path, file_name)
        cv2.imwrite(dest_path, image)


def numpy_processed_directory(project_name, numpy_ids_file_name='ids.npy',
                              numpy_x_file_name='X.npy',
                              numpy_y_file_name='y.npy', labels_file_name='labels.csv',
                              reset=True):
    # Raw folders
    processed_path = abspath(join('..', 'data', 'processed'))
    labels_path = abspath(join('..', 'data', 'labels'))
    numpy_path = abspath(join('..', 'data', 'numpy'))
    # Project folders
    project_processed_path = join(processed_path, project_name)
    project_labels_path = join(labels_path, project_name)
    project_numpy_path = join(numpy_path, project_name)
    # Project files
    project_numpy_ids_file_name = join(project_numpy_path, numpy_ids_file_name)
    project_numpy_x_file_name = join(project_numpy_path, numpy_x_file_name)
    project_numpy_y_file_name = join(project_numpy_path, numpy_y_file_name)
    project_numpy_labels_file_name = join(project_labels_path, labels_file_name)

    # Load raw data
    direct = Directory(project_processed_path, include_extensions='images')
    label_dict = {}
    for line in open(project_numpy_labels_file_name):
        line = line.strip().split(',')
        file_name = line[0].strip()
        label = line[1].strip()
        label_dict[file_name] = label

    # Reset / create paths if not exist
    if exists(project_numpy_path) and reset:
        ut.remove_dirs(project_numpy_path)
    ut.ensuredir(project_numpy_path)

    # Get shape for all images
    shape_x = list(cv2.imread(direct.files()[0]).shape)
    if len(shape_x) == 2:
        shape_x = shape_x + [1]
    shape_x = tuple([len(direct.files())] + shape_x[::-1])  # NOQA
    shape_y = shape_x[0:1]  # NOQA

    # Create numpy arrays
    # X = np.empty(shape_x, dtype=np.uint8)
    # y = np.empty(shape_y, dtype=np.uint8)
    ids = []
    X = []
    y = []

    # Process by loading images into the numpy array for saving
    for index, file_path in enumerate(direct.files()):
        file_name = basename(file_path)
        print('Processing %r' % (file_name, ))
        image = cv2.imread(file_path)
        try:
            label = label_dict[file_name]
            # X[index] = np.array(cv2.split(image))
            # y[index] = label
            # X.append(np.array(cv2.split(image)))  # Lasange format
            ids.append(file_name)
            X.append(image)  # cv2 format
            y.append(label)
        except KeyError:
            print('Cannot find label...skipping')
            # raw_input()

    ids = np.array(ids)
    X = np.array(X, dtype=np.uint8)
    # y = np.array(y, dtype=np.uint8)
    y = np.array(y)

    # Save numpy array
    print('  ids.shape = %r' % (ids.shape,))
    print('  ids.dtype = %r' % (ids.dtype,))
    print('  X.shape   = %r' % (X.shape,))
    print('  X.dtype   = %r' % (X.dtype,))
    print('  y.shape   = %r' % (y.shape,))
    print('  y.dtype   = %r' % (y.dtype,))
    np.save(project_numpy_ids_file_name, ids)
    np.save(project_numpy_x_file_name, X)
    np.save(project_numpy_y_file_name, y)


def view_numpy_data(project_namel, numpy_x_file_name='X.npy', numpy_y_file_name='y.npy'):
    # Raw folders
    numpy_path = abspath(join('..', 'data', 'numpy'))
    # Project folders
    project_numpy_path = join(numpy_path, project_name)
    # Project files
    project_numpy_x_file_name = join(project_numpy_path, numpy_x_file_name)
    project_numpy_y_file_name = join(project_numpy_path, numpy_y_file_name)

    X = np.load(project_numpy_x_file_name)
    y = np.load(project_numpy_y_file_name)

    print('  X.shape = %r' % (X.shape,))
    print('  X.dtype = %r' % (X.dtype,))
    print('  y.shape = %r' % (y.shape,))
    print('  y.dtype = %r' % (y.dtype,))

    image = X[1]
    # image = cv2.merge(image)  # Lasagne format conversion
    cv2.imshow('', image)
    cv2.waitKey(0)


if __name__ == '__main__':
    # project_name = 'viewpoint_large'
    # # size = (96, 96)
    # # process_image_directory(project_name, size)
    # numpy_processed_directory(project_name)

    project_name = 'viewpoint_pz'
    # size = (64, 64)
    # process_image_directory(project_name, size)
    numpy_processed_directory(project_name)

    project_name = 'quality_pz'
    # size = (64, 64)
    # process_image_directory(project_name, size)
    numpy_processed_directory(project_name)

    # view_numpy_data(project_name)
