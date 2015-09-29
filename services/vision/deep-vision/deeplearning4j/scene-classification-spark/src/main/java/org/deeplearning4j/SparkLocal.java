package org.deeplearning4j;

import org.apache.commons.io.FileUtils;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.mllib.feature.StandardScaler;
import org.apache.spark.mllib.feature.StandardScalerModel;
import org.apache.spark.mllib.linalg.Vector;
import org.apache.spark.mllib.regression.LabeledPoint;
import org.canova.image.recordreader.ImageRecordReader;
import org.deeplearning4j.datasets.iterator.DataSetIterator;
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
import org.deeplearning4j.spark.impl.multilayer.SparkDl4jMultiLayer;
import org.deeplearning4j.spark.util.MLLibUtil;
import org.deeplearning4j.util.StringUtils;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import scala.Tuple2;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Hello world!
 *
 */
public class SparkLocal {
    public static void main( String[] args) throws Exception {
        final JavaSparkContext sc = new JavaSparkContext(new SparkConf().setMaster("local[*]").set("spark.driver.maxResultSize","3g")
                .setAppName("scenes"));
        DataSetSetup setSetup = new DataSetSetup();
        setSetup.setup();
        DataSet next = setSetup.getTrainIter().next();
        next.shuffle();
        List<DataSet> list = new ArrayList<>();
        for(int i  = 0; i < next.numExamples(); i++) {
            list.add(next.get(i).copy());
        }

        next = null;



        //System.out.println("Loaded " + next.numExamples() + " with num features " + next.getLabels().columns());


        JavaRDD<DataSet> dataSetJavaRDD = sc.parallelize(list,list.size() / 100);


        //train test split 60/40
        System.out.println("Setup data with train test split");


        MultiLayerConfiguration conf = setSetup.getConf();
        //train the network
        SparkDl4jMultiLayer trainLayer = new SparkDl4jMultiLayer(sc.sc(),conf);
        for(int i = 0; i < 5; i++) {
            //fit on the training set
            MultiLayerNetwork trainedNetwork = trainLayer.fitDataSet(dataSetJavaRDD);


            System.out.println("Saving model...");

            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream("model.bin"));
            DataOutputStream dos = new DataOutputStream(bos);
            Nd4j.write(trainedNetwork.params(),dos);
            bos.flush();
            bos.close();
            FileUtils.write(new File("conf.yaml"), trainedNetwork.conf().toYaml());

            System.out.println("Testing...");
            Evaluation evaluation = new Evaluation();

            DataSetIterator testIter23 = (DataSetIterator) setSetup.getTestIter();
            while(testIter23.hasNext()) {
                DataSet testNext = testIter23.next();
                evaluation.eval(testNext.getLabels(),trainedNetwork.output(testNext.getFeatureMatrix(),true));
            }

            testIter23.reset();
            System.out.println(evaluation.stats());
        }


        // Get evaluation metrics.
      /*  MulticlassMetrics metrics = new MulticlassMetrics(predictionAndLabels.rdd());
        double precision = metrics.fMeasure();
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream("model.bin"));
        Nd4j.write(bos,trainedNetwork.params());
*/       // FileUtils.write(new File("conf.yaml"),trainedNetwork.conf().toYaml());
        //System.out.println("F1 = " + precision);


    }
}
