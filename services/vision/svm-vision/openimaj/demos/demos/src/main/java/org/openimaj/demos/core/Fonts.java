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
package org.openimaj.demos.core;

import java.awt.Font;

import org.openimaj.demos.Demo;
import org.openimaj.image.DisplayUtilities;
import org.openimaj.image.MBFImage;
import org.openimaj.image.colour.RGBColour;
import org.openimaj.image.typography.general.GeneralFont;

/**
 * 	@author David Dupplaw (dpd@ecs.soton.ac.uk)
 *	@created 5th November 2011
 */
@Demo(
		author = "David Dupplaw",
		description = "Demonstrates some the OpenIMAJ typography",
		keywords = { "fonts" },
		title = "Fonts"
		)
public class Fonts
{
	/**
	 * 	Construct the fonts demo.
	 */
	public Fonts()
	{
		final MBFImage img = new MBFImage( 800, 600, 3 );
		img.drawText( "OOpenIMAJ", 20, 100,
				new GeneralFont("Arial", Font.PLAIN ), 120, RGBColour.WHITE );
		img.drawText( "is Awesome!", 20, 220,
				new GeneralFont("Courier", Font.PLAIN ), 120, RGBColour.WHITE );
		img.drawText( "HHope you agree", 20, 400,
				new GeneralFont("Comic Sans MS", Font.PLAIN ), 50, RGBColour.WHITE );

		DisplayUtilities.display( img );
	}

	/**
	 * 	Default main
	 *  @param args Command-line arguments
	 */
	public static void main(final String[] args)
	{
		new Fonts();
	}
}
