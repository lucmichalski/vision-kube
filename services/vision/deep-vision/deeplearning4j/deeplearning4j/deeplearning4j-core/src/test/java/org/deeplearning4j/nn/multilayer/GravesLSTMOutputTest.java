package org.deeplearning4j.nn.multilayer;

import org.deeplearning4j.eval.Evaluation;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.distribution.NormalDistribution;
import org.deeplearning4j.nn.conf.layers.GravesLSTM;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.conf.preprocessor.RnnToFeedForwardPreProcessor;
import org.deeplearning4j.nn.conf.stepfunctions.NegativeDefaultStepFunction;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.nd4j.linalg.api.buffer.DataBuffer.Type;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.nd4j.linalg.util.FeatureUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

/**
 * Created by Kirill Lebedev (drlebedev.com) on 8/31/2015.
 */
public class GravesLSTMOutputTest {

    private static int nIn = 20;
    private static int layerSize = 15;
    private static int window = 300;
    private static INDArray data;
    private static Logger log;
    private static Type type;

    @BeforeClass
    public static void setUp() {
        type = Nd4j.dtype;
        Nd4j.dtype = Type.FLOAT;
        log = LoggerFactory.getLogger(GravesLSTMOutputTest.class);
        data = getData();
    }

    @AfterClass
    public static void tearDown() {
        data = null;
        log = null;
        Nd4j.dtype = type;
    }

    @Test
    public void testSameLabelsOutput() {
        MultiLayerNetwork network = new MultiLayerNetwork(getNetworkConf(40));
        network.init();
        network.setListeners(new ScoreIterationListener(1));
        network.fit(reshapeInput(data), data);
        Evaluation ev = eval(network);
        Assert.assertTrue(ev.f1() > 0.90);
    }

    private Evaluation eval(MultiLayerNetwork network) {
        Evaluation ev = new Evaluation(nIn);
        INDArray predict = network.output(reshapeInput(data));
        ev.eval(data, predict);
        log.info(ev.stats());
        return ev;
    }

    private MultiLayerConfiguration getNetworkConf(int iterations) {
        return new NeuralNetConfiguration.Builder()
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .learningRate(0.1)
                .regularization(true)
                .l2(0.0025)
                .iterations(iterations)
                .stepFunction(new NegativeDefaultStepFunction())
                .list(2)
                .layer(0, new GravesLSTM.Builder().weightInit(WeightInit.DISTRIBUTION)
                        .dist(new NormalDistribution(0.0, 0.01)).nIn(nIn).nOut(layerSize)
                        .updater(Updater.ADAGRAD)
                        .activation("tanh").build())
                .layer(1, new OutputLayer.Builder(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD)
                        .updater(Updater.ADAGRAD).nIn(layerSize).nOut(nIn)
                        .activation("softmax").build())
                .inputPreProcessor(1, new RnnToFeedForwardPreProcessor())
                .backprop(true)
                .pretrain(false)
                .build();
    }

    private static INDArray getData() {
        Random r = new Random(1);
        int[] result = new int[window];
        for (int i = 0; i < window; i++) {
            result[i] = r.nextInt(nIn);
        }
        return FeatureUtil.toOutcomeMatrix(result, nIn);
    }

    private INDArray reshapeInput(INDArray inp) {
        int[] shape = inp.shape();
        int miniBatchSize = 1;
        INDArray reshaped = inp.reshape(miniBatchSize, shape[0] / miniBatchSize, shape[1]);
        return reshaped.permute(0, 2, 1);
    }
}
