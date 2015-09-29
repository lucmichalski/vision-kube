package org.deeplearning4j.nn.layers.convolution;

import org.deeplearning4j.berkeley.Pair;
import org.deeplearning4j.datasets.iterator.impl.MnistDataSetIterator;
import org.deeplearning4j.eval.Evaluation;
import org.deeplearning4j.nn.api.Layer;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.ConvolutionLayer;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.conf.layers.SubsamplingLayer;
import org.deeplearning4j.nn.conf.layers.setup.ConvolutionLayerSetup;
import org.deeplearning4j.nn.conf.preprocessor.CnnToFeedForwardPreProcessor;
import org.deeplearning4j.nn.conf.preprocessor.FeedForwardToCnnPreProcessor;
import org.deeplearning4j.nn.gradient.DefaultGradient;
import org.deeplearning4j.nn.gradient.Gradient;
import org.deeplearning4j.nn.layers.factory.LayerFactories;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.params.DefaultParamInitializer;
import org.deeplearning4j.nn.weights.WeightInit;
import org.junit.Before;
import org.junit.Test;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import static org.junit.Assert.*;

/**
 * @author Adam Gibson
 */
public class ConvolutionLayerTest {

    @Before
    public void before() {
        Nd4j.dtype = DataBuffer.Type.DOUBLE;
        Nd4j.factory().setDType(DataBuffer.Type.DOUBLE);
        Nd4j.EPS_THRESHOLD = 1e-4;
    }

    @Test
    public void testTwdFirstLayer() throws Exception {
        MultiLayerConfiguration.Builder builder = new NeuralNetConfiguration.Builder()
                .seed(123)
                .iterations(5)
                .batchSize(100)
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .constrainGradientToUnitNorm(true)
                .l2(2e-4)
                .regularization(true)
                .useDropConnect(true)
                .list(4)
                .layer(0, new ConvolutionLayer.Builder(8, 8) //16 filters kernel size 8 stride 4
                        .stride(4, 4)
                        .nOut(16)
                        .dropOut(0.5)
                        .activation("relu")
                        .weightInit(WeightInit.XAVIER)
                        .build())
                .layer(1, new ConvolutionLayer.Builder(4, 4) //32 filters kernel size 4 stride 2
                        .stride(2, 2)
                        .nOut(32)
                        .dropOut(0.5)
                        .activation("relu")
                        .weightInit(WeightInit.XAVIER)
                        .build())
                .layer(2, new DenseLayer.Builder() //fully connected with 256 rectified units
                        .nOut(256)
                        .activation("relu")
                        .weightInit(WeightInit.XAVIER)
                        .dropOut(0.5)
                        .build())
                .layer(3, new OutputLayer.Builder(LossFunctions.LossFunction.SQUARED_LOSS) //output layer
                        .nOut(10)
                        .weightInit(WeightInit.XAVIER)
                        .activation("softmax")
                        .build())
                .backprop(true).pretrain(false);
        new ConvolutionLayerSetup(builder,28,28,1);
        DataSetIterator iter = new MnistDataSetIterator(10,10);
        MultiLayerConfiguration conf = builder.build();
        MultiLayerNetwork network = new MultiLayerNetwork(conf);
        network.init();
        network.fit(iter.next());

    }


    @Test
    public void testCNNBiasInit() {
        ConvolutionLayer cnn = new ConvolutionLayer.Builder()
                .nIn(1)
                .nOut(3)
                .biasInit(1)
                .build();

        NeuralNetConfiguration conf = new NeuralNetConfiguration.Builder()
                .layer(cnn)
                .build();

        Layer layer =  LayerFactories.getFactory(conf).create(conf);

        assertEquals(1, layer.getParam("b").size(0));
    }

    @Test
    public void testCNNInputSetupMNIST() throws Exception{
        INDArray input = getMnistData();
        Layer layer = getMNISTConfig();
        layer.activate(input);

        assertEquals(input, layer.input());
        assertArrayEquals(input.shape(), layer.input().shape());
    }

