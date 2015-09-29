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
package org.openimaj.hadoop.tools.twitter.token.mode.dfidf;

import gnu.trove.TObjectIntHashMap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Counters;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.joda.time.DateTime;
import org.kohsuke.args4j.CmdLineException;
import org.openimaj.hadoop.mapreduce.stage.StageProvider;
import org.openimaj.hadoop.mapreduce.stage.helper.TextLongByteStage;
import org.openimaj.hadoop.tools.HadoopToolsUtil;
import org.openimaj.hadoop.tools.twitter.HadoopTwitterTokenToolOptions;
import org.openimaj.hadoop.tools.twitter.JsonPathFilterSet;
import org.openimaj.hadoop.tools.twitter.token.mode.TextEntryType;
import org.openimaj.hadoop.tools.twitter.token.mode.WritableEnumCounter;
import org.openimaj.hadoop.tools.twitter.utils.TweetCountWordMap;
import org.openimaj.io.IOUtils;
import org.openimaj.twitter.GeneralJSONTwitter;
import org.openimaj.twitter.USMFStatus;

import com.jayway.jsonpath.JsonPath;

/**
 * A mapper/reducer whose purpose is to do the following:
 * function(timePeriodLength)
 * So a word in a tweet can happen in the time period between t - 1 and t.
 * First task:
 * 	map input:
 * 		tweetstatus # json twitter status with JSONPath to words
 * 	map output:
 * 		<timePeriod: <word:#freq,tweets:#freq>, -1:<word:#freq,tweets:#freq> > 
 * 	reduce input:
 * 		<timePeriod: [<word:#freq,tweets:#freq>,...,<word:#freq,tweets:#freq>]> 
 *	reduce output:
 *		<timePeriod: <<tweet:#freq>,<word:#freq>,<word:#freq>,...>
 * @author Sina Samangooei (ss@ecs.soton.ac.uk)
 *
 */
public class CountTweetsInTimeperiod extends StageProvider{
	private String[] nonHadoopArgs;
	private boolean inmemoryCombine;
	private long timedelta;
	public final static String TIMECOUNT_DIR = "timeperiodTweet";
	public final static String GLOBAL_STATS_FILE = "globalstats";
	private static final String TIMEDELTA = "org.openimaj.hadoop.tools.twitter.token.mode.dfidf.timedelta";

	/**
	 * @param output the output location
	 * @param nonHadoopArgs to be sent to the stage
	 */
	public CountTweetsInTimeperiod(Path output,String[] nonHadoopArgs, long timedelta) {
		this.nonHadoopArgs = nonHadoopArgs;
		this.inmemoryCombine = false;
		this.timedelta = timedelta;
	}
	
	/**
	 * @param output the output location
	 * @param nonHadoopArgs to be sent to the stage
	 * @param inMemoryCombine whether an in memory combination of word counts should be performed
	 */
	public CountTweetsInTimeperiod(Path output,String[] nonHadoopArgs, boolean inMemoryCombine, long timedelta) {
		this.nonHadoopArgs = nonHadoopArgs;
		this.inmemoryCombine = inMemoryCombine;
		this.timedelta = timedelta;
	}
	
	/**
	 * 
	 *  map input:
	 *  	tweetstatus # json twitter status with JSONPath to words
	 *  map output:
	 *  	<timePeriod: <word:#freq,tweets:#freq>, -1:<word:#freq,tweets:#freq> > 
	 *  
	 * @author Jonathon Hare (jsh2@ecs.soton.ac.uk), Sina Samangooei
	 *         <ss@ecs.soton.ac.uk>
	 * 
	 */
	public static class Map extends Mapper<LongWritable, Text, LongWritable, BytesWritable> {

		/**
		 * Mapper don't care, mapper don't give a fuck
		 */
		public Map(){
			
		}
		/**
		 * The time used to signify the end, used to count total numbers of times a given word appears
		 */
		public static final LongWritable END_TIME = new LongWritable(-1);
		/**
		 * A total of the number of tweets, must be ignored!
		 */
		public static final LongWritable TOTAL_TIME = new LongWritable(-2);
		private static HadoopTwitterTokenToolOptions options;
		private static long timeDeltaMillis;
		private static JsonPath jsonPath;
		private static JsonPathFilterSet filters;

