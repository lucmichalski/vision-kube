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
package org.openimaj.ml.annotation.basic.util;

import java.util.Random;

import org.openimaj.experiment.dataset.Dataset;
import org.openimaj.ml.annotation.Annotated;

/**
 * Choose a random number of annotations between the given
 * limits.
 * 
 * @author Jonathon Hare (jsh2@ecs.soton.ac.uk)
 *
 */
public class RandomChooser implements NumAnnotationsChooser {
	protected final Random rng = new Random();
	protected int min;
	protected int max;

	/**
	 * Construct so that the maximum possible number of annotations
	 * is max and the minimum is 0.
	 * @param max the maximum possible number of annotations
	 */
	public RandomChooser(int max) {
		this.max = max;
	}
	
	/**
	 * Construct so that the minimium possible number of annotations
	 * is min and the maximum is max.
	 * @param min the minimium possible number of annotations
	 * @param max the maximum possible number of annotations
	 */
	public RandomChooser(int min, int max) {
		this.min = min;
		this.max = max;
	}
	
	@Override
	public <O, A> void train(Dataset<? extends Annotated<O, A>> data) {
		//Do nothing
	}

	@Override
	public int numAnnotations() {
		return rng.nextInt(min + max) - min;
	}
}
