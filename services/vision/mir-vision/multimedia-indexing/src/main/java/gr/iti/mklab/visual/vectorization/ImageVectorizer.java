package gr.iti.mklab.visual.vectorization;

import gr.iti.mklab.visual.aggregation.AbstractFeatureAggregator;
import gr.iti.mklab.visual.aggregation.VladAggregatorMultipleVocabularies;
import gr.iti.mklab.visual.dimreduction.PCA;
import gr.iti.mklab.visual.extraction.AbstractFeatureExtractor;
import gr.iti.mklab.visual.extraction.ColorSURFExtractor;
import gr.iti.mklab.visual.extraction.RootSIFTExtractor;
import gr.iti.mklab.visual.extraction.SIFTExtractor;
import gr.iti.mklab.visual.extraction.SURFExtractor;

import java.awt.image.BufferedImage;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * This class implements multi-threaded image vectorization.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 * 
 */
public class ImageVectorizer {

	private ExecutorService vectorizationExecutor;

	private CompletionService<ImageVectorizationResult> pool;

	/** The current number of tasks whose termination is pending. **/
	private int numPendingTasks;

	/** The target length of the extracted vector. **/
	private int targetVectorLength;

	/** The initial length of the vector. **/
	private int initialVectorLength;

	public int getInitialVectorLength() {
		return initialVectorLength;
	}

	/**
	 * Image will be scaled at this maximum number of pixels before vectorization.
	 */
	private int maxImageSizeInPixels = 1024 * 768;

	public void setMaxImageSizeInPixels(int maxImageSizeInPixels) {
		this.maxImageSizeInPixels = maxImageSizeInPixels;
	}

	/**
	 * The maximum allowable number of pending tasks, used to limit the memory usage.
	 */
	private final int maxNumPendingTasks;

	/**
	 * Constructor of the multi-threaded vectorization class.
	 * 
	 * @param featureType
	 *            the features to be extracted (surf/sift/rootsift/csurf)
	 * @param codebooksFiles
	 *            a String array with full paths to the codebook files
	 * @param numCentroids
	 *            an int array with the number of centroids in each codebook
	 * @param projectionLength
	 *            the length at which the vectors are projected
	 * @param PCAFileName
	 *            the file containing the PCA projection matrix
	 * @param whitening
	 *            whether whitening should be applied jointly with PCA projection
	 * @param numThreads
	 *            the number of vectorization threads to use
	 * @throws Exception
	 */
	public ImageVectorizer(String featureType, String[] codebookFiles, int[] numCentroids,
			int projectionLength, String PCAFileName, boolean whitening, int numThreads) throws Exception {
		int featureLength;

		AbstractFeatureExtractor fe = null;
		if (featureType.equals("surf")) {
			featureLength = AbstractFeatureExtractor.SURFLength;
			fe = new SURFExtractor();
		} else if (featureType.equals("sift")) {
			featureLength = AbstractFeatureExtractor.SIFTLength;
			fe = new SIFTExtractor();
		} else if (featureType.equals("rootsift")) {
			featureLength = AbstractFeatureExtractor.SIFTLength;
			fe = new RootSIFTExtractor();
		} else if (featureType.equals("csurf")) {
			featureLength = 3 * AbstractFeatureExtractor.SURFLength;
			fe = new ColorSURFExtractor();
		} else {
			throw new Exception("Wrong feature type;");
		}
		ImageVectorization.setFeatureExtractor(fe);

		int numCodebooks = codebookFiles.length;
		// initialize the VLAD object
		double[][][] codebooks = new double[numCodebooks][][];
		for (int i = 0; i < numCodebooks; i++) {
			codebooks[i] = AbstractFeatureAggregator.readQuantizer(codebookFiles[i], numCentroids[i],
					featureLength);
			initialVectorLength += codebooks[i].length * featureLength;
		}
		targetVectorLength = projectionLength;

		VladAggregatorMultipleVocabularies vladmvoc = new VladAggregatorMultipleVocabularies(codebooks);
		ImageVectorization.setVladAggregator(vladmvoc);

		// initialize the PCA object
		PCA PCA = null;
		if (PCAFileName != null && projectionLength < initialVectorLength) {
			// initialize the PCA object
			PCA = new PCA(projectionLength, 1, initialVectorLength, whitening);
			PCA.loadPCAFromFile(PCAFileName);
		}
		ImageVectorization.setPcaProjector(PCA);

		vectorizationExecutor = Executors.newFixedThreadPool(numThreads);
		pool = new ExecutorCompletionService<ImageVectorizationResult>(vectorizationExecutor);
		numPendingTasks = 0;
		maxNumPendingTasks = numThreads * 10;
	}

