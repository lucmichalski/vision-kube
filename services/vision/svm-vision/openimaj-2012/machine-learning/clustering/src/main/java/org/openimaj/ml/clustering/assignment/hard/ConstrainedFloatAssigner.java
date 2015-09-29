package org.openimaj.ml.clustering.assignment.hard;

import org.openimaj.citation.annotation.Reference;
import org.openimaj.citation.annotation.ReferenceType;
import org.openimaj.ml.clustering.assignment.HardAssigner;
import org.openimaj.util.pair.IntFloatPair;

/**
 * An assigner that wraps another hard assigner and only
 * produces valid assignments if the closest cluster is
 * within (or outside) of a given threshold distance.
 * <p>
 * Invalid assignments are marked by a cluster id of -1,
 * and (if applicable) distance of {@link Float#NaN}. Users
 * of this class must check the assignments and filter
 * as necessary.
 * 
 * @author Jonathon Hare (jsh2@ecs.soton.ac.uk)
 * @param <DATATYPE> the primitive array datatype which represents a centroid of this cluster.
 */
@Reference(
		author = { "Y. Cai", "W. Tong", "L. Yang", "A. G. Hauptmann" }, 
		title = "Constrained Keypoint Quantization: Towards Better Bag-of-Words Model for Large-scale Multimedia Retrieval", 
		type = ReferenceType.Inproceedings, 
		year = "2012",
		booktitle = "ACM International Conference on Multimedia Retrieval",
		customData = { "location", "Hong Kong, China" }
		)
public class ConstrainedFloatAssigner<DATATYPE> implements HardAssigner<DATATYPE, float[], IntFloatPair> {
	HardAssigner<DATATYPE, float[], IntFloatPair> internalAssigner;
	
	boolean allowIfGreater = false;
	float threshold;
	
	/**
	 * Construct the ConstrainedFloatAssigner with the given 
	 * assigner and threshold. Assignments will be rejected 
	 * if the distance given by the internal assigner are
	 * greater than the threshold.
	 * 
	 * @param internalAssigner the internal assigner for computing distances.
	 * @param threshold the threshold at which assignments are rejected.
	 */
	public ConstrainedFloatAssigner(HardAssigner<DATATYPE, float[], IntFloatPair> internalAssigner, float threshold) {
		this.threshold = threshold;
	}
	
	/**
	 * Construct the ConstrainedFloatAssigner with the given 
	 * assigner and threshold. The greater flag determines if
	 * assignments should be rejected if the distance generated
	 * by the internal assigner is greater than the threshold 
	 * (false) or less than the threshold (true).
	 * 
	 * @param internalAssigner the internal assigner for computing distances.
	 * @param threshold the threshold at which assignments are rejected.
	 * @param greater if true distances less than the threshold are 
	 * 		rejected; if false then distances greater than the threshold 
	 * 		are rejected.
	 */
	public ConstrainedFloatAssigner(HardAssigner<DATATYPE, float[], IntFloatPair> internalAssigner, float threshold, boolean greater) {
		this.allowIfGreater = greater;
		this.threshold = threshold;
	}
	
	private boolean allow(float distance) {
		if (allowIfGreater) {
			return distance > threshold;
		}
		
		return distance < threshold;
	}
	
	@Override
	public int[] assign(DATATYPE[] data) {
		int[] indices = new int[data.length];
		float[] distances = new float[data.length];
		
		assignDistance(data, indices, distances);
		
		return indices;
	}

	@Override
	public int assign(DATATYPE data) {
		return assignDistance(data).first;
	}

	@Override
	public void assignDistance(DATATYPE[] data, int[] indices, float[] distances) {
		internalAssigner.assignDistance(data, indices, distances);
		
		for (int i=0; i<data.length; i++) {
			if (!allow(distances[i])) {
				distances[i] = Float.NaN;
				indices[i] = -1;
			}
		}
	}

	@Override
	public IntFloatPair assignDistance(DATATYPE data) {
		IntFloatPair res = internalAssigner.assignDistance(data);
		
		if (!allow(res.second)) {
			res.second = Float.NaN;
			res.first = -1;
		}
		
		return res;
	}
}
