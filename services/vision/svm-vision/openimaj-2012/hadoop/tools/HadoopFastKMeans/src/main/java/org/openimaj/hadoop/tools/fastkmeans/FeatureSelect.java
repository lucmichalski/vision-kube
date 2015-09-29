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
package org.openimaj.hadoop.tools.fastkmeans;

import java.io.IOException;

import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;

import org.openimaj.tools.clusterquantiser.FileType;

public class FeatureSelect {
	public static final String FILETYPE_KEY = "uk.ac.soton.ecs.jsh2.clusterquantiser.FileType";
	public static final String NFEATURE_KEY = "uk.ac.soton.ecs.ss.hadoop.fastkmeans.nfeatures";
	public static class Map extends Mapper<Text, BytesWritable, IntWritable, BytesWritable> 
	{
		private int nfeatures = -1;
		private static FileType fileType = null;
		private IndexedByteArrayPriorityQueue queue; 
		
		@Override
		protected void setup(Mapper<Text, BytesWritable, IntWritable, BytesWritable>.Context context)throws IOException, InterruptedException{
			if (fileType == null) {
				fileType = FileType.valueOf(context.getConfiguration().getStrings(FILETYPE_KEY)[0]);
			}
			if(nfeatures == -1){
				nfeatures  = Integer.parseInt(context.getConfiguration().getStrings(NFEATURE_KEY)[0]);
			}
			
			queue = new IndexedByteArrayPriorityQueue(nfeatures);
		}
		
		@Override
		public void map(Text key, BytesWritable value, Context context) throws IOException, InterruptedException {
			byte[] validBytes = new byte[value.getLength()];
			System.arraycopy(value.getBytes(), 0, validBytes, 0, validBytes.length);
			IndexedByteArray indexedItem = new IndexedByteArray(validBytes);
			queue.insert(indexedItem);
		}
		
		@Override
		protected void cleanup(Context context) throws IOException, InterruptedException {
			while(this.queue.size() > 0){
				IndexedByteArray item = this.queue.pop();
				context.write(new IntWritable(item.index), new BytesWritable(item.array));
			}
		}
		
	} 
	public static class Reduce extends Reducer<IntWritable, BytesWritable, IntWritable, BytesWritable> {
		private int nfeatures = -1;
		private int seen = 0;
		@Override
		protected void setup(Context context)throws IOException, InterruptedException{
			if(nfeatures == -1){
				nfeatures  = Integer.parseInt(context.getConfiguration().getStrings(NFEATURE_KEY)[0]);
			}
		}
		
		@Override
		public void reduce(IntWritable key, Iterable<BytesWritable> values, Context context) throws IOException, InterruptedException {
			if(seen >= nfeatures){
				return;
			}
			for (BytesWritable val : values) {
				context.write(new IntWritable(seen), val);
				seen++;
				if(seen >= nfeatures){
					return;
				}
			}
		}
	}

}