		protected static synchronized void loadOptions(Mapper<LongWritable, Text, LongWritable, BytesWritable>.Context context) throws IOException {
			if (options == null) {
				try {
					options = new HadoopTwitterTokenToolOptions(context.getConfiguration().getStrings(HadoopTwitterTokenToolOptions.ARGS_KEY));
					options.prepare();
					filters = options.getFilters();
					timeDeltaMillis = context.getConfiguration().getLong(CountTweetsInTimeperiod.TIMEDELTA, 60) * 60 * 1000;
					jsonPath = JsonPath.compile(options.getJsonPath());
					
				} catch (CmdLineException e) {
					throw new IOException(e);
				} catch (Exception e) {
					throw new IOException(e);
				}
			}
		}

		private HashMap<Long, TweetCountWordMap> tweetWordMap;

		@Override
		protected void setup(Mapper<LongWritable, Text, LongWritable, BytesWritable>.Context context) throws IOException, InterruptedException {
			loadOptions(context);
			this.tweetWordMap = new HashMap<Long, TweetCountWordMap>();
		}

		@Override
		protected void map(LongWritable key,Text value,Mapper<LongWritable, Text, LongWritable, BytesWritable>.Context context) throws java.io.IOException, InterruptedException {
			List<String> tokens = null;
			USMFStatus status = null;
			DateTime time = null;
			try {
				String svalue = value.toString();
				status = new USMFStatus(GeneralJSONTwitter.class);
				status.fillFromString(svalue);
				if(status.isInvalid()) return;
				if(!filters.filter(svalue))return;
				tokens = jsonPath.read(svalue );
				if(tokens == null) {
					context.getCounter(TextEntryType.INVALID_JSON).increment(1);
//					System.err.println("Couldn't read the tokens from the tweet");
					return;
				}
				if(tokens.size() == 0){
					context.getCounter(TextEntryType.INVALID_ZEROLENGTH).increment(1);
					return; //Quietly quit, value exists but was empty
				}
				time = status.createdAt();
				if(time == null){
					context.getCounter(TextEntryType.INVALID_TIME).increment(1);
//					System.err.println("Time was null, this usually means the original tweet had no time. Skip this tweet.");
					return;
				}

			} catch (Exception e) {
//				System.out.println("Couldn't get tokens from:\n" + value + "\nwith jsonpath:\n" + jsonPath);
				return;
			}
			// Quantise the time to a specific index
			long timeIndex = (time.getMillis() / timeDeltaMillis) * timeDeltaMillis;
			TweetCountWordMap timeWordMap = this.tweetWordMap.get(timeIndex);
//			System.out.println("Tweet time: " + time.getMillis());
//			System.out.println("Tweet timeindex: " + timeIndex);
			if (timeWordMap == null) {
				this.tweetWordMap.put(timeIndex,timeWordMap =  new TweetCountWordMap());
			}
			TObjectIntHashMap<String> tpMap = timeWordMap.getTweetWordMap();
			timeWordMap.incrementTweetCount(1);
			List<String> seen = new ArrayList<String>();
			for (String token : tokens) {
				// Apply stop words?
				// Apply junk words?
				// Already seen it?

				if (seen.contains(token))
					continue;
				seen.add(token);
				tpMap.adjustOrPutValue(token, 1, 1);
//				if(token.equals("...")){
//					System.out.println("TOKEN: " + token);
//					System.out.println("TIME: " + timeIndex);
//					System.out.println("NEW VALUE: " + newv);
//				}
			}
			context.getCounter(TextEntryType.VALID).increment(1);
		}

