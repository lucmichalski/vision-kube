package org.deeplearning4j;

import org.apache.commons.io.FileUtils;
import org.canova.api.records.reader.RecordReader;
import org.canova.api.split.FileSplit;
import org.canova.image.recordreader.ImageRecordReader;
import org.deeplearning4j.datasets.canova.RecordReaderDataSetIterator;
import org.deeplearning4j.datasets.iterator.impl.LFWDataSetIterator;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Hello world!
 *
 */
public class AppLocalScenes {
    public static void main( String[] args ) throws Exception {
        // Path to the labeled images
        String labeledPath = System.getProperty("user.home")+"/data";
        List<String> labels = new ArrayList<>(Arrays.asList("beach", "desert", "forest", "mountain", "rain", "snow"));
        Nd4j.ENFORCE_NUMERICAL_STABILITY = true;
        // Instantiating a RecordReader pointing to the data path with the specified
        // height and width for each image.
        int height = 28;
        int width = 28;
        int nIn = height * width;
        RecordReader recordReader = new ImageRecordReader(height, width, true,labels);
        recordReader.initialize(new FileSplit(new File(labeledPath)));

        // Canova to Dl4j
        DataSetIterator iter = new RecordReaderDataSetIterator(recordReader,60000, nIn,labels.size());
        //DataSetIterator iter = new LFWDataSetIterator(100,28,28);


        //setup the network
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().l2(2e-4)
                .l1(1e-1).optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT).iterations(5)
                .regularization(false)
                .list(4).backprop(true).pretrain(false)
                .layer(0,new DenseLayer.Builder().nIn(nIn).nOut(600).activation("relu")
                        .weightInit(WeightInit.XAVIER)
                        .build())
                .layer(1, new DenseLayer.Builder().nIn(600).nOut(500).activation("relu")
                        .weightInit(WeightInit.XAVIER)
                        .build())
                .layer(2, new DenseLayer.Builder().nIn(500).nOut(400).activation("relu")
                        .weightInit(WeightInit.XAVIER)
                        .build())
                .layer(3,new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT)
                        .activation("softmax")
                        .nIn(400).nOut(5749).weightInit(WeightInit.XAVIER)
                        .build()).build();

        MultiLayerNetwork trainedNetwork = new MultiLayerNetwork(conf);
        trainedNetwork.setListeners(new ScoreIterationListener(1));
        while(iter.hasNext()) {
            DataSet next = iter.next();
            next.normalizeZeroMeanZeroUnitVariance();
            trainedNetwork.fit(next);
        }
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream("newimagemodel.bin"));
        Nd4j.write(bos,trainedNetwork.params());
        FileUtils.write(new File("conf.yaml"),trainedNetwork.conf().toYaml());
        // System.out.println(evaluation.stats());


    }
}
