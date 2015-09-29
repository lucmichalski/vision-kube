package org.deeplearning4j;


import org.canova.api.records.reader.RecordReader;
import org.canova.api.split.FileSplit;
import org.canova.image.recordreader.ImageRecordReader;
import org.deeplearning4j.datasets.canova.RecordReaderDataSetIterator;
import org.deeplearning4j.datasets.iterator.DataSetPreProcessor;
import org.deeplearning4j.datasets.iterator.SamplingDataSetIterator;
import org.deeplearning4j.datasets.iterator.impl.ListDataSetIterator;
import org.deeplearning4j.datasets.rearrange.LocalUnstructuredDataFormatter;
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
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.MiniBatchFileDataSetIterator;
import org.nd4j.linalg.dataset.SplitTestAndTrain;
import org.nd4j.linalg.dataset.ViewIterator;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.dataset.api.iterator.StandardScaler;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Hello world!
 *
 */
public class AppLocal {
    public static void main(String[] args) throws Exception {
        // Path to the labeled images
        Nd4j.factory().setDType(DataBuffer.Type.FLOAT);
        Nd4j.dtype = DataBuffer.Type.FLOAT;
        String labeledPath = System.getProperty("user.home")+ File.separator + "data";
        String testTrainSplitPath = System.getProperty("user.home") + "/splittesttrain";
        File splitTestTrainRoot = new File(testTrainSplitPath);
        if(!splitTestTrainRoot.exists()) {
            LocalUnstructuredDataFormatter formatter = new LocalUnstructuredDataFormatter(splitTestTrainRoot,new File(labeledPath), LocalUnstructuredDataFormatter.LabelingType.DIRECTORY,0.8);
            formatter.rearrange();
        }
        List<String> labels = new ArrayList<>(Arrays.asList("beach", "desert", "forest", "mountain", "rain", "snow"));
        //List<String> labels = new ArrayList<>(Arrays.asList("mountain", "rain"));
        final int numRows = 75;
        final int numColumns = 75;
        int nChannels = 3;
        int outputNum = labels.size();
        int batchSize = 1000;
        int iterations = 1;
        int seed = 123;

        Nd4j.ENFORCE_NUMERICAL_STABILITY = true;
        // Instantiating a RecordReader pointing to the data path with the specified
        // height and width for each image.
        int nIn = numRows * numColumns * nChannels;

        StandardScaler scaler = new StandardScaler();

        //setup the network
        MultiLayerConfiguration.Builder builder = new NeuralNetConfiguration.Builder()
                .seed(seed)
                .batchSize(batchSize)
                .iterations(iterations).regularization(true)
                .l1(1e-1).l2(2e-4).useDropConnect(true)
                .constrainGradientToUnitNorm(true).miniBatch(true)
                .optimizationAlgo(OptimizationAlgorithm.CONJUGATE_GRADIENT)
                .list(6)
                .layer(0, new ConvolutionLayer.Builder(5, 5)
                        .nOut(5).dropOut(0.5)
                        .weightInit(WeightInit.XAVIER)
                        .activation("relu")
                        .build())

                .layer(1, new SubsamplingLayer
                        .Builder(SubsamplingLayer.PoolingType.MAX, new int[]{2, 2})
                        .build())
                .layer(2, new ConvolutionLayer.Builder(3, 3)
                        .nOut(10).dropOut(0.5)
                        .weightInit(WeightInit.XAVIER)
                        .activation("relu")
                        .build())
                .layer(3, new SubsamplingLayer
                        .Builder(SubsamplingLayer.PoolingType.MAX, new int[]{2, 2})
                        .build())
                .layer(4, new DenseLayer.Builder().nOut(100).activation("relu")
                        .build())

                .layer(5, new OutputLayer.Builder(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD)
                        .nOut(outputNum)
                        .weightInit(WeightInit.XAVIER)
                        .activation("softmax")
                        .build())
                .backprop(true).pretrain(false);

        new ConvolutionLayerSetup(builder,numRows,numColumns,nChannels);
        MultiLayerConfiguration conf = builder.build();


        MultiLayerNetwork trainedNetwork = new MultiLayerNetwork(conf);
        trainedNetwork.init();
        trainedNetwork.setListeners(new ScoreIterationListener(1));


        RecordReader trainReader = new ImageRecordReader(numRows,numColumns,nChannels,true);
        trainReader.initialize(new FileSplit(new File(new File(splitTestTrainRoot, "split"), "train")));
        DataSetIterator iter;
        File meanFile = new File("mean.bin");
        File stdFile = new File("std.bin");
        if(!meanFile.exists() || !stdFile.exists()) {
            iter   = new RecordReaderDataSetIterator(trainReader,10000,numColumns * numRows * nChannels,6);
            scaler.fit(iter.next());
            scaler.save(meanFile,stdFile);
        }
        else {
            scaler.load(meanFile,stdFile);
        }

        RecordReader testReader = new ImageRecordReader(numRows,numColumns,nChannels,true);
        testReader.initialize(new FileSplit(new File(new File(splitTestTrainRoot, "split"), "test")));


        System.out.println("Begin training");
        DataSet trainingSet;
        File training = new File("train.bin");
        if(!training.exists()) {
            iter = new RecordReaderDataSetIterator(trainReader,10000,numColumns * numRows * nChannels,6);
            trainingSet = iter.next();
            trainingSet.save(training);
        }
        else {
            trainingSet = new DataSet();
            trainingSet.load(training);
        }

        DataSetIterator trainIter = new SamplingDataSetIterator(trainingSet,100,10000);
        System.out.println("Loading test data");
        DataSet testNext = null;
        File testSet = new File("test.bin");
        if(!testSet.exists()) {
            DataSetIterator testIter = new RecordReaderDataSetIterator(trainReader,10000,numColumns * numRows * nChannels,6);
            testNext = testIter.next();
            testNext.save(testSet);
        }
        else {
            testNext = new DataSet();
            testNext.load(testSet);
        }

        trainingSet.shuffle();


        scaler.transform(trainingSet);
        scaler.transform(testNext);
        System.out.println("Scaled data");
        while(trainIter.hasNext()) {
            DataSet next = trainIter.next();
            System.out.println("Loaded data with label distribution " + next.labelCounts());
            trainedNetwork.fit(next);
            System.out.println("Evaluating so far");
            Evaluation evaluation = new Evaluation(labels.size());
            DataSet testIterNext = testNext.sample(100,true);
            evaluation.eval(testIterNext.getLabels(), trainedNetwork.output(testIterNext.getFeatureMatrix(), true));
            System.out.println(evaluation.stats());
            System.out.println("One batch done with score " + trainedNetwork.score());

            System.out.println(evaluation.stats());

        }
      /*


       ;
        System.out.println(evaluation.stats());*/



    }




}
