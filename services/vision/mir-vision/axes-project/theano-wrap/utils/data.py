import numpy as np
import theano
import pandas as pd

import os
import skimage.io
import skimage.color
from skimage.transform import resize
import math

import h5py

from multiprocessing import Process, Queue
#import threading
#from Queue import Queue

import logging
log = logging.getLogger('data')

class BatchLoader(object):

    def __init__(self, input_file, input_dir, imshape=(256,256),
                 mean_impath=None, greyscale=False):

        self.input_dir = input_dir
        self.imshape = imshape
        self.greyscale = greyscale

        self.mean_im = None
        if mean_impath is not None:
            self.mean_im = skimage.io.imread(mean_impath)
            assert(self.mean_im.shape[0] == imshape[0])
            assert(self.mean_im_shape[1] == imshape[1])

        # preload image paths from gt file
        # self.images = []
        # with open(input_file) as f:
        #     for line in f:
        #         line = line.rstrip()
        #         parts = line.split('\t')

        #         self.images.append((parts[0], parts[1]))

        print input_file
        self.df = pd.read_csv(input_file, sep=',',
                              names=['label'])

        self.num_classes = pd.unique(self.df['label']).size

    def batch_gen(self, sz, shuffle=False, async=False):

        # define number of batches to pre-load when using async mode
        async_num_cached = 50

        batch_count = int(math.ceil(len(self.df) / float(sz)))

        idxs = range(len(self.df))
        if shuffle:
            idxs = list(np.random.permutation(idxs))

        if self.greyscale:
            ch_count = 1
        else:
            ch_count = 3

        x_shape = (sz, ch_count, self.imshape[0], self.imshape[1])
        y_shape = (sz,)

        # batch generation function

        def produce_batch(batch_num):
            batch_start = batch_num*sz
            batch_end = batch_start + sz
            if batch_end > len(self.df):
                batch_end = len(self.df)

            x_chunk = np.zeros(x_shape, dtype=theano.config.floatX)
            y_chunk = np.zeros(y_shape, dtype=np.int32)
            paths = []

            for batch_i in range(batch_start, batch_end):
                image_ifo = self.df.iloc[idxs[batch_i]]

                #print x_chunk.shape
                im = self.load_image(os.path.join(self.input_dir,
                                                  image_ifo.name))
                #print im.shape
                x_chunk[batch_i - batch_start] = im
                y_chunk[batch_i - batch_start] = image_ifo['label']
                paths.append(image_ifo.name)

                #impath, label = self.images[batch_i]
                #
                #x_chunk[batch_i] = self.load_image(impath)
                #y_chunk[batch_i] = label
                #paths.append(impath)

            return x_chunk, y_chunk, paths

        # produce batches, either synchronously or asynchronously

        if not async:

            for batch_num in range(batch_count):
                yield produce_batch(batch_num)

        else:

            # initialize job queue
            queue = Queue(maxsize=async_num_cached)
            end_marker = 'end'

            def producer():

                for batch_num in range(batch_count):
                    x_chunk, y_chunk, paths = produce_batch(batch_num)
                    queue.put((x_chunk, y_chunk, paths))

                queue.put(end_marker)

            # start producer
            proc = Process(target=producer)
            proc.daemon = True
            proc.start()

            # run as consumer
            item = queue.get()
            while item != end_marker:
                yield item
                item = queue.get()

    def load_image(self, impath, normalize=False):

        if os.path.splitext(impath)[1] == '.npz':
            im = np.load(impath)['im']

        else:

            im = skimage.io.imread(impath)
            if normalize:
                im = im / 255.0

            im = resize(im, self.imshape, mode='nearest')

        if self.mean_im is not None:
            im -= self.mean_im

        # shuffle from (W,H,3) to (3,W,H)
        if not self.greyscale:
            im = np.swapaxes(im,0,2)
            im = np.swapaxes(im,1,2)
        else:
            if im.ndim == 3:
                im = skimage.color.rgb2grey(im)

        return im

##

class ChunkBatchLoader(object):

    def __init__(self, index_file, input_dir, mean_impath=None):

        self.input_dir = input_dir

        self.mean_im = None
        if mean_impath is not None:
            self.mean_im = skimage.io.imread(mean_impath)
            assert(self.mean_im.shape[0] == imshape[0])
            assert(self.mean_im_shape[1] == imshape[1])

        self.chunk_paths = []
        self.dset_sz = 0
        self.chunk_sz = 0
        self.num_classes = 0
        with open(index_file) as f:

            self.dset_sz = int(f.readline().rstrip())
            self.chunk_sz = int(f.readline().rstrip())
            self.num_classes = int(f.readline().rstrip())

            for line in f:
                line = line.rstrip()

                self.chunk_paths.append(line)

    def batch_gen(self, sz, shuffle=False, async=False):

        if async:
            # async chunk loading not implemented, as probably wouldn't speed things up
            # appreciably at all
            log.warning('async batch loading not implemented for chunks - ignoring async flag')

        #batch_count = int(math.ceil(self.dset_sz / float(sz)))
        if self.chunk_sz % sz != 0:
            print self.chunk_sz
            print sz
            print self.chunk_sz % sz
            raise RuntimeError('chunk_sz should be multiple of batch_sz')
        else:
            batches_per_chunk = self.chunk_sz / sz

        chunk_idxs = range(len(self.chunk_paths))
        if shuffle:
            idxs = list(np.random.permutation(chunk_idxs))

        for chunk_num in range(len(self.chunk_paths)):

            f_ch = h5py.File(os.path.join(self.input_dir,
                                          self.chunk_paths[chunk_idxs[chunk_num]]))

            for batch_num in range(batches_per_chunk):

                batch_start = batch_num*sz
                batch_end = (batch_num+1)*sz

                chunk_end = False
                if batch_end > f_ch['labels'].size:
                    batch_end = f_ch['labels'].size
                    chunk_end = True

                x_chunk = np.array(f_ch['imgs'][batch_start:batch_end,:,:,:])
                y_chunk = np.array(f_ch['labels'][batch_start:batch_end])
                paths = np.array(f_ch['paths'])

                yield x_chunk, y_chunk, paths

                if chunk_end:
                    break

    def batch_gen_async(self, sz, shuffle=False):
        raise RuntimeError('Not implemented!')