    @Test
    public void testFeatureMapShapeMNIST() throws Exception  {
        int inputWidth = 28;
        int[] stride = new int[] {2, 2};
        int[] padding = new int[] {0,0};
        int[] kernelSize = new int[] {9, 9};
        int nChannelsIn = 1;
        int depth = 20;
        int  featureMapWidth = (inputWidth + padding[1] * 2 - kernelSize[1]) / stride[1] + 1;

        INDArray input = getMnistData();

        Layer layer = getCNNConfig(nChannelsIn, depth, kernelSize, stride, padding);
        INDArray convActivations = layer.activate(input);

        assertEquals(featureMapWidth, convActivations.size(2));
        assertEquals(depth, convActivations.size(1));
    }

    @Test
    public void testActivateResultsContained()  {
        Layer layer = getContainedConfig();
        INDArray input = getContainedData();
        INDArray expectedOutput = Nd4j.create(new double[] {
                0.98201379,  0.98201379,  0.98201379,  0.98201379,  0.99966465,
                0.99966465,  0.99966465,  0.99966465,  0.98201379,  0.98201379,
                0.98201379,  0.98201379,  0.99966465,  0.99966465,  0.99966465,
                0.99966465,  0.98201379,  0.98201379,  0.98201379,  0.98201379,
                0.99966465,  0.99966465,  0.99966465,  0.99966465,  0.98201379,
                0.98201379,  0.98201379,  0.98201379,  0.99966465,  0.99966465,
                0.99966465,  0.99966465
        },new int[]{1,2,4,4});

        INDArray convActivations = layer.activate(input);

        assertArrayEquals(expectedOutput.shape(), convActivations.shape());
        assertEquals(expectedOutput, convActivations);
    }


    @Test
    public void testPreOutputMethodContained()  {
        Layer layer = getContainedConfig();
        INDArray col = getContainedCol();

        INDArray expectedOutput = Nd4j.create(new double[] {
                4.,4.,4.,4.,8.,8.,8.,8.,4.,4.,4.,4.,8.,8.,8.,8.,4.,4.
                ,4.,4.,8.,8.,8.,8.,4.,4.,4.,4.,8.,8.,8.,8
        },new int[]{1, 2, 4, 4});

        org.deeplearning4j.nn.layers.convolution.ConvolutionLayer layer2 = (org.deeplearning4j.nn.layers.convolution.ConvolutionLayer) layer;
        layer2.setCol(col);
        INDArray activation = layer2.preOutput(true);

        assertArrayEquals(expectedOutput.shape(), activation.shape());
        assertEquals(expectedOutput, activation);
    }

    //note precision is off on this test but the numbers are close
    //investigation in a future release should determine how to resolve
    @Test
    public void testBackpropResultsContained()  {
        Layer layer = getContainedConfig();
        INDArray input = getContainedData();
        INDArray col = getContainedCol();
        INDArray epsilon = Nd4j.ones(1, 2, 4, 4);

        INDArray expectedBiasGradient = Nd4j.create(new double[]{
                0.16608272, 0.16608272
        }, new int[]{1, 2});
        INDArray expectedWeightGradient = Nd4j.create(new double[] {
                0.17238397,  0.17238397,  0.33846668,  0.33846668,  0.17238397,
                0.17238397,  0.33846668,  0.33846668
        }, new int[]{2,1,2,2});
        INDArray expectedEpsilon = Nd4j.create(new double[] {
                0.00039383,  0.00039383,  0.00039383,  0.00039383,  0.00039383,
                0.00039383,  0.        ,  0.        ,  0.00039383,  0.00039383,
                0.00039383,  0.00039383,  0.00039383,  0.00039383,  0.        ,
                0.        ,  0.02036651,  0.02036651,  0.02036651,  0.02036651,
                0.02036651,  0.02036651,  0.        ,  0.        ,  0.02036651,
                0.02036651,  0.02036651,  0.02036651,  0.02036651,  0.02036651,
                0.        ,  0.        ,  0.00039383,  0.00039383,  0.00039383,
                0.00039383,  0.00039383,  0.00039383,  0.        ,  0.        ,
                0.00039383,  0.00039383,  0.00039383,  0.00039383,  0.00039383,
                0.00039383,  0.        ,  0.        ,  0.        ,  0.        ,
                0.        ,  0.        ,  0.        ,  0.        ,  0.        ,
                0.        ,  0.        ,  0.        ,  0.        ,  0.        ,
                0.        ,  0.        ,  0.        ,  0.
        },new int[]{1,1,8,8});

        layer.setInput(input);
        org.deeplearning4j.nn.layers.convolution.ConvolutionLayer layer2 = (org.deeplearning4j.nn.layers.convolution.ConvolutionLayer) layer;
        layer2.setCol(col);
        Pair<Gradient, INDArray> pair = layer2.backpropGradient(epsilon);

        assertArrayEquals(expectedEpsilon.shape(), pair.getSecond().shape());
        assertArrayEquals(expectedWeightGradient.shape(), pair.getFirst().getGradientFor("W").shape());
        assertArrayEquals(expectedBiasGradient.shape(), pair.getFirst().getGradientFor("b").shape());
        assertEquals(expectedEpsilon, pair.getSecond());
        assertEquals(expectedWeightGradient, pair.getFirst().getGradientFor("W"));
        assertEquals(expectedBiasGradient, pair.getFirst().getGradientFor("b"));

    }

