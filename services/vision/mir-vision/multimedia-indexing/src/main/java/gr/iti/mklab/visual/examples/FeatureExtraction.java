package gr.iti.mklab.visual.examples;

import gr.iti.mklab.visual.extraction.AbstractFeatureExtractor;
import gr.iti.mklab.visual.extraction.ColorSURFExtractor;
import gr.iti.mklab.visual.extraction.ImageScaling;
import gr.iti.mklab.visual.extraction.RootSIFTExtractor;
import gr.iti.mklab.visual.extraction.SIFTExtractor;
import gr.iti.mklab.visual.extraction.SURFExtractor;
import gr.iti.mklab.visual.utilities.FeatureIO;
import gr.iti.mklab.visual.utilities.ImageIOGreyScale;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FilenameFilter;

import javax.imageio.ImageIO;

/**
 * Extracts features from images (.jpg or .png files) contained in a directory and writes them in a file for
 * each image. The feature files are written in a directory (named by the name of the feature) that is created
 * (if it does not already exist) in the parent directory of the directory were the images reside.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 * 
 */
public class FeatureExtraction {

	/**
	 * 
	 * @param args
	 *            [0] Full path to the images folder.
	 * @param args
	 *            [1] Maximum number of images to perform feature extraction on, i.e. only the top N images
	 *            returned by File.list will be processed.
	 * @param args
	 *            [2] Number of images to be skipped from extraction (usually 0).
	 * @param args
	 *            [3] Maximum number of pixels that each image will be scaled to (e.g. 196608 for 512x384).
	 * @param args
	 *            [4] Type of features to extract (surf/sift/rootsift/csurf).
	 * @param args
	 *            [5] Whether the features should be written in textual format (txt) / binary format (bin) or
	 *            not written (no).
	 * @throws Exception
	 */
	public static void main(String args[]) throws Exception {

		String imageFolder = args[0];
		int totalImages = Integer.parseInt(args[1]);
		int skipImages = Integer.parseInt(args[2]);
		int maxPixels = Integer.parseInt(args[3]);
		String featureType = args[4];
		String format = args[5];

		ImageScaling scale = new ImageScaling(maxPixels);
		AbstractFeatureExtractor featureExtractor;
		if (featureType.equals("surf")) {
			featureExtractor = new SURFExtractor();
		} else if (featureType.equals("sift")) {
			featureExtractor = new SIFTExtractor();
		} else if (featureType.equals("rootsift")) {
			featureExtractor = new RootSIFTExtractor();
		} else if (featureType.equals("csurf")) {
			featureExtractor = new ColorSURFExtractor();
		} else {
			throw new Exception("Wrong feature type provided.");
		}

		// --------------Load the image files-------------
		File dir = new File(imageFolder);
		FilenameFilter filter = new FilenameFilter() {
			public boolean accept(File dir, String name) {
				if (name.endsWith(".jpg") || name.endsWith(".png"))
					return true;
				else
					return false;
			}
		};
		String[] files = dir.list(filter);

		// create a folder for writing the features
		String featuresFolder = imageFolder + "../" + maxPixels + "_" + featureType;
		File file = new File(featuresFolder);
		if (!file.exists()) {
			if (file.mkdir()) {
				System.out.println("Directory is created!");
			} else {
				System.out.println("Failed to create directory!");
			}
		}

		double totalReadingTime = 0;
		double totalScalingTime = 0;
		double totalWritingTime = 0;
		int extractedCount = 0;
		int limit = Math.min(files.length, totalImages);
		// --------------Extract features for each image-------------
		for (int i = skipImages; i < limit; i++) {
			long start = System.currentTimeMillis();
			System.out.print("Processing image " + (i + 1) + ": ");
			BufferedImage image;
			long startReading = System.currentTimeMillis();
			try { // first try reading with the default class
				image = ImageIO.read(new File(imageFolder + files[i]));
			} catch (IllegalArgumentException e) { // if it fails retry with the corrected class
				// This exception is probably because of a grayscale jpeg image.
				System.out.println("Exception: " + e.getMessage() + " | Image: " + files[i]);
				image = ImageIOGreyScale.read(new File(imageFolder + files[i]));
			} catch (Exception e) { // skip extraction an other exception is thrown
				System.out.println("Exception: " + e.getMessage() + " | Image: " + files[i]);
				continue;
			}
			if (image == null) {
				System.out.println("Null image: " + files[i]);
				continue;
			}
			totalReadingTime += System.currentTimeMillis() - startReading;

			long startScaling = System.currentTimeMillis();
			image = scale.maxPixelsScaling(image);
			totalScalingTime += System.currentTimeMillis() - startScaling;

			double[][] features = featureExtractor.extractFeatures(image);

			// sanity check
			for (int k = 0; k < features.length; k++) {
				if (String.valueOf(features[k][0]).equals("NaN")) {
					System.out.println("NaN feature " + (k + 1) + " in image " + files[i]);
				}
			}

			String imageFileExtension;
			if (files[i].endsWith("jpg")) {
				imageFileExtension = "jpg";
			} else {
				imageFileExtension = "png";
			}
			// write features to file
			long startWring = System.currentTimeMillis();
			if (format.equals("bin")) {
				String featuresFileName = featuresFolder + "/"
						+ files[i].split("\\." + imageFileExtension)[0] + "." + featureType + "b";
				FeatureIO.writeBinary(featuresFileName, features);
			} else if (format.equals("txt")) {
				String featuresFileName = featuresFolder + "/"
						+ files[i].split("\\." + imageFileExtension)[0] + "." + featureType;
				FeatureIO.writeText(featuresFileName, features);
			}
			totalWritingTime += System.currentTimeMillis() - startWring;
			System.out.println("completed in " + (System.currentTimeMillis() - start) + " ms");
			extractedCount++;
		}
		System.out.println("Average reading time in ms: " + totalReadingTime / (double) extractedCount);
		System.out.println("Average scaling time in ms: " + totalScalingTime / (double) extractedCount);
		System.out.println("Average extraction time in ms: " + featureExtractor.getTotalExtractionTime()
				/ (double) extractedCount);
		System.out.println("Average writing time in ms: " + totalWritingTime / (double) extractedCount);
		System.out.println("Average number of interest points per image: "
				+ featureExtractor.getTotalNumberInterestPoints() / (double) extractedCount);

	}
}
