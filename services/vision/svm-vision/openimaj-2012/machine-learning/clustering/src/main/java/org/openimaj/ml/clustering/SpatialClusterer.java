/**
 * Copyright (c) 2011, The University of Southampton and the individual contributors.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 *   * 	Redistributions of source code must retain the above copyright notice,
 * 	this list of conditions and the following disclaimer.
 *
 *   *	Redistributions in binary form must reproduce the above copyright notice,
 * 	this list of conditions and the following disclaimer in the documentation
 * 	and/or other materials provided with the distribution.
 *
 *   *	Neither the name of the University of Southampton nor the names of its
 * 	contributors may be used to endorse or promote products derived from this
 * 	software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.openimaj.ml.clustering;

import org.openimaj.data.DataSource;
import org.openimaj.ml.clustering.assignment.HardAssigner;

/**
 * A {@link SpatialClusterer} clusters data that can be represented 
 * as points in a space. Each point must be represented by a numerical
 * coordinate vector.
 * 
 * @author Jonathon Hare (jsh2@ecs.soton.ac.uk)
 * @author Sina Samangooei (ss@ecs.soton.ac.uk)
 *
 * @param <T> The type which can be read and written by this cluster
 * @param <DATATYPE> the primitive array datatype which represents a centroid of this cluster
 */
public interface SpatialClusterer<T, DATATYPE> extends ReadWriteableClusterer {
	
	/**
	 * Perform clustering on the given data.
	 * 
	 * @param data the data.
	 * 
	 * @return false if an overflow may have occurred; true otherwise.
	 */
	public abstract boolean cluster(final DATATYPE[] data);
	
	/**
	 * Perform clustering with data from a data source. 
	 * The {@link DataSource} could potentially be 
	 * backed by disk rather in memory.
	 * 
	 * @param data the data.
	 * 
	 * @return false if an overflow may have occurred; true otherwise.
	 */
	public abstract boolean cluster(DataSource<DATATYPE> data);
	
	/**
	 * Get the data dimensionality
	 * @return the data dimensionality.
	 */
	public abstract int numDimensions();

	/**
	 * Get the number of clusters.
	 * @return number of clusters.
	 */
	public int numClusters();
	
	/**
	 * Get the default hard assigner for this clusterer. This
	 * method is potentially expensive, so callers should only
	 * call it once, and hold on to the result (and reuse it).
	 * 
	 * @return a hard assigner.
	 */
	public HardAssigner<DATATYPE, ?, ?> defaultHardAssigner();
}
