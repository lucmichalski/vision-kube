package org.deeplearning4j;

import org.deeplearning4j.datasets.canova.RecordReaderDataSetIterator;
import org.deeplearning4j.datasets.iterator.impl.ListDataSetIterator;
import org.deeplearning4j.eval.Evaluation;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.ConvolutionLayer;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.conf.layers.SubsamplingLayer;
import org.deeplearning4j.nn.conf.layers.setup.ConvolutionLayerSetup;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.dataset.api.iterator.StandardScaler;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;

/**
 * Created by agibsonccc on 9/21/15.
 */
public class EvaluationOnScene {
    public static void main(String[] args) throws Exception {
        DataSet data = new DataSet();
        data.load(new File("test.bin"));
        data = data.sample(10);
        BufferedInputStream bis = new BufferedInputStream(new FileInputStream("model.bin"));

        File meanFile = new File("mean.bin");
        File stdFile = new File("std.bin");
        StandardScaler scaler = new StandardScaler();
        scaler.load(meanFile,stdFile);
        scaler.transform(data);
        DataInputStream dis = new DataInputStream(bis);
        INDArray params = Nd4j.read(dis);

        MultiLayerConfiguration conf = DataSetSetup.convolutionConf();
        MultiLayerNetwork network = new MultiLayerNetwork(conf);
        network.init();
        network.setParameters(params);
        Evaluation evaluation = new Evaluation(6);
        DataSetIterator list = new ListDataSetIterator(data.asList(),10);
        while(list.hasNext()) {
            DataSet next = list.next();
            evaluation.eval(next.getLabels(),network.output(next.getFeatureMatrix(),false));

        }
        System.out.println(evaluation.stats());
    }


}
