from lasagne.layers import InputLayer, DenseLayer, NonlinearityLayer
from lasagne.layers.dnn import Conv2DDNNLayer as ConvLayer
from lasagne.layers import Pool2DLayer as PoolLayer
from lasagne.nonlinearities import softmax

def build_model():
    net = {}
    net['input'] = InputLayer((None, 3, 224, 224), name='input')
    net['conv1_1'] = ConvLayer(net['input'], 64, 3, pad=1, name='conv1_1')
    net['conv1_2'] = ConvLayer(net['conv1_1'], 64, 3, pad=1, name='conv1_2')
    net['pool1'] = PoolLayer(net['conv1_2'], 2, name='pool1')
    net['conv2_1'] = ConvLayer(net['pool1'], 128, 3, pad=1, name='conv2_1')
    net['conv2_2'] = ConvLayer(net['conv2_1'], 128, 3, pad=1, name='conv2_2')
    net['pool2'] = PoolLayer(net['conv2_2'], 2, name='pool2')
    net['conv3_1'] = ConvLayer(net['pool2'], 256, 3, pad=1, name='conv3_1')
    net['conv3_2'] = ConvLayer(net['conv3_1'], 256, 3, pad=1, name='conv3_2')
    net['conv3_3'] = ConvLayer(net['conv3_2'], 256, 3, pad=1, name='conv3_3')
    net['pool3'] = PoolLayer(net['conv3_3'], 2, name='pool3')
    net['conv4_1'] = ConvLayer(net['pool3'], 512, 3, pad=1, name='conv4_1')
    net['conv4_2'] = ConvLayer(net['conv4_1'], 512, 3, pad=1, name='conv4_2')
    net['conv4_3'] = ConvLayer(net['conv4_2'], 512, 3, pad=1, name='conv4_3')
    net['pool4'] = PoolLayer(net['conv4_3'], 2, name='pool4')
    net['conv5_1'] = ConvLayer(net['pool4'], 512, 3, pad=1, name='conv5_1')
    net['conv5_2'] = ConvLayer(net['conv5_1'], 512, 3, pad=1, name='conv5_2')
    net['conv5_3'] = ConvLayer(net['conv5_2'], 512, 3, pad=1, name='conv5_3')
    net['pool5'] = PoolLayer(net['conv5_3'], 2, name='pool5')
    net['fc6'] = DenseLayer(net['pool5'], num_units=4096, name='fc6')
    net['fc7'] = DenseLayer(net['fc6'], num_units=4096, name='fc7')
    net['fc8'] = DenseLayer(net['fc7'], num_units=1000, nonlinearity=None, name='fc8')
    net['prob'] = NonlinearityLayer(net['fc8'], softmax, name='prob')

    return net
