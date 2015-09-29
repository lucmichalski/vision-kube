package org.deeplearning4j;

import org.canova.api.records.reader.RecordReader;
import org.canova.api.split.FileSplit;
import org.canova.image.recordreader.ImageRecordReader;
import org.deeplearning4j.datasets.canova.RecordReaderDataSetIterator;
import org.deeplearning4j.datasets.iterator.SamplingDataSetIterator;
import org.deeplearning4j.datasets.iterator.impl.ListDataSetIterator;
import org.deeplearning4j.datasets.rearrange.LocalUnstructuredDataFormatter;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.ConvolutionLayer;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.conf.layers.SubsamplingLayer;
import org.deeplearning4j.nn.conf.layers.setup.ConvolutionLayerSetup;
import org.deeplearning4j.nn.weights.WeightInit;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.dataset.api.iterator.StandardScaler;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by agibsonccc on 9/21/15.
 */
public class DataSetSetup {

    // Path to the labeled images

    List<String> labels = new ArrayList<>(Arrays.asList("beach", "desert", "forest", "mountain", "rain", "snow"));
    //List<String> labels = new ArrayList<>(Arrays.asList("mountain", "rain"));
    final int numRows = 75;
    final int numColumns = 75;
    int nChannels = 3;
    int outputNum = labels.size();
    int batchSize = 1000;
    int iterations = 1;
    int seed = 123;
    private DataSetIterator trainIter,testIter;
    private MultiLayerConfiguration conf;


    public static MultiLayerConfiguration convolutionConf() {
        List<String> labels = new ArrayList<>(Arrays.asList("beach", "desert", "forest", "mountain", "rain", "snow"));

        final int numRows = 75;
        final int numColumns = 75;
        int nChannels = 3;
        int outputNum = labels.size();
        int batchSize = 1000;
        int iterations = 1;
        int seed = 123;

        //setup the network
        MultiLayerConfiguration.Builder builder = new NeuralNetConfiguration.Builder()
                .seed(seed)
                .batchSize(batchSize)
                .iterations(iterations).regularization(true)
                .l1(1e-1).l2(2e-4).useDropConnect(true)
                .constrainGradientToUnitNorm(true).miniBatch(true)
                .optimizationAlgo(OptimizationAlgorithm.LINE_GRADIENT_DESCENT)
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
                        .nOut(20)
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
        return builder.build();
    }

    public void setConf() {
        conf = DataSetSetup.convolutionConf();
    }


    public void setup() throws Exception {
        StandardScaler scaler = new StandardScaler();

        Nd4j.factory().setDType(DataBuffer.Type.FLOAT);
        Nd4j.dtype = DataBuffer.Type.FLOAT;
        String labeledPath = System.getProperty("user.home")+ File.separator + "data";
        String testTrainSplitPath = System.getProperty("user.home") + "/splittesttrain";
        File splitTestTrainRoot = new File(testTrainSplitPath);
        if(!splitTestTrainRoot.exists()) {
            LocalUnstructuredDataFormatter formatter = new LocalUnstructuredDataFormatter(splitTestTrainRoot,new File(labeledPath), LocalUnstructuredDataFormatter.LabelingType.DIRECTORY,0.8);
            formatter.rearrange();
        }


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

        trainIter = new SamplingDataSetIterator(trainingSet,10000,10000);
        System.out.println("Loading test data");
        DataSet testNext = null;
        File testSet = new File("test.bin");
        if(!testSet.exists()) {
            testIter = new RecordReaderDataSetIterator(trainReader,10000,numColumns * numRows * nChannels,6);
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
        setConf();
        testIter = new ListDataSetIterator(testNext.asList(),100);
        //reduce number of samles
        testIter = new ListDataSetIterator(testIter.next().asList(),100);

    }

    public MultiLayerConfiguration getConf() {
        return conf;
    }

    public DataSetIterator getTrainIter() {
        return trainIter;
    }

    public void setTrainIter(DataSetIterator trainIter) {
        this.trainIter = trainIter;
    }

    public DataSetIterator getTestIter() {
        return testIter;
    }

    public void setTestIter(DataSetIterator testIter) {
        this.testIter = testIter;
    }
}
