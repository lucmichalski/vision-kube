import os
from nets import mnist
import utils

import logging
logging.basicConfig(level=logging.DEBUG,
                    format='%(asctime)s:%(levelname)s:%(name)s:%(message)s',
                    datefmt='%y-%m-%d %H:%M:%S',
                    filename='make_net.log',
                    filemode='w')
log = logging.getLogger('')
ch = logging.StreamHandler()
ch.setLevel(logging.DEBUG)
formatter = logging.Formatter('%(levelname)s:%(name)s:%(message)s')
ch.setFormatter(formatter)
log.addHandler(ch)

log.info('Starting...')

net_obj = mnist.build_model()
solver = utils.SGDSolver()

input_dir = 'input/mnist'

# train_loader = utils.BatchLoader(os.path.join(input_dir, 'train.txt'),
#                                  os.path.join(input_dir, 'train'),
#                                  imshape=(28, 28), greyscale=True)
# val_loader = utils.BatchLoader(os.path.join(input_dir, 'val.txt'),
#                                os.path.join(input_dir, 'val'),
#                                imshape=(28, 28), greyscale=True)
# test_loader = utils.BatchLoader(os.path.join(input_dir, 'test.txt'),
#                                 os.path.join(input_dir, 'test'),
#                                 imshape=(28, 28), greyscale=True)

train_loader = utils.ChunkBatchLoader(os.path.join(input_dir, 'train_index.txt'),
                                      input_dir)
val_loader = utils.ChunkBatchLoader(os.path.join(input_dir, 'val_index.txt'),
                                    input_dir)
test_loader = utils.ChunkBatchLoader(os.path.join(input_dir, 'test_index.txt'),
                                     input_dir)

net = utils.Net(net_obj, solver, train_loader, val_loader, test_loader)
net.train_epochs = 500
net.snapshot_freq = 0
net.batch_sz = 500
net.val_freq = 100

net.train()