    //note precision is off on this test but the numbers are close
    //investigation in a future release should determine how to resolve
    @Test
    public void testCalculateDeltaContained() {
        Layer layer = getContainedConfig();
        INDArray input = getContainedData();
        INDArray col = getContainedCol();
        INDArray epsilon = Nd4j.ones(1,2,4,4);

        INDArray expectedOutput = Nd4j.create(new double[] {
                0.02036651,  0.02036651,  0.02036651,  0.02036651,  0.00039383,
                0.00039383,  0.00039383,  0.00039383,  0.02036651,  0.02036651,
                0.02036651,  0.02036651,  0.00039383,  0.00039383,  0.00039383,
                0.00039383,  0.02036651,  0.02036651,  0.02036651,  0.02036651,
                0.00039383,  0.00039383,  0.00039383,  0.00039383,  0.02036651,
                0.02036651,  0.02036651,  0.02036651,  0.00039383,  0.00039383,
                0.00039383,  0.00039383
        },new int[]{1, 2, 4, 4});

        layer.setInput(input);
        org.deeplearning4j.nn.layers.convolution.ConvolutionLayer layer2 = (org.deeplearning4j.nn.layers.convolution.ConvolutionLayer) layer;
        layer2.setCol(col);
        INDArray delta = layer2.calculateDelta(epsilon);

        assertArrayEquals(expectedOutput.shape(), delta.shape());
        assertEquals(expectedOutput, delta);
    }

    //////////////////////////////////////////////////////////////////////////////////

    private static Layer getCNNConfig(int nIn, int nOut, int[] kernelSize, int[] stride, int[] padding){

        ConvolutionLayer layer = new ConvolutionLayer.Builder(kernelSize, stride, padding)
                .nIn(nIn)
                .nOut(nOut)
                .activation("sigmoid")
                .build();

        NeuralNetConfiguration conf = new NeuralNetConfiguration.Builder()
                .iterations(1)
                .layer(layer)
                .build();
        return LayerFactories.getFactory(conf).create(conf);

    }

    public Layer getMNISTConfig(){
        int[] kernelSize = new int[] {9, 9};
        int[] stride = new int[] {2,2};
        int[] padding = new int[] {1,1};
        int nChannelsIn = 1;
        int depth = 20;

        return getCNNConfig(nChannelsIn, depth, kernelSize, stride, padding);

    }

    public INDArray getMnistData() throws Exception {
        int inputWidth = 28;
        int inputHeight = 28;
        int nChannelsIn = 1;
        int nExamples = 5;

        DataSetIterator data = new MnistDataSetIterator(nExamples, nExamples);
        DataSet mnist = data.next();
        nExamples = mnist.numExamples();
        return mnist.getFeatureMatrix().reshape(nExamples, nChannelsIn, inputHeight, inputWidth);
    }

    public Layer getContainedConfig(){
        int[] kernelSize = new int[] {2, 2};
        int[] stride = new int[] {2,2};
        int[] padding = new int[] {0,0};
        int nChannelsIn = 1;
        int depth = 2;

        INDArray W = Nd4j.create(new double[] {
                0.5,  0.5,  0.5,  0.5,  0.5,  0.5,  0.5,  0.5
        }, new int[]{2,1,2,2});
        INDArray b = Nd4j.create(new double[] {1,1});
        Layer layer = getCNNConfig(nChannelsIn, depth, kernelSize, stride, padding);
        layer.setParam("W", W);
        layer.setParam("b", b);

        return layer;

    }

