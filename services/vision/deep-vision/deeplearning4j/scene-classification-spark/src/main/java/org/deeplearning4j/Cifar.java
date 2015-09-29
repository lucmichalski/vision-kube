package org.deeplearning4j;

import org.apache.commons.io.FileUtils;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
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
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Hello world!
 *
 */
public class Cifar {
    public static void main( String[] args) throws Exception {
        final JavaSparkContext sc = new JavaSparkContext(new SparkConf().setMaster("local[*]").set("spark.driver.maxResultSize","3g")
                .setAppName("scenes"));
        DataSet d = new DataSet();
        d.load(new File("cifar-train.bin"));
        d = (DataSet) d.getRange(0,10).copy();
        List<DataSet> ciFarList = d.asList();


        //System.out.println("Loaded " + next.numExamples() + " with num features " + next.getLabels().columns());


        JavaRDD<DataSet> dataSetJavaRDD = sc.parallelize(ciFarList, ciFarList.size());


        //train test split 60/40
        System.out.println("Setup data with train test split");
        //setup the network
        MultiLayerConfiguration.Builder builder = new NeuralNetConfiguration.Builder()
                .seed(123)
                .batchSize(100)
                .iterations(5).regularization(true)
                .l1(1e-1).l2(2e-4).useDropConnect(true)
                .constrainGradientToUnitNorm(true).miniBatch(false)
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
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
                        .nOut(10)
                        .weightInit(WeightInit.XAVIER)
                        .activation("softmax")
                        .build())
                .backprop(true).pretrain(false);

        new ConvolutionLayerSetup(builder,32,32,3);
        MultiLayerConfiguration conf = builder.build();

        //train the network
        SparkDl4jMultiLayer trainLayer = new SparkDl4jMultiLayer(sc.sc(),conf);
        for(int i = 0; i < 5; i++) {
            //fit on the training set
            MultiLayerNetwork trainedNetwork = trainLayer.fitDataSet(dataSetJavaRDD);


            System.out.println("Saving model...");

            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream("model.bin"));
            Nd4j.write(bos, trainedNetwork.params());
            bos.flush();
            bos.close();
            FileUtils.write(new File("conf.yaml"), trainedNetwork.conf().toYaml());


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