	/**
	 * Submits a new image vectorization task for an image that is stored in the disk and has not yet been
	 * read into a BufferedImage object.
	 * 
	 * @param imageFolder
	 *            The folder where the image resides.
	 * @param imageName
	 *            The name of the image.
	 */
	public void submitImageVectorizationTask(String imageFolder, String imageName) {
		Callable<ImageVectorizationResult> call = new ImageVectorization(imageFolder, imageName,
				targetVectorLength, maxImageSizeInPixels);
		pool.submit(call);
		numPendingTasks++;
	}

	/**
	 * This methods submits an image vectorization task for an image that has already been read into a
	 * BufferedImage object.
	 * 
	 * @param imageName
	 *            The name of the image.
	 * @param im
	 *            The BufferedImage object of the image.
	 */
	public void submitImageVectorizationTask(String imageName, BufferedImage im) {
		Callable<ImageVectorizationResult> call = new ImageVectorization(imageName, im, targetVectorLength,
				maxImageSizeInPixels);
		pool.submit(call);
		numPendingTasks++;
	}

	/**
	 * Takes and returns a vectorization result from the pool.
	 * 
	 * @return the vectorization result, or null in no results are ready
	 * @throws Exception
	 *             for a failed vectorization task
	 */
	public ImageVectorizationResult getImageVectorizationResult() throws Exception {
		Future<ImageVectorizationResult> future = pool.poll();
		if (future == null) {
			return null;
		} else {
			try {
				ImageVectorizationResult imvr = future.get();
				return imvr;
			} catch (Exception e) {
				throw e;
			} finally {
				// in any case (Exception or not) the numPendingTask should be reduced
				numPendingTasks--;
			}
		}
	}

	/**
	 * Gets an image vectorization result from the pool, waiting if necessary.
	 * 
	 * @return the vectorization result
	 * @throws Exception
	 *             for a failed vectorization task
	 */
	public ImageVectorizationResult getImageVectorizationResultWait() throws Exception {
		try {
			ImageVectorizationResult imvr = pool.take().get();
			return imvr;
		} catch (Exception e) {
			throw e;
		} finally {
			// in any case (Exception or not) the numPendingTask should be reduced
			numPendingTasks--;
		}
	}

	/**
	 * Returns true if the number of pending tasks is smaller than the maximum allowable number.
	 * 
	 * @return
	 */
	public boolean canAcceptMoreTasks() {
		if (numPendingTasks < maxNumPendingTasks) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Shuts the vectorization executor down, waiting for up to 10 seconds for the remaining tasks to
	 * complete. See http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/ExecutorService.html
	 * 
	 */
	public void shutDown() {
		vectorizationExecutor.shutdown(); // Disable new tasks from being submitted
		try {
			// Wait a while for existing tasks to terminate
			if (!vectorizationExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
				vectorizationExecutor.shutdownNow(); // Cancel currently executing tasks
				// Wait a while for tasks to respond to being cancelled
				if (!vectorizationExecutor.awaitTermination(10, TimeUnit.SECONDS))
					System.err.println("Pool did not terminate");
			}
		} catch (InterruptedException ie) {
			// (Re-)Cancel if current thread also interrupted
			vectorizationExecutor.shutdownNow();
			// Preserve interrupt status
			Thread.currentThread().interrupt();
		}
	}

}