    public INDArray getContainedData() {
        INDArray ret = Nd4j.create(new double[]{
                1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3,
                3, 4, 4, 4, 4, 4, 4, 4, 4, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2,
                2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4
        }, new int[]{1,1,8,8});
        return ret;
    }

    public INDArray getContainedCol() {
        return Nd4j.create(new double[] {
                1, 1, 1, 1, 3, 3, 3, 3, 1, 1, 1, 1, 3, 3, 3, 3, 1, 1, 1, 1, 3, 3, 3,
                3, 1, 1, 1, 1, 3, 3, 3, 3, 2, 2, 2, 2, 4, 4, 4, 4, 2, 2, 2, 2, 4, 4,
                4, 4, 2, 2, 2, 2, 4, 4, 4, 4, 2, 2, 2, 2, 4, 4, 4, 4
        },new int[]{1,1,2,2,4,4});
    }



    //////////////////////////////////////////////////////////////////////////////////


    @Test
    public void testCNNMLNPretrain() throws Exception {
        // Note CNN does not do pretrain
        int numSamples = 10;
        int batchSize = 10;
        DataSetIterator mnistIter = new MnistDataSetIterator(batchSize,numSamples, true);

        MultiLayerNetwork model = getCNNMLNConfig(false, true);
        model.fit(mnistIter);

        MultiLayerNetwork model2 = getCNNMLNConfig(false, true);
        model2.fit(mnistIter);

        DataSet test = mnistIter.next();

        Evaluation eval = new Evaluation();
        INDArray output = model.output(test.getFeatureMatrix());
        eval.eval(test.getLabels(), output);
        double f1Score = eval.f1();

        Evaluation eval2 = new Evaluation();
        INDArray output2 = model2.output(test.getFeatureMatrix());
        eval2.eval(test.getLabels(), output2);
        double f1Score2 = eval2.f1();

        assertEquals(f1Score, f1Score2, 1e-4);


    }


    @Test
    public void testCNNMLNBackprop() throws Exception {
        int numSamples = 10;
        int batchSize = 10;
        DataSetIterator mnistIter = new MnistDataSetIterator(batchSize, numSamples, true);

        MultiLayerNetwork model = getCNNMLNConfig(true, false);
        model.fit(mnistIter);

        MultiLayerNetwork model2 = getCNNMLNConfig(true, false);
        model2.fit(mnistIter);

        DataSet test = mnistIter.next();

        Evaluation eval = new Evaluation();
        INDArray output = model.output(test.getFeatureMatrix());
        eval.eval(test.getLabels(), output);
        double f1Score = eval.f1();

        Evaluation eval2 = new Evaluation();
        INDArray output2 = model2.output(test.getFeatureMatrix());
        eval2.eval(test.getLabels(), output2);
        double f1Score2 = eval2.f1();

        assertEquals(f1Score, f1Score2, 1e-4);

    }

    //////////////////////////////////////////////////////////////////////////////////

    private static MultiLayerNetwork getCNNMLNConfig(boolean backprop, boolean pretrain) {
        Nd4j.ENFORCE_NUMERICAL_STABILITY = true;

        final int numRows = 28;
        final int numColumns = 28;
        int nChannels = 1;
        int outputNum = 10;
        int iterations = 10;
        int seed = 123;

        MultiLayerConfiguration.Builder conf = new NeuralNetConfiguration.Builder()
                .seed(seed)
                .iterations(iterations)
                .optimizationAlgo(OptimizationAlgorithm.LINE_GRADIENT_DESCENT)
                .list(3)
                .layer(0, new ConvolutionLayer.Builder(new int[]{10, 10})
                        .nIn(nChannels)
                        .nOut(6)
                        .build())
                .layer(1, new SubsamplingLayer.Builder(SubsamplingLayer.PoolingType.MAX, new int[]{2, 2})
                        .weightInit(WeightInit.XAVIER)
                        .activation("relu")
                        .build())
                .layer(2, new OutputLayer.Builder(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD)
                        .nIn(150)
                        .nOut(outputNum)
                        .weightInit(WeightInit.XAVIER)
                        .activation("softmax")
                        .build())
                .backprop(backprop).pretrain(pretrain);

        new ConvolutionLayerSetup(conf,numRows,numColumns,1);

        MultiLayerNetwork model = new MultiLayerNetwork(conf.build());
        model.init();

        return model;

    }
}
