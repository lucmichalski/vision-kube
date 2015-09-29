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
/**
 * 
 */
package org.openimaj.demos.sandbox;

import java.io.IOException;

import org.openimaj.image.MBFImage;
import org.openimaj.math.geometry.point.Point2d;
import org.openimaj.video.VideoDisplay;
import org.openimaj.video.VideoDisplayListener;
import org.openimaj.video.analysis.motion.GridMotionEstimator;
import org.openimaj.video.analysis.motion.MotionEstimator;
import org.openimaj.video.analysis.motion.MotionEstimator.MotionEstimatorAlgorithm;
import org.openimaj.video.capture.VideoCapture;
import org.openimaj.video.translator.FImageToMBFImageVideoTranslator;
import org.openimaj.video.translator.MBFImageToFImageVideoTranslator;

/**
 *  @author David Dupplaw (dpd@ecs.soton.ac.uk)
 *	
 *	@created 29 Feb 2012
 */
public class PhaseCorrelationProcessor
{
	/**
	 *  @param args
	 * 	@throws IOException 
	 */
	public static void main( String[] args ) throws IOException
    {
		VideoCapture vc = new VideoCapture( 320, 240 );
		final MotionEstimator me = new GridMotionEstimator( 
				new MBFImageToFImageVideoTranslator( vc ), 
				MotionEstimatorAlgorithm.PHASE_CORRELATION, 40, 40, true );
		
		VideoDisplay<MBFImage> vd = VideoDisplay.createVideoDisplay( 
			new FImageToMBFImageVideoTranslator( me ) );
		vd.addVideoListener( new VideoDisplayListener<MBFImage>()
		{
			@Override
            public void afterUpdate( VideoDisplay<MBFImage> display )
            {
            }

			@Override
            public void beforeUpdate( MBFImage frame )
            {
				for( Point2d p : me.motionVectors.keySet() )
				{
					Point2d p2 = me.motionVectors.get(p);
					frame.drawLine( (int)p.getX(), (int)p.getY(),
							(int)(p.getX() + p2.getX()), 
							(int)(p.getY() + p2.getY()),
							2, new Float[]{1f,0f,0f} );
				}
            }			
		});
    }
}