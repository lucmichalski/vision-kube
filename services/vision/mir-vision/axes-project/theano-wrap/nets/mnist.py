import lasagne
from lasagne.layers import InputLayer, DenseLayer, NonlinearityLayer
from lasagne.layers import Conv2DLayer, MaxPool2DLayer, DropoutLayer

def build_model():

    net = {}

    net['input'] = InputLayer(shape=(None, 1, 28, 28), name='input')

    # Apply 20% dropout to the input data:
    net['drop'] = DropoutLayer(net['input'], p=0.2)

    # Add a fully-connected layer of 800 units, using the linear rectifier, and
    # initializing weights with Glorot's scheme (which is the default anyway):
    net['hid1'] = DenseLayer(
        net['drop'], num_units=800,
        nonlinearity=lasagne.nonlinearities.rectify,
        W=lasagne.init.GlorotUniform())

    # We'll now add dropout of 50%:
    net['hid1_drop'] = DropoutLayer(net['hid1'], p=0.5)

    # Another 800-unit layer:
    net['hid2'] = DenseLayer(
        net['hid1_drop'], num_units=800,
        nonlinearity=lasagne.nonlinearities.rectify)

    # 50% dropout again:
    net['hid2_drop'] = DropoutLayer(net['hid2'], p=0.5)

    # Finally, we'll add the fully-connected output layer, of 10 softmax units:
    net['prob'] = DenseLayer(
        net['hid2_drop'], num_units=10,
        nonlinearity=lasagne.nonlinearities.linear)

    # Each layer is linked to its incoming layer(s), so we only need to pass
    # the output layer to give access to a network in Lasagne:
    return net

def build_model_cnn():

    net = {}

    net['input'] = InputLayer(shape=(None, 1, 28, 28), name='input')
    # This time we do not apply input dropout, as it tends to work less well
    # for convolutional layers.

    # Convolutional layer with 32 kernels of size 5x5. Strided and padded
    # convolutions are supported as well; see the docstring.
    net['conv1'] = Conv2DLayer(
        net['input'], num_filters=32, filter_size=(5, 5),
        nonlinearity=lasagne.nonlinearities.rectify,
        W=lasagne.init.GlorotUniform())
    # Expert note: Lasagne provides alternative convolutional layers that
    # override Theano's choice of which implementation to use; for details
    # please see http://lasagne.readthedocs.org/en/latest/user/tutorial.html.

    # Max-pooling layer of factor 2 in both dimensions:
    net['pool1'] = MaxPool2DLayer(net['conv1'], pool_size=(2, 2))

    # Another convolution with 32 5x5 kernels, and another 2x2 pooling:
    net['conv2'] = Conv2DLayer(
        net['pool1'], num_filters=32, filter_size=(5, 5),
        nonlinearity=lasagne.nonlinearities.rectify)
    net['pool2'] = MaxPool2DLayer(net['conv2'], pool_size=(2, 2))

    # A fully-connected layer of 256 units with 50% dropout on its inputs:
    net['fc3'] = DenseLayer(
        lasagne.layers.dropout(net['pool2'], p=.5),
        num_units=256,
        nonlinearity=lasagne.nonlinearities.rectify)

    # And, finally, the 10-unit output layer with 50% dropout on its inputs:
    net['prob'] = DenseLayer(
        lasagne.layers.dropout(net['fc3'], p=.5),
        num_units=10,
        nonlinearity=lasagne.nonlinearities.softmax)

    return net
