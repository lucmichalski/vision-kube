

class SiameseCenterSurroundModel(BaseModel):
    """
    Model for individual identification

    TODO:
        RBM / EBM  - http://deeplearning.net/tutorial/rbm.html
    """
    def __init__(model, autoinit=False, batch_size=128, input_shape=(None, 3, 64, 64)):
        super(SiameseCenterSurroundModel, model).__init__()
        model.network_layers = None
        model.input_shape = input_shape
        model.batch_size = batch_size
        model.output_dims = 1
        # bad name, says that this network will take
        # 2*N images in a batch and N labels that map to
        # two images a piece
        model.data_per_label = 2
        model.needs_padding = False
        if autoinit:
            model.initialize_architecture()

    def augment(self, Xb, yb=None):
        """

        CommandLine:
            python -m ibeis_cnn.models --test-SiameseCenterSurroundModel.augment --show

        Example:
            >>> # DISABLE_DOCTEST
            >>> from ibeis_cnn.models import *  # NOQA
            >>> from ibeis_cnn import train
            >>> data, labels = train.testdata_patchmatch()
            >>> cv2_data = utils.convert_theano_images_to_cv2_images(data)
            >>> batch_size = 128
            >>> Xb, yb = cv2_data[0:batch_size], labels[0:batch_size // 2]
            >>> self = SiameseCenterSurroundModel()
            >>> Xb1, yb1 = self.augment(Xb.copy(), yb.copy())
            >>> modified_indexes = np.where((Xb1 != Xb).sum(-1).sum(-1).sum(-1) > 0)[0]
            >>> ut.quit_if_noshow()
            >>> import plottool as pt
            >>> pt.imshow(Xb[modified_indexes[0]], pnum=(2, 2, 1))
            >>> pt.imshow(Xb1[modified_indexes[0]], pnum=(2, 2, 2))
            >>> pt.imshow(Xb[modified_indexes[1]], pnum=(2, 2, 3))
            >>> pt.imshow(Xb1[modified_indexes[1]], pnum=(2, 2, 4))
            >>> ut.show_if_requested()
        """
        import functools
        #Xb = Xb.copy()
        #if yb is not None:
        #    yb = yb.copy()
        Xb1, Xb2 = Xb[::2], Xb[1::2]
        rot_transforms  = [functools.partial(np.rot90, k=k) for k in range(1, 4)]
        flip_transforms = [np.fliplr, np.flipud]
        prob_rotate = .3
        prob_flip   = .3

        num = len(Xb1)

        # Determine which examples will be augmented
        rotate_flags = [random.uniform(0.0, 1.0) <= prob_rotate for _ in range(num)]
        flip_flags   = [random.uniform(0.0, 1.0) <= prob_flip for _ in range(num)]

        # Determine which functions to use
        rot_fn_list  = [random.choice(rot_transforms) if flag else None for flag in rotate_flags]
        flip_fn_list = [random.choice(flip_transforms) if flag else None for flag in flip_flags]

        for index, func_list in enumerate(zip(rot_fn_list, flip_fn_list)):
            for func in func_list:
                if func is not None:
                    pass
                    Xb1[index] = func(Xb1[index])
                    Xb2[index] = func(Xb2[index])
        return Xb, yb

    def build_model(model, batch_size, input_width, input_height,
                    input_channels, output_dims, verbose=True):
        r"""
        CommandLine:
            python -m ibeis_cnn.models --test-SiameseCenterSurroundModel.build_model
            python -m ibeis_cnn.models --test-SiameseCenterSurroundModel.build_model --verbcnn
            python -m ibeis_cnn.train --test-train_patchmatch_pz --vtd --max-examples=5 --batch_size=128 --learning_rate .0000001 --verbcnn
            python -m ibeis_cnn.train --test-train_patchmatch_pz --vtd

        Example:
            >>> # DISABLE_DOCTEST
            >>> from ibeis_cnn.models import *  # NOQA
            >>> # build test data
            >>> model = SiameseCenterSurroundModel()
            >>> batch_size = 128
            >>> input_width, input_height, input_channels = 64, 64, 3
            >>> output_dims = None
            >>> verbose = True
            >>> # execute function
            >>> output_layer = model.build_model(batch_size, input_width, input_height, input_channels, output_dims, verbose)
            >>> print('\n---- Arch Str')
            >>> model.print_architecture_str(sep='\n')
            >>> print('\n---- Layer Info')
            >>> model.print_layer_info()
            >>> print('\n---- HashID')
            >>> print('hashid=%r' % (model.get_architecture_hashid()),)
            >>> print('----')
            >>> # verify results
            >>> result = str(output_layer)
            >>> print(result)
        """
        #print('build model may override settings')
        input_shape = (batch_size, input_channels, input_width, input_height)
        model.input_shape = input_shape
        model.batch_size = batch_size
        model.output_dims = 1
        model.initialize_architecture(verbose=verbose)
        output_layer = model.get_output_layer()
        return output_layer

    def get_2ch2stream_def(model, verbose=True):
        """
        Notes:

            (i) 2ch-2stream consists of two branches
                C(95, 5, 1)- ReLU-
                P(2, 2)-
                C(96, 3, 1)- ReLU-
                P(2, 2)-
                C(192, 3, 1)- ReLU-
                C(192, 3, 1)- ReLU,

                one for central and one for surround parts, followed by
                F(768)- ReLU-
                F(1)
        """
        raise NotImplementedError('The 2-channel part is not yet implemented')
        _P = functools.partial
        leaky = dict(nonlinearity=nonlinearities.LeakyRectify(leakiness=(1. / 10.)))
        leaky_orthog = dict(W=init.Orthogonal(), **leaky)

        network_layers_def = (
            [
                _P(layers.InputLayer, shape=model.input_shape),
                # TODO: Stack Inputs by making a 2 Channel Layer
                _P(custom_layers.CenterSurroundLayer),

                #layers.GaussianNoiseLayer,
                #caffenet.get_conv2d_layer(0, trainable=False, **leaky),
                #lasange_ext.freeze_params,
                _P(Conv2DLayer, num_filters=96, filter_size=(5, 5), stride=(1, 1), name='C0', **leaky_orthog),
                _P(MaxPool2DLayer, pool_size=(2, 2), stride=(2, 2), name='P0'),
                _P(Conv2DLayer, num_filters=96, filter_size=(3, 3), name='C1', **leaky_orthog),
                _P(MaxPool2DLayer, pool_size=(2, 2), stride=(2, 2), name='P1'),
                _P(Conv2DLayer, num_filters=192, filter_size=(3, 3), name='C2', **leaky_orthog),
                _P(Conv2DLayer, num_filters=192, filter_size=(3, 3), name='C3', **leaky_orthog),
                #_P(custom_layers.L2NormalizeLayer, axis=2),
                _P(custom_layers.SiameseConcatLayer, axis=1, data_per_label=4),  # 4 when CenterSurroundIsOn
                #_P(custom_layers.SiameseConcatLayer, data_per_label=2),
                _P(layers.DenseLayer, num_units=768, name='F1',  **leaky_orthog),
                _P(layers.DropoutLayer, p=0.5),
                _P(layers.DenseLayer, num_units=1, name='F2', **leaky_orthog),
            ]
        )
        return network_layers_def

    def get_siam2stream_def(model, verbose=True):
        """
        Notes:
            (viii) siam-2stream has 4 branches
                C(96, 4, 2)- ReLU-
                P(2, 2)-
                C(192, 3, 1)- ReLU-
                C(256, 3, 1)- ReLU-
                C(256, 3, 1)- ReLU

                (coupled in pairs for central and surround streams), and
                decision layer

                F(512)-ReLU-
                F(1)
        """
        _P = functools.partial

        leaky = dict(nonlinearity=nonlinearities.LeakyRectify(leakiness=(1. / 10.)))
        leaky_orthog = dict(W=init.Orthogonal(), **leaky)

        network_layers_def = (
            [
                _P(layers.InputLayer, shape=model.input_shape),
                # TODO: Stack Inputs by making a 2 Channel Layer
                _P(custom_layers.CenterSurroundLayer),

                layers.GaussianNoiseLayer,
                #caffenet.get_conv2d_layer(0, trainable=False, **leaky),
                _P(Conv2DLayer, num_filters=96, filter_size=(4, 4), name='C0', **leaky_orthog),
                _P(MaxPool2DLayer, pool_size=(2, 2), stride=(2, 2), name='P0'),
                _P(Conv2DLayer, num_filters=192, filter_size=(3, 3), name='C2', **leaky_orthog),
                _P(Conv2DLayer, num_filters=256, filter_size=(3, 3), name='C3', **leaky_orthog),
                _P(Conv2DLayer, num_filters=256, filter_size=(3, 3), name='C3', **leaky_orthog),
                #_P(custom_layers.L2NormalizeLayer, axis=2),
                _P(custom_layers.SiameseConcatLayer, axis=1, data_per_label=4),  # 4 when CenterSurroundIsOn
                #_P(custom_layers.SiameseConcatLayer, data_per_label=2),
                _P(layers.DenseLayer, num_units=512, name='F1',  **leaky_orthog),
                _P(layers.DropoutLayer, p=0.5),
                _P(layers.DenseLayer, num_units=1, name='F2', **leaky_orthog),
            ]
        )
        #raise NotImplementedError('The 2-channel part is not yet implemented')
        return network_layers_def

    def initialize_architecture(model, verbose=True):
        """
        Notes:
            http://arxiv.org/pdf/1504.03641.pdf

            Performance of several models on the local image patches benchmark.

            The shorthand notation used was the following.

            C(n, k, s) is a convolutional layer with n filters of spatial size k x k applied with stride s.

            P(k, s) is a max-pooling layer of size k x k applied with stride s.

            F(n) denotes a fully connected linear layer with n output
            units.

            The models architecture is as follows:

            (ii)
                2ch-deep =
                C(96, 4, 3)-
                Stack(96)-
                P(2, 2)-
                Stack(192)-
                F(1),

                where
                Stack(n) =
                C(n, 3, 1)- ReLU-
                C(n, 3, 1)- ReLU-
                C(n, 3, 1)- ReLU.

            (iii)
                2ch = C(96, 7, 3)- ReLU-
                P(2, 2)-
                C(192, 5, 1)- ReLU-
                P(2, 2)-
                C(256, 3, 1)- ReLU-
                F(256)- ReLU-
                F(1)

            (iv) siam has two branches
                C(96, 7, 3)- ReLU-
                P(2, 2)-
                C(192, 5, 1)- ReLU-
                P(2, 2)-
                C(256, 3, 1)- ReLU

                and decision layer
                F(512)- ReLU-
                F(1)

            (v) siam-l2 reduces to a single branch of siam

            (vi) pseudo-siam is uncoupled version of siam

            (vii) pseudo-siam-l2 reduces to a single branch of pseudo-siam

            (ix) siam-2stream-l2 consists of one central and one surround
                branch of siam-2stream.
        """
        # TODO: remove output dims
        #_P = functools.partial
        (_, input_channels, input_width, input_height) = model.input_shape
        if verbose:
            print('[model] Initialize siamese model architecture')
            print('[model]   * batch_size     = %r' % (model.batch_size,))
            print('[model]   * input_width    = %r' % (input_width,))
            print('[model]   * input_height   = %r' % (input_height,))
            print('[model]   * input_channels = %r' % (input_channels,))
            print('[model]   * output_dims    = %r' % (model.output_dims,))

        #leaky = dict(nonlinearity=nonlinearities.LeakyRectify(leakiness=(1. / 10.)))
        #leaky_orthog = dict(W=init.Orthogonal(), **leaky)

        #vggnet = PretrainedNetwork('vggnet')
        #caffenet = PretrainedNetwork('caffenet')

        #def conv_pool(num_filters, filter_size, pool_size, stride):
        #    conv_ = _P(Conv2DLayer, num_filters=num_filters, filter_size=filter_size, **leaky_orthog)
        #    maxpool_ =  _P(MaxPool2DLayer, pool_size=pool_size, stride=stride)
        #    return [conv_, maxpool_]
        #] +
        ##conv_pool(num_filters=16, filter_size=(6, 6), pool_size=(2, 2), stride=(2, 2)) +
        ##conv_pool(num_filters=32, filter_size=(3, 3), pool_size=(2, 2), stride=(2, 2)) +
        #[

        #network_layers_def = (
        #    [
        #        _P(layers.InputLayer, shape=model.input_shape),
        #        _P(custom_layers.CenterSurroundLayer),

        #        #layers.GaussianNoiseLayer,
        #        #caffenet.get_conv2d_layer(0, trainable=False, **leaky),
        #        _P(Conv2DLayer, num_filters=32, filter_size=(4, 4), name='C0', **leaky_orthog),
        #        _P(MaxPool2DLayer, pool_size=(2, 2), stride=(2, 2), name='M0'),
        #        _P(Conv2DLayer, num_filters=32, filter_size=(3, 3), name='C1', **leaky_orthog),
        #        _P(Conv2DLayer, num_filters=32, filter_size=(3, 3), name='C2', **leaky_orthog),
        #        _P(Conv2DLayer, num_filters=32, filter_size=(3, 3), name='C3', **leaky_orthog),
        #        _P(Conv2DLayer, num_filters=32, filter_size=(3, 3), name='C5', **leaky_orthog),
        #        _P(Conv2DLayer, num_filters=32, filter_size=(3, 3), name='C6', **leaky_orthog),
        #        #_P(custom_layers.L2NormalizeLayer, axis=2),
        #        _P(custom_layers.SiameseConcatLayer, axis=1, data_per_label=4),  # 4 when CenterSurroundIsOn
        #        #_P(custom_layers.SiameseConcatLayer, data_per_label=2),
        #        _P(layers.DenseLayer, num_units=512, name='D1',  **leaky_orthog),
        #        #_P(layers.DropoutLayer, p=0.5),
        #        _P(layers.DenseLayer, num_units=512, name='D2',  **leaky_orthog),
        #        #_P(layers.DropoutLayer, p=0.5),
        #        _P(layers.DenseLayer, num_units=1, name='D3', **leaky_orthog),
        #    ]
        #)
        network_layers_def = model.get_siam2stream_def()

        # connect and record layers
        network_layers = evaluate_layer_list(network_layers_def)
        model.network_layers = network_layers
        output_layer = model.network_layers[-1]
        return output_layer

    def loss_function(model, E, Y, T=T, verbose=True):
        """

        CommandLine:
            python -m ibeis_cnn.models --test-loss_function

        Example:
            >>> # DISABLE_DOCTEST
            >>> from ibeis_cnn.models import *  # NOQA
            >>> from ibeis_cnn import train
            >>> from ibeis_cnn import batch_processing as batch
            >>> data, labels = train.testdata_patchmatch()
            >>> model = SiameseCenterSurroundModel(autoinit=True, input_shape=(128,) + (data.shape[1:]))
            >>> theano_forward = batch.create_unbuffered_network_output_func(model)
            >>> batch_size = model.batch_size
            >>> Xb, yb = data[0:batch_size], labels[0:batch_size // 2]
            >>> network_output = theano_forward(Xb)[0]
            >>> E = network_output
            >>> Y = yb
            >>> T = np
            >>> # execute function
            >>> verbose = True
            >>> avg_loss = model.loss_function(E, Y, T=T)
            >>> result = str(avg_loss)
            >>> print(result)
        """
        if verbose:
            print('[model] Build SiameseCenterSurroundModel loss function')
        # Hinge-loss objective from Zagoruyko and Komodakis
        Y_ = (1 - (2 * Y))
        loss = T.maximum(0, 1 - (Y_ * E.T))

        # Contrastive loss function from LeCunn 2005
        #Q = 2
        #E = E.flatten()
        #genuine_loss = (1 - Y) * (2 / Q) * (E ** 2)
        #imposter_loss = (Y) * 2 * Q * T.exp((-2.77 * E) / Q)
        #loss = genuine_loss + imposter_loss
        avg_loss = T.mean(loss)
        loss.name = 'loss'
        avg_loss.name = 'avg_loss'
        return avg_loss

        #avg_loss = lasange_ext.siamese_loss(G, Y_padded, data_per_label=2)
        #return avg_loss


