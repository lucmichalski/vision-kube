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
package org.openimaj.demos.sandbox.asm;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.openimaj.image.FImage;
import org.openimaj.image.ImageUtilities;
import org.openimaj.math.geometry.point.Point2dImpl;
import org.openimaj.math.geometry.shape.PointList;
import org.openimaj.math.geometry.shape.PointListConnections;
import org.openimaj.util.pair.IndependentPair;

public class AMPTSDataset {
	private List<IndependentPair<PointList, FImage>> data = new ArrayList<IndependentPair<PointList, FImage>>();
	private PointListConnections connections;
	
	public AMPTSDataset(String [] filenames, File ptsDir, File imgDir, File connFile) throws IOException {
		for (String name : filenames) {
			FImage img = ImageUtilities.readF(new File(imgDir, name + ".jpg"));
			PointList pts = readPts(new File(ptsDir, name + ".pts"));
			data.add(new IndependentPair<PointList, FImage>(pts, img));
		}
		
		connections = readConnections(connFile);
	}
	
	public AMPTSDataset(File ptsDir, File imgDir, File connFile) throws IOException {
		this(new String[] {
				"107_0764", "107_0766", "107_0779", "107_0780", "107_0781", "107_0782", "107_0784", 
				"107_0785", "107_0786", "107_0787", "107_0788", "107_0789", "107_0790", "107_0791", "107_0792"}, 
				ptsDir, imgDir, connFile);
	}

	private PointListConnections readConnections(File connFile) throws IOException {
		BufferedReader br = new BufferedReader(new FileReader(connFile)); 
		PointListConnections plc = new PointListConnections();
		
		String line;
		while ( (line = br.readLine()) != null) {
			if (!line.trim().startsWith("indices")) 
				continue;
			
			String [] data = line.trim().replace("indices(", "").replace(")", "").split(",");
			boolean isOpen = (br.readLine().contains("open_boundary"));
			
			int prev = Integer.parseInt(data[0]);
			for (int i=1; i<data.length; i++) {
				int next = Integer.parseInt(data[i]);
				plc.addConnection(prev, next);
				prev = next;
			}
			
			if (!isOpen) {
				plc.addConnection(Integer.parseInt(data[data.length - 1]), Integer.parseInt(data[0]));
			}
		}
		
		br.close();
		
		return plc;
	}
	
	private PointList readPts(File file) throws IOException {
		PointList pl = new PointList();
		BufferedReader br = new BufferedReader(new FileReader(file));

		br.readLine();
		br.readLine();
		br.readLine();

		String line;
		while ((line = br.readLine()) != null) {
			if (!line.startsWith("}") && line.trim().length() > 0) {
				String[] parts = line.split("\\s+");

				float x = Float.parseFloat(parts[0].trim());
				float y = Float.parseFloat(parts[1].trim());

				pl.points.add(new Point2dImpl(x, y));
			}
		}
		br.close();

		return pl;
	}

	public List<IndependentPair<PointList, FImage>> getData() {
		return data;
	}

	public PointListConnections getConnections() {
		return connections;
	}
}
