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
package org.openimaj.image.processing.threshold;

import org.openimaj.image.FImage;
import org.openimaj.image.processor.SinglebandImageProcessor;

/**
 * Abstract base class for local thresholding operations. Local thresholding
 * operations determine their threshold based on a rectangular image patch.
 * 
 * @author Jonathon Hare (jsh2@ecs.soton.ac.uk)
 */
public abstract class AbstractLocalThreshold implements SinglebandImageProcessor<Float, FImage> {
	protected int sizeX;
	protected int sizeY;

	/**
	 * Construct the AbstractLocalThreshold with the given patch size (the patch
	 * will be square).
	 * 
	 * @param size
	 *            the length of the patch side.
	 */
	public AbstractLocalThreshold(int size) {
		this(size, size);
	}

	/**
	 * Construct the AbstractLocalThreshold with the given patch size.
	 * 
	 * @param size_x
	 *            the width of the patch.
	 * @param size_y
	 *            the height of the patch.
	 */
	public AbstractLocalThreshold(int size_x, int size_y) {
		this.sizeX = size_x;
		this.sizeY = size_y;
	}

	/**
	 * Get the height of the local sampling rectangle
	 * 
	 * @return the height of the local sampling rectangle
	 */
	public int getKernelHeight() {
		return sizeY;
	}

	/**
	 * Get the width of the local sampling rectangle
	 * 
	 * @return the width of the local sampling rectangle
	 */
	public int getKernelWidth() {
		return sizeX;
	}
}
