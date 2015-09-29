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
package org.openimaj.tools.localfeature;

import java.io.IOException;

import org.kohsuke.args4j.CmdLineOptionsProvider;
import org.kohsuke.args4j.Option;
import org.openimaj.image.FImage;
import org.openimaj.image.Image;
import org.openimaj.image.processing.resize.ResizeProcessor;
import org.openimaj.image.processor.SinglebandImageProcessor;


public enum ImageTransform implements CmdLineOptionsProvider {
	NOTHING {
		@Override
		public ImageTransformOp getOptions() {
			return new NothingOp();
		}
	},
	RESIZE_MAX {
		@Override
		public ImageTransformOp getOptions() {
			return new ResizeMaxOp();
		}
	};
	
	@Override
	public abstract ImageTransformOp getOptions();
	
	public static abstract class ImageTransformOp {
		public abstract Image<?,?> transform(Image<?,?> a) throws IOException;
	}
	
	private static class NothingOp extends ImageTransformOp {
		@Override
		public Image<?,?> transform(Image<?,?> a){
			return a;
		}		
	}
	
	private static class ResizeMaxOp extends ImageTransformOp {
		@Option(name="--dim-max", aliases="-dmax", required=false, usage="The resultant length of maximum dimention")
		private int dmax = 640;
		
		@SuppressWarnings("unchecked")
		@Override
		public Image<?,?> transform(Image<?,?> a) throws IOException {
			if(!(a instanceof SinglebandImageProcessor.Processable<?,?,?>))
				throw new IOException("Can't resize that kind of image");
			int aw = a.getWidth();
			int ah = a.getHeight();
			int newwidth, newheight;
			if(aw < ah){
				// Final height will be dmax
				newheight = dmax;
				float resizeRatio = ((float)dmax/(float)ah);
				newwidth = (int) (aw * resizeRatio);
			}
			else{
				// Final width will be dmax, 
				newwidth = dmax;
				float resizeRatio = ((float)dmax/(float)aw);
				newheight = (int) (ah * resizeRatio);
			}
			
			System.out.println("Resizing image to: " + newwidth + "x" + newheight);
			return ((SinglebandImageProcessor.Processable<Float, FImage, FImage>)a).process(new ResizeProcessor(newwidth,newheight));
		}		
	}
}
