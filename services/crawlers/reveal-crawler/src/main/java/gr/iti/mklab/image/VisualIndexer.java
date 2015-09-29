package gr.iti.mklab.image;

import gr.iti.mklab.simmo.items.Image;
import gr.iti.mklab.simmo.morphia.MediaDAO;
import gr.iti.mklab.simmo.morphia.MorphiaManager;
import gr.iti.mklab.visual.aggregation.AbstractFeatureAggregator;
import gr.iti.mklab.visual.aggregation.VladAggregatorMultipleVocabularies;
import gr.iti.mklab.visual.datastructures.AbstractSearchStructure;
import gr.iti.mklab.visual.datastructures.IVFPQ;
import gr.iti.mklab.visual.datastructures.PQ;
import gr.iti.mklab.visual.dimreduction.PCA;
import gr.iti.mklab.visual.extraction.AbstractFeatureExtractor;
import gr.iti.mklab.visual.extraction.SURFExtractor;
import gr.iti.mklab.visual.utilities.ImageIOGreyScale;
import gr.iti.mklab.visual.vectorization.ImageVectorization;
import gr.iti.mklab.visual.vectorization.ImageVectorizationResult;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created by kandreadou on 12/1/14.
 */
public class VisualIndexer {

    private MediaDAO<Image> imageDAO;

    private static final Logger LOGGER = LoggerFactory.getLogger(VisualIndexer.class);
    private final static String LEARNING_FOLDER = "/home/kandreadou/webservice/learning_files/";
    private final static String INDEX_FOLDER = "/home/kandreadou/webservice/reveal_indices/bubing/";

    /**
     * The value to use in HttpURLConnection.setConnectTimeout()
     */
    public static final int connectionTimeout = 3000;
    /**
     * The value to use in HttpURLConnection.setReadTimeout()
     */
    public static final int readTimeout = 2000;

    private static VisualIndexer uniqueInstance;
    private static IVFPQ index;

    private static int targetLengthMax = 1024;
    private static int maxNumPixels = 768 * 512;

    public static synchronized VisualIndexer getInstance() {
        if (uniqueInstance == null)
            uniqueInstance = new VisualIndexer();
        return uniqueInstance;
    }

    private VisualIndexer() {
        try {
            initialize();
            MorphiaManager.setup("test");
            imageDAO = new MediaDAO<Image>(Image.class);
        } catch (Exception ex) {
            LOGGER.error("Error creating VisualIndexer " + ex);
        }
    }

    public void downloadIndexAndStore(Image item) {
        String urlLine = item.getUrl();
        try {
            BufferedImage im = downloadImage(urlLine);
            indexAndStore(im, item);
        } catch (Exception ex) {
            System.out.println("Exception on download " + ex);
        }

    }

    public void indexAndStore(BufferedImage im, Image obj) throws Exception {
        ImageVectorization imvec = new ImageVectorization(obj.getObjectId().toString(), im, targetLengthMax, maxNumPixels);
        ImageVectorizationResult result = imvec.call();
        if (StringUtils.isEmpty(result.getExceptionMessage())) {
            String id = result.getImageName();
            double[] vector = result.getImageVector();
            //System.out.println("Vectorization Result: " + vector.length + " " + id);
            if (index.indexVector(id, vector))
                imageDAO.save(obj);
        }
    }

    private BufferedImage downloadImage(String imageUrl) throws Exception {
        BufferedImage image = null;
        InputStream in = null;
        try { // first try reading with the default class
            URL url = new URL(imageUrl);
            HttpURLConnection conn = null;
            boolean success = false;
            try {
                conn = (HttpURLConnection) url.openConnection();
                conn.setInstanceFollowRedirects(true);
                conn.setConnectTimeout(connectionTimeout); // TO DO: add retries when connections times out
                conn.setReadTimeout(readTimeout);
                conn.connect();
                success = true;
            } catch (Exception e) {
                System.out.println("Connection related exception at url: " + imageUrl);
            } finally {
                if (!success) {
                    conn.disconnect();
                }
            }
            success = false;
            try {
                in = conn.getInputStream();
                success = true;
            } catch (Exception e) {
                System.out.println("Exception when getting the input stream from the connection at url: "
                        + imageUrl);
            } finally {
                if (!success) {
                    in.close();
                }
            }
            image = ImageIO.read(in);
        } catch (IllegalArgumentException e) {
            // this exception is probably thrown because of a greyscale jpeg image
            System.out.println("Exception: " + e.getMessage() + " | Image: " + imageUrl);
            image = ImageIOGreyScale.read(in); // retry with the modified class
        } catch (MalformedURLException e) {
            System.out.println("Malformed url exception. Url: " + imageUrl);
        }
        return image;
    }

    private void initialize() throws Exception {
        int[] numCentroids = {128, 128, 128, 128};
        int initialLength = numCentroids.length * numCentroids[0] * AbstractFeatureExtractor.SURFLength;

        String[] codebookFiles = {
                LEARNING_FOLDER + "surf_l2_128c_0.csv",
                LEARNING_FOLDER + "surf_l2_128c_1.csv",
                LEARNING_FOLDER + "surf_l2_128c_2.csv",
                LEARNING_FOLDER + "surf_l2_128c_3.csv"
        };
        String pcaFile = LEARNING_FOLDER + "pca_surf_4x128_32768to1024.txt";
        ImageVectorization.setFeatureExtractor(new SURFExtractor());
        double[][][] codebooks = AbstractFeatureAggregator.readQuantizers(codebookFiles, numCentroids,
                AbstractFeatureExtractor.SURFLength);
        ImageVectorization.setVladAggregator(new VladAggregatorMultipleVocabularies(codebooks));
        if (targetLengthMax < initialLength) {
            PCA pca = new PCA(targetLengthMax, 1, initialLength, true);
            pca.loadPCAFromFile(pcaFile);
            ImageVectorization.setPcaProjector(pca);
        }
        int maximumNumVectors = 10000;
        int m2 = 64;
        int k_c = 256;
        int numCoarseCentroids = 8192;
        String coarseQuantizerFile2 = LEARNING_FOLDER + "qcoarse_1024d_8192k.csv";
        String productQuantizerFile2 = LEARNING_FOLDER + "pq_1024_64x8_rp_ivf_8192k.csv";
        index = new IVFPQ(targetLengthMax, maximumNumVectors, false, INDEX_FOLDER, m2, k_c, PQ.TransformationType.RandomPermutation, numCoarseCentroids, true, 0);
        index.loadCoarseQuantizer(coarseQuantizerFile2);
        index.loadProductQuantizer(productQuantizerFile2);
        int w = 64; // larger values will improve results/increase seach time
        index.setW(w); // how many (out of 8192) lists should be visited during search.
    }
}
