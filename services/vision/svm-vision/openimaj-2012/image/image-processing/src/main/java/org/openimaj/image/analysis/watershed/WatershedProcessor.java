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
package org.openimaj.image.analysis.watershed;

import java.util.ArrayList;
import java.util.List;

import org.openimaj.image.FImage;
import org.openimaj.image.analysis.watershed.event.ComponentStackMergeListener;
import org.openimaj.image.analysis.watershed.feature.ComponentFeature;
import org.openimaj.image.pixel.IntValuePixel;


/**
 *	Detector for Maximally-Stable Extremal Regions. The actual image analysis
 *	is in the class {@link WatershedProcessorAlgorithm} to allow this class to be
 *	re-used efficiently.
 *
 *	@author David Dupplaw (dpd@ecs.soton.ac.uk)
 *	
 */
public class WatershedProcessor
{
	/** This is where we start "pouring" our water */
	private IntValuePixel startPixel = new IntValuePixel(0,0);
		
	/** A list of objects that want to know what components on the stack merge */
	private List<ComponentStackMergeListener> csmListeners = null;

	private Class<? extends ComponentFeature>[] featureClasses;
	
	/**
	 * 	Default constructor. 
	 * @param featureClasses classes for feature creation
	 */
	public WatershedProcessor(Class<? extends ComponentFeature>... featureClasses)
	{
		this.csmListeners = new ArrayList<ComponentStackMergeListener>();
		this.featureClasses = featureClasses;
	}
	
	/**
	 *	Process the given image.
	 *	@param greyscaleImage The image to process
	 */
	public void processImage( FImage greyscaleImage ) {	
		WatershedProcessorAlgorithm d = new WatershedProcessorAlgorithm( greyscaleImage, this.startPixel, featureClasses );
		
		for( ComponentStackMergeListener csm : csmListeners )
			d.addComponentStackMergeListener( csm );
		
		d.startPour();
	}
	
	/**
	 * 	Add a component stack merge listener
	 *	@param csml The {@link ComponentStackMergeListener} to add
	 */
	public void addComponentStackMergeListener( ComponentStackMergeListener csml )
	{
		csmListeners.add( csml );
	}

}