class SiameseModel(BaseModel):
    """
    Model for individual identification
    """
    def __init__(model):
        super(SiameseModel, model).__init__()
        model.network_layers = None
        # bad name, says that this network will take
        # 2*N images in a batch and N labels that map to
        # two images a piece
        model.data_per_label = 2
        model.needs_padding = True

    def build_model(model, batch_size, input_width, input_height,
                    input_channels, output_dims, verbose=True):
        r"""
        CommandLine:
            python -m ibeis_cnn.models --test-SiameseModel.build_model

        Example:
            >>> # DISABLE_DOCTEST
            >>> from ibeis_cnn.models import *  # NOQA
            >>> # build test data
            >>> model = SiameseModel()
            >>> batch_size = 128
            >>> input_width, input_height, input_channels  = 64, 64, 3
            >>> output_dims = None
            >>> verbose = True
            >>> output_layer = model.build_model(batch_size, input_width, input_height, input_channels, output_dims, verbose)
            >>> print('----')
            >>> model.print_architecture_str(sep='\n')
            >>> print('hashid=%r' % (model.get_architecture_hashid()),)
            >>> print('----')
            >>> result = str(output_layer)
            >>> print(result)
        """
        input_shape = (None, input_channels, input_width, input_height)
        model.input_shape = input_shape
        model.batch_size = batch_size
        model.output_dims = 256
        model.initialize_architecture(verbose=verbose)
        output_layer = model.get_output_layer()
        return output_layer

    def initialize_architecture(model, verbose=True):
        # TODO: remove output dims
        _P = functools.partial
        (_, input_channels, input_width, input_height) = model.input_shape
        model.output_dims
        if verbose:
            print('[model] Initialize siamese model architecture')
            print('[model]   * batch_size     = %r' % (model.batch_size,))
            print('[model]   * input_width    = %r' % (input_width,))
            print('[model]   * input_height   = %r' % (input_height,))
            print('[model]   * input_channels = %r' % (input_channels,))
            print('[model]   * output_dims    = %r' % (model.output_dims,))

        #num_filters=32,
        #filter_size=(3, 3),
        ## nonlinearity=nonlinearities.rectify,
        #nonlinearity=nonlinearities.LeakyRectify(leakiness=(1. / 10.)),

        #rlu_glorot = dict(nonlinearity=nonlinearities.rectify, W=init.GlorotUniform())
        #rlu_orthog = dict(nonlinearity=nonlinearities.rectify, W=init.Orthogonal())
        leaky = dict(nonlinearity=nonlinearities.LeakyRectify(leakiness=(1. / 10.)))
        leaky_orthog = dict(W=init.Orthogonal(), **leaky)
        # variable batch size (None), channel, width, height
        #input_shape = (batch_size * model.data_per_label, input_channels, input_width, input_height)

        vggnet = PretrainedNetwork('vggnet')
        #caffenet = PretrainedNetwork('caffenet')

        network_layers_def = [
            _P(layers.InputLayer, shape=model.input_shape),

            #layers.GaussianNoiseLayer,
            vggnet.get_conv2d_layer(0, **leaky),

            _P(Conv2DLayer, num_filters=16, filter_size=(3, 3), **leaky_orthog),

            _P(MaxPool2DLayer, pool_size=(2, 2), stride=(2, 2)),
            _P(Conv2DLayer, num_filters=16, filter_size=(3, 3), **leaky_orthog),

            _P(MaxPool2DLayer, pool_size=(2, 2), stride=(2, 2)),

            #_P(layers.DenseLayer, num_units=512, **leaky_orthog),
            #_P(layers.DropoutLayer, p=0.5),
            _P(layers.DenseLayer, num_units=256, **leaky_orthog),
            _P(layers.DropoutLayer, p=0.5),
            #_P(lasange_ext.l1),  # TODO: make a layer

            #_P(layers.DenseLayer, num_units=256, **leaky_orthog),
            #_P(layers.DropoutLayer, p=0.5),
            #_P(layers.FeaturePoolLayer, pool_size=2),

            #_P(layers.DenseLayer, num_units=1024, **leaky_orthog),
            #_P(layers.FeaturePoolLayer, pool_size=2,),
            #_P(layers.DropoutLayer, p=0.5),

            #_P(layers.DenseLayer, num_units=output_dims,
            #   nonlinearity=nonlinearities.softmax,
            #   W=init.Orthogonal(),),
        ]

        # connect and record layers
        network_layers = evaluate_layer_list(network_layers_def)
        model.network_layers = network_layers
        output_layer = model.network_layers[-1]
        model.output_layer = output_layer
        return output_layer

    def loss_function(self, G, Y_padded, T=T, verbose=True):
        if verbose:
            print('[model] Build center surround siamese loss function')
        avg_loss = lasange_ext.siamese_loss(G, Y_padded, data_per_label=2)
        return avg_loss