		@Override
		protected void cleanup(Mapper<LongWritable, Text, LongWritable, BytesWritable>.Context context) throws IOException, InterruptedException {
			for (Entry<Long, TweetCountWordMap> tpMapEntry : this.tweetWordMap.entrySet()) {
				Long time = tpMapEntry.getKey();
				TweetCountWordMap map = tpMapEntry.getValue();
				ByteArrayOutputStream outarr = new ByteArrayOutputStream();
				IOUtils.writeBinary(outarr, map);
				byte[] arr = outarr.toByteArray();
				BytesWritable toWrite = new BytesWritable(arr);
				context.write(END_TIME, toWrite);
				context.write(new LongWritable(time), toWrite);
				context.getCounter(TextEntryType.ACUAL_EMITS).increment(1);
			}
		}
	}
	

	/**
	 *  reduce input: 
	 *  	<timePeriod: [<word:#freq,tweets:#freq>,...,<word:#freq,tweets:#freq>]> 
	 *  reduce output:
	 *  	<timePeriod: <<tweet:#freq>,<word:#freq>,<word:#freq>,...>
	 * @author Sina Samangooei (ss@ecs.soton.ac.uk)
	 *
	 */
	public static class InMemoryCombiningReducer extends Reducer<LongWritable, BytesWritable, LongWritable, BytesWritable>{
		
		/**
		 * default construct does nothing
		 */
		public InMemoryCombiningReducer(){
			
		}
		@Override
		protected void reduce(LongWritable key, Iterable<BytesWritable> values, Reducer<LongWritable, BytesWritable, LongWritable, BytesWritable>.Context context) throws IOException,InterruptedException{
			TweetCountWordMap accum = new TweetCountWordMap();
			for (BytesWritable tweetwordmapbytes : values) {
				TweetCountWordMap tweetwordmap = null;
				tweetwordmap = IOUtils.read(new ByteArrayInputStream(tweetwordmapbytes.getBytes()), TweetCountWordMap.class);
				accum.combine(tweetwordmap);
			}
			ByteArrayOutputStream outstream = new ByteArrayOutputStream();
			IOUtils.writeBinary(outstream, accum);
			context.write(key, new BytesWritable(outstream.toByteArray()));
		}
	}
	
	



	@Override
	public TextLongByteStage stage() {
		TextLongByteStage s = new TextLongByteStage() {
			private Path actualOutputLocation;

			@Override
			public void setup(Job job) {
				job.getConfiguration().setStrings(HadoopTwitterTokenToolOptions.ARGS_KEY, nonHadoopArgs);
				job.getConfiguration().setLong(TIMEDELTA, timedelta);
				if(!inmemoryCombine){
					job.setNumReduceTasks(0);
				}
			}
			
			@Override
			public Class<? extends Mapper<LongWritable, Text, LongWritable, BytesWritable>> mapper() {
				return CountTweetsInTimeperiod.Map.class;
			}
			@Override
			public Class<? extends Reducer<LongWritable, BytesWritable, LongWritable, BytesWritable>> reducer() {
				if(inmemoryCombine)
					return CountTweetsInTimeperiod.InMemoryCombiningReducer.class;
				else
					return super.reducer();
			}
			
			@Override
			public Job stage(Path[] inputs, Path output, Configuration conf) throws Exception {
				this.actualOutputLocation = output; 
				return super.stage(inputs, output, conf);
			}
			
			@Override
			public String outname() {
				return TIMECOUNT_DIR;
			}
			
			@Override
			public void finished(Job job) {
				Counters counters;
				try {
					counters = job.getCounters();
				} catch (IOException e) {
//					System.out.println("Counters not found!");
					return;
				}
				// Prepare a writer to the actual output location
				Path out = new Path(actualOutputLocation, GLOBAL_STATS_FILE);
				FileSystem fs;
				try {
					fs = HadoopToolsUtil.getFileSystem(out);
					FSDataOutputStream os = fs.create(out);
					IOUtils.writeASCII(os, new WritableEnumCounter<TextEntryType>(counters,TextEntryType.values()){
						@Override
						public TextEntryType valueOf(String str) {
							return TextEntryType .valueOf(str);
						}
						
					});
				} catch (IOException e) {
				}
				
			}
		};
		return s;
	}
}