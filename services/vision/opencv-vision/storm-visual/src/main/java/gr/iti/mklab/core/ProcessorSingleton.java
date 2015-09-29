package gr.iti.mklab.core;

import gr.iti.mklab.visual.aggregation.VladAggregatorMultipleVocabularies;
import gr.iti.mklab.visual.datastructures.AbstractSearchStructure;
import gr.iti.mklab.visual.datastructures.Linear;
import gr.iti.mklab.visual.dimreduction.PCA;
import gr.iti.mklab.visual.extraction.AbstractFeatureExtractor;
import gr.iti.mklab.visual.extraction.SURFExtractor;
import gr.iti.mklab.visual.vectorization.ImageVectorization;
import gr.iti.mklab.visual.vectorization.ImageVectorizationResult;
import gr.iti.mklab.visual.vectorization.ImageVectorizationTrain;

/**
 * The multimedia indexing framework wrapper class
 *
 * Created by kandreadou on 4/11/14.
 */
public class ProcessorSingleton {

    protected static int maxNumPixels = 768 * 512;
    protected static int targetLengthMax = 1024;
    protected static AbstractSearchStructure index;

    private static ProcessorSingleton instance;

    public static synchronized ProcessorSingleton get() {
        if (instance == null) {
            instance = new ProcessorSingleton();
        }
        return instance;
    }

    private ProcessorSingleton() {
        try {
            String learningFolder = "/home/kandreadou/webservice/learning_files/";


            int[] numCentroids = {128, 128, 128, 128};
            int initialLength = numCentroids.length * numCentroids[0] * AbstractFeatureExtractor.SURFLength;

            String[] codebookFiles = {
                    learningFolder + "surf_l2_128c_0.csv",
                    learningFolder + "surf_l2_128c_1.csv",
                    learningFolder + "surf_l2_128c_2.csv",
                    learningFolder + "surf_l2_128c_3.csv"
            };

            String pcaFile = learningFolder + "pca_surf_4x128_32768to1024.txt";

            //Initialize the ImageVectorization
            SURFExtractor extractor = new SURFExtractor();

            ImageVectorization.setFeatureExtractor(extractor);
            ImageVectorization.setVladAggregator(new VladAggregatorMultipleVocabularies(codebookFiles,
                    numCentroids, AbstractFeatureExtractor.SURFLength));


            if (targetLengthMax < initialLength) {
                System.out.println("targetLengthMax : " + targetLengthMax + " initialLengh " + initialLength);
                PCA pca = new PCA(targetLengthMax, 1, initialLength, true);
                pca.loadPCAFromFile(pcaFile);
                ImageVectorization.setPcaProjector(pca);

            }

            String BDBEnvHome = learningFolder + "storm_" + targetLengthMax;
            index = new Linear(targetLengthMax, 1000, false, BDBEnvHome, true,
                    true, 0);
        } catch (Exception ex) {
            System.out.println("Exception on initialization " + ex);
        }
    }

    public double[] getVector(String imageFolder, String imageFilename) {
        try {

            ImageVectorization imvec = new ImageVectorization(imageFolder, imageFilename, targetLengthMax, maxNumPixels);

            ImageVectorizationResult imvr = imvec.call();
            return imvr.getImageVector();
        } catch (Exception ex) {
            System.out.println("Exception " + ex);
        }
        return null;
    }

    public boolean index(String id, double[] vector){
        try {

            return index.indexVector(id, vector);
        } catch (Exception ex) {
            System.out.println("Exception " + ex);
        }
        return false;

    }

}
