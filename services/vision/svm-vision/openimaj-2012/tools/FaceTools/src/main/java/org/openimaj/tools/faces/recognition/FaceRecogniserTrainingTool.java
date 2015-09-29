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
package org.openimaj.tools.faces.recognition;

import java.io.IOException;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.openimaj.image.processing.face.detection.DetectedFace;
import org.openimaj.image.processing.face.recognition.FaceRecognitionEngine;
import org.openimaj.tools.faces.recognition.FaceRecogniserTrainingToolOptions.RecognitionStrategy;

/**
 * A tool for training face recognisers
 * 
 * @author Jonathon Hare (jsh2@ecs.soton.ac.uk)
 *
 * @param <T> Type of {@link DetectedFace}
 */
public class FaceRecogniserTrainingTool<T extends DetectedFace> {
	
	/**
	 * The main method of the tool.
	 * 
	 * @param args
	 * @throws IOException
	 */
	public static void main(String [] args) throws IOException {
		FaceRecogniserTrainingToolOptions options = new FaceRecogniserTrainingToolOptions();
        CmdLineParser parser = new CmdLineParser( options );

        try
        {
	        parser.parseArgument( args );
        }
        catch( CmdLineException e )
        {
	        System.err.println( e.getMessage() );
	        System.err.println( "java FaceRecogniserTrainingTool [options...] IMAGE-FILES-OR-DIRECTORIES");
	        parser.printUsage( System.err );
	        
	        System.err.println();
	        System.err.println("Strategy information:");
	        for (RecognitionStrategy s : RecognitionStrategy.values()) {
	        	System.err.println(s + ":");
	        	System.err.println(s.description());
	        	System.err.println();
	        }
	        return;
        }

        FaceRecognitionEngine<?> engine = options.getEngine();
        
        if (options.identifier == null) {
        	if(options.identifierFile == null)
        	{
        		engine.trainBatch(options.files);
        	}
        	else{
        		engine.trainBatchFile(options.identifierFile);
        	}
        } else {
        	engine.trainSingle(options.identifier, options.files);
        }
        engine.finalTrain();
        
        engine.save(options.recogniserFile);
	}
	
}
