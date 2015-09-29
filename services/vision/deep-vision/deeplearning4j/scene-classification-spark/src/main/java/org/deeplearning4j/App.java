package org.deeplearning4j;

import org.apache.commons.io.FileUtils;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.input.PortableDataStream;
import org.apache.spark.mllib.evaluation.MulticlassMetrics;
import org.apache.spark.mllib.feature.StandardScaler;
import org.apache.spark.mllib.feature.StandardScalerModel;
import org.apache.spark.mllib.linalg.Vector;
import org.apache.spark.mllib.regression.LabeledPoint;
import org.canova.image.recordreader.ImageRecordReader;
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
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.spark.impl.multilayer.SparkDl4jMultiLayer;
import org.deeplearning4j.spark.util.MLLibUtil;
import org.deeplearning4j.util.StringUtils;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import scala.Tuple2;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Hello world!
 *
 */
public class App {
    public static void main( String[] args) throws Exception {
        List<String> labels = Arrays.asList("beach", "desert", "forest", "mountain", "rain", "snow");

        final int numRows = 75;
        final int numColumns = 75;
        int nChannels = 3;
        int outputNum = labels.size();
        int batchSize = 100;
        int iterations = 10;
        int seed = 123;
        String pathsFile = "s3n://dl4j-distribution/paths.txt";
        final JavaSparkContext sc = new JavaSparkContext(new SparkConf().setMaster("local[*]").setAppName("scenes"));
        //load the images from the bucket setting the size to 28 x 28
        final String s3Bucket = "file:///home/ec2-user of :P/data/";
        //normalize the data to zero mean and unit variance
        String csv = StringUtils.join(",",sc.textFile(pathsFile).map(new Function<String, String>() {
            @Override
            public String call(String v1) throws Exception {
                return s3Bucket + v1;
            }
        }).collect());

       System.out.println(csv.split(",").length);

        JavaRDD<LabeledPoint> data = MLLibUtil.fromBinary(sc.binaryFiles(s3Bucket + "/*")
                , new ImageRecordReader(numRows, numColumns, nChannels,true,labels));
        StandardScaler scaler = new StandardScaler(true,true);

        final StandardScalerModel scalarModel = scaler.fit(data.map(new Function<LabeledPoint, Vector>() {
            @Override
            public Vector call(LabeledPoint v1) throws Exception {
                return v1.features();
            }
        }).rdd());
        //get the trained data for the train/test split
        JavaRDD<LabeledPoint> normalizedData = data.map(new Function<LabeledPoint, LabeledPoint>() {
            @Override
            public LabeledPoint call(LabeledPoint v1) throws Exception {
                Vector features = v1.features();
                Vector normalized = scalarModel.transform(features);
                return new LabeledPoint(v1.label(), normalized);
            }
        }).cache();


        //train test split 60/40
        JavaRDD<LabeledPoint>[] trainTestSplit = normalizedData.randomSplit(new double[]{80, 20});



        //setup the network
        MultiLayerConfiguration.Builder builder = new NeuralNetConfiguration.Builder()
                .seed(seed)
                .batchSize(batchSize)
                .iterations(iterations).regularization(true)
                .l1(1e-1).l2(2e-4).useDropConnect(true)
                .constrainGradientToUnitNorm(true).miniBatch(true)
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
                        .nOut(outputNum)
                        .weightInit(WeightInit.XAVIER)
                        .activation("softmax")
                        .build())
                .backprop(true).pretrain(false);

        new ConvolutionLayerSetup(builder,numRows,numColumns,nChannels);
        MultiLayerConfiguration conf = builder.build();
        //train the network
        SparkDl4jMultiLayer trainLayer = new SparkDl4jMultiLayer(sc.sc(),conf);
        //fit on the training set
        MultiLayerNetwork trainedNetwork = trainLayer.fit(trainTestSplit[0],100);
        final SparkDl4jMultiLayer trainedNetworkWrapper = new SparkDl4jMultiLayer(sc.sc(),trainedNetwork);

        // Compute raw scores on the test set.
        JavaRDD<Tuple2<Double, Double>> predictionAndLabels = trainTestSplit[1].map(
                new Function<LabeledPoint, Tuple2<Double, Double>>() {
                    public Tuple2<Double, Double> call(LabeledPoint p) {
                        Vector prediction = trainedNetworkWrapper.predict(p.features());
                        double max = 0;
                        double idx = 0;
                        for(int i = 0; i < prediction.size(); i++) {
                            if(prediction.apply(i) > max) {
                                idx = i;
                                max = prediction.apply(i);
                            }
                        }

                        return new Tuple2<>(idx, p.label());
                    }
                }
        );

        System.out.println("Saving model...");

        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream("model.bin"));
        Nd4j.write(bos,trainedNetwork.params());
        bos.flush();
        bos.close();
        FileUtils.write(new File("conf.yaml"),trainedNetwork.conf().toYaml());



        // Get evaluation metrics.
      /*  MulticlassMetrics metrics = new MulticlassMetrics(predictionAndLabels.rdd());
        double precision = metrics.fMeasure();
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream("model.bin"));
        Nd4j.write(bos,trainedNetwork.params());
*/       // FileUtils.write(new File("conf.yaml"),trainedNetwork.conf().toYaml());
        //System.out.println("F1 = " + precision);


    }
}
