/**
 * ImageTerrier - The Terabyte Retriever for Images
 * Webpage: http://www.imageterrier.org/
 * Contact: jsh2@ecs.soton.ac.uk
 * Electronics and Computer Science, University of Southampton
 * http://www.ecs.soton.ac.uk/
 *
 * The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
 * the License for the specific language governing rights and limitations
 * under the License.
 *
 * The Original Code is IndexType.java
 *
 * The Original Code is Copyright (C) 2011 the University of Southampton
 * and the original contributors.
 * All Rights Reserved.
 *
 * Contributor(s):
 *   Jonathon Hare <jsh2@ecs.soton.ac.uk> (original contributor)
 *   Sina Samangooei <ss@ecs.soton.ac.uk>
 *   David Dupplaw <dpd@ecs.soton.ac.uk>
 */
package org.imageterrier.toolopts;

import java.io.IOException;

import org.imageterrier.indexing.BasicSinglePassIndexer;
import org.imageterrier.indexing.NNSinglePassIndexer;
import org.imageterrier.indexing.PositionSinglePassIndexer;
import org.imageterrier.locfile.PositionSpec;
import org.imageterrier.locfile.PositionSpec.PositionSpecMode;
import org.kohsuke.args4j.CmdLineOptionsProvider;
import org.kohsuke.args4j.Option;
import org.terrier.indexing.ExtensibleSinglePassIndexer;
import org.terrier.utility.ArrayUtils;
import org.terrier.utility.ExtendedArrayUtils;


/**
 * Different types of index
 * 
 * @author Jonathon Hare <jsh2@ecs.soton.ac.uk>
 */
public enum IndexType implements CmdLineOptionsProvider {
	BASIC {
		@Override
		public IndexTypeOptions getOptions() {
			return new BasicOptions();
		}
	},
	NEAREST_NEIGHBOUR {
		@Override
		public IndexTypeOptions getOptions() {
			return new NNOptions();
		}
	},
	POSITION {
		@Override
		public IndexTypeOptions getOptions() {
			return new PositionOptions();
		}
	},
	AFFINESIM {
		@Override
		public IndexTypeOptions getOptions() {
			return new AffineSimOptions();
		}
	};
	
	@Override
	public abstract IndexTypeOptions getOptions();

	/**
	 * Options for all modes
	 * 
	 * @author Jonathon Hare <jsh2@ecs.soton.ac.uk>
	 *
	 */
	public static abstract class IndexTypeOptions {
		public abstract ExtensibleSinglePassIndexer getIndexer(String indexPath, String indexName) throws IOException;		
	}
	
	/**
	 * Options for BASIC mode
	 * 
	 * @author Jonathon Hare <jsh2@ecs.soton.ac.uk>
	 *
	 */
	public static class BasicOptions extends IndexTypeOptions {
		@Override
		public ExtensibleSinglePassIndexer getIndexer(String indexPath, String indexName) {
			return new BasicSinglePassIndexer(indexPath, indexName);				
		}
	}
	
	/**
	 * Options for NEAREST_NEIGHBOUR mode
	 * 
	 * @author Jonathon Hare <jsh2@ecs.soton.ac.uk>
	 *
	 */
	public static class NNOptions extends IndexTypeOptions {
		@Override
		public ExtensibleSinglePassIndexer getIndexer(String indexPath, String indexName) {
			return new NNSinglePassIndexer(indexPath, indexName);	
		}
	}
	
	/**
	 * Options for POSITION mode
	 * 
	 * @author Jonathon Hare <jsh2@ecs.soton.ac.uk>
	 *
	 */
	public static class PositionOptions extends IndexTypeOptions {
		@Option(name="--nbits", aliases="-nb", required=false, usage="Comma separated number of bits per position entry")
		private String nBitsStr = "";

		@Option(name="--min-values", aliases="-mins", required=false, usage="Comma separated minimum value for each position entry")
		private String minStr = "";

		@Option(name="--max-values", aliases="-maxs", required=false, usage="Comma separated maximum value for each position entry")
		private String maxStr = "";

		@Option(name="--position-mode", aliases="-pm", required=true, usage="The position information to be stored in this index")
		PositionSpec.PositionSpecMode posMode = PositionSpec.PositionSpecMode.NONE;
		
		@Override
		public ExtensibleSinglePassIndexer getIndexer(String indexPath, String indexName) throws IOException {

			int[] bits = ArrayUtils.parseCommaDelimitedInts(nBitsStr);
			if(posMode.npos != bits.length) throw new IOException("Incorrect number of bits, expecting: " + posMode.npos);

			double[] mins = ExtendedArrayUtils.parseCommaDelimitedDoubles(minStr);
			if(posMode.npos != mins.length) throw new IOException("Incorrect number of mins, expecting: " + posMode.npos);

			double[] maxs = ExtendedArrayUtils.parseCommaDelimitedDoubles(maxStr);
			if(posMode.npos != maxs.length) throw new IOException("Incorrect number of maxs, expecting: " + posMode.npos);

			PositionSpec spec = new PositionSpec(posMode, bits,mins,maxs);
			return new PositionSinglePassIndexer(indexPath, indexName, spec);	
		}
	}
	
	/**
	 * Options for AFFINESIM mode
	 * 
	 * @author Jonathon Hare <jsh2@ecs.soton.ac.uk>
	 *
	 */
	public static class AffineSimOptions extends IndexTypeOptions {
		@Override
		public ExtensibleSinglePassIndexer getIndexer(String indexPath, String indexName) {
			PositionSpec spec = new PositionSpec(PositionSpecMode.AFFINE_INDEX, new int[]{5}, new double[]{0}, new double[]{32});
			return new PositionSinglePassIndexer(indexPath, indexName, spec);	
		}
	}
}
