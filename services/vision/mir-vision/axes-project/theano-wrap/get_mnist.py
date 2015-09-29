import os
import numpy as np
from urllib import urlretrieve
import cPickle as pickle
import gzip
import skimage.io
import math

import h5py

# options --

opts = dict(
    url='http://deeplearning.net/data/mnist/mnist.pkl.gz',
    filename='input/mnist.pkl.gz',
    output_dir='input/mnist',
    output_type='h5py' # either 'imgs' or 'h5py'
)

##

def download_mnist(url, fname):

    if not os.path.exists(fname):

        path = os.path.split(fname)[0]
        if not os.path.exists(path):
            os.makedirs(path)

        print("Downloading MNIST dataset...")
        urlretrieve(url, fname)

def load_mnist_data(fname):

    with gzip.open(fname, 'rb') as f:
        data = pickle.load(f)

    X_train, y_train = data[0]
    X_val, y_val = data[1]
    X_test, y_test = data[2]

    # The inputs come as vectors, we reshape them to monochrome 2D images,
    # according to the shape convention: (examples, channels, rows, columns)
    X_train = X_train.reshape((-1, 1, 28, 28))
    X_val = X_val.reshape((-1, 1, 28, 28))
    X_test = X_test.reshape((-1, 1, 28, 28))

    # The targets are int64, we cast them to int8 for GPU compatibility.
    y_train = y_train.astype(np.uint8)
    y_val = y_val.astype(np.uint8)
    y_test = y_test.astype(np.uint8)

    print X_train.shape
    print y_train.shape
    print X_val.shape
    print y_val.shape
    print X_test.shape
    print y_test.shape

    return dict(
        X_train=X_train,
        y_train=y_train,
        X_val=X_val,
        y_val=y_val,
        X_test=X_test,
        y_test=y_test
    )


def save_imgs(output_dir, sets, mnist_data):

    for set in sets:
        print 'Processing %s...' % set
        set_dir = os.path.join(output_dir, set)

        if not os.path.exists(set_dir):
            os.makedirs(set_dir)

        if set == 'train':
            X = mnist_data['X_train']
            y = mnist_data['y_train']
        elif set == 'val':
            X = mnist_data['X_val']
            y = mnist_data['y_val']
        elif set == 'test':
            X = mnist_data['X_test']
            y = mnist_data['y_test']

        with open(os.path.join(output_dir, '%s.txt' % set), 'w') as f:
            for i in range(X.shape[0]):
                fname = '%d.npz' % i
                path = os.path.join(set_dir, fname)
                im = np.squeeze(X[i, :, :, :])
                np.savez(path, im=np.squeeze(X[i, :, :, :]))
                #skimage.io.imsave(path, np.squeeze(X[i, :, :, :]))

                f.write('%s,%d\n' % (fname, y[i]))


def save_h5py(output_dir, sets, chunk_sz, mnist_data):

    # format of output hdf5 file:
    #
    # paths  [N]       array of strings of image paths
    # imgs   [Nx1xDxD] matrix of images
    # labels [N]       array of int32 labels

    for set in sets:
        print 'Processing %s...' % set

        if set == 'train':
            X = mnist_data['X_train']
            y = mnist_data['y_train']
        elif set == 'val':
            X = mnist_data['X_val']
            y = mnist_data['y_val']
        elif set == 'test':
            X = mnist_data['X_test']
            y = mnist_data['y_test']

        chunk_index_path = os.path.join(output_dir, '%s_index.txt' % set)
        chunk_count = int(math.ceil(X.shape[0] / float(chunk_sz)))

        dt = h5py.special_dtype(vlen=str)

        with open(chunk_index_path, 'w') as f:

            f.write('%d\n' % X.shape[0])
            f.write('%d\n' % chunk_sz)
            f.write('%d\n' % 10)

            for chunk_num in range(chunk_count):
                chunk_start = chunk_num*chunk_sz
                chunk_end = (chunk_num + 1)*chunk_sz
                if chunk_end > X.shape[0]:
                    chunk_end = X.shape[0]
                assert(chunk_end > chunk_start)
                chunk_actual_sz = chunk_end - chunk_start

                chunk_fname_path = '%s_%d.h5' % (set, chunk_num+1)
                chunk_fname_path_full = os.path.join(output_dir, chunk_fname_path)

                f.write('%s\n' % chunk_fname_path)

                f_ch = h5py.File(chunk_fname_path_full)

                paths = f_ch.create_dataset('paths', (chunk_actual_sz,), dtype=dt)
                for idx, i in enumerate(range(chunk_start, chunk_end)):
                    paths[idx] = '%d.image' % i

                imgs = f_ch.create_dataset('imgs', (chunk_actual_sz, 1,
                                                    X.shape[2],
                                                    X.shape[3]), dtype=X.dtype)
                imgs[:,:,:,:] = X[chunk_start:chunk_end, :, :, :]

                labels = f_ch.create_dataset('labels', (chunk_actual_sz,), dtype=np.int32)
                labels[:] = y[chunk_start:chunk_end]

                f_ch.close()


def main(opts):

    download_mnist(opts['url'], opts['filename'])
    mnist_data = load_mnist_data(opts['filename'])

    chunk_sz = 500*10

    if not os.path.exists(opts['output_dir']):
        os.makedirs(opts['output_dir'])
    sets = ['train', 'val', 'test']

    if opts['output_type'] == 'imgs':
        save_imgs(opts['output_dir'], sets, mnist_data)
    elif opts['output_type'] == 'h5py':
        save_h5py(opts['output_dir'], sets, chunk_sz, mnist_data)
    else:
        raise RuntimeError('Unknown output_type')


if __name__ == '__main__':

    main(opts)
