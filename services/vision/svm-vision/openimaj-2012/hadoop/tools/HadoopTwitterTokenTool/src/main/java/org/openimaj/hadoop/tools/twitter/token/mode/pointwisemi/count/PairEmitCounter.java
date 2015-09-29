package org.openimaj.hadoop.tools.twitter.token.mode.pointwisemi.count;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.log4j.Logger;
import org.openimaj.hadoop.tools.HadoopToolsUtil;
import org.openimaj.io.IOUtils;
import org.openimaj.util.pair.IndependentPair;

/**
 * The input to this reducer is ordered firstly by unary/pairs then within these sets by word
 * Given a particular time period, first read all unary counts and combine for each word
 * Then for all pairs, combine pair instances for a given pair then emit onces a new pair or the end is reached
 * 
 * Once the first non unary word is found, start counting for a particular word
 * @author Sina Samangooei (ss@ecs.soton.ac.uk)
 *
 */
public class PairEmitCounter extends Reducer<BytesWritable, BytesWritable, BytesWritable, BytesWritable> {
	Map<String,Long> unaryCounts = null;
	private Path timeperiodCountOutputRoot;
	Logger logger = Logger.getLogger(PairEmitCounter.class);
	
	@Override
	protected void setup(Reducer<BytesWritable,BytesWritable,BytesWritable,BytesWritable>.Context context) throws IOException ,InterruptedException {
		this.timeperiodCountOutputRoot = new Path(context.getConfiguration().get(PairMutualInformation.TIMEPERIOD_COUNT_OUTPUT_ROOT));
		if(!HadoopToolsUtil.getFileSystem(this.timeperiodCountOutputRoot ).mkdirs(this.timeperiodCountOutputRoot)) throw new IOException("Couldn't create: " + this.timeperiodCountOutputRoot);
	};
	
	public PairEmitCounter() {
		this.unaryCounts = new HashMap<String,Long>();
	}
	@Override
	protected void reduce(BytesWritable timeB, Iterable<BytesWritable> paircounts, Reducer<BytesWritable,BytesWritable,BytesWritable,BytesWritable>.Context context) throws IOException ,InterruptedException {
		long time = TokenPairCount.timeFromBinaryIdentity(timeB.getBytes());
		// Prepare the timeperiod output
		Path timeperiodCountOutput = new Path(timeperiodCountOutputRoot,""+time);
		
		long pairsCount = 0;
		// Start with unary count
		TokenPairCollector collector = new TokenPairCollector();
		for (BytesWritable bytesWritable : paircounts) {
			TokenPairCount newcount = IOUtils.deserialize(bytesWritable.getBytes(), TokenPairCount.class);
			TokenPairCount count = collector.add(newcount);
			if(count!=null){
				pairsCount += count.paircount;
				// this is the combined counts for this unary word in this time period
				addUnaryWordCount(count);
				// Now check if the current word is a pair, if so next part!
				if(collector.isCurrentPair()){
					break;
				}
			}
		}
		
		writeTimeperiodCount(timeperiodCountOutput,pairsCount);
		
		for (BytesWritable bytesWritable : paircounts) {
			TokenPairCount newcount = IOUtils.deserialize(bytesWritable.getBytes(), TokenPairCount.class);
			if(newcount.isSingle){
				// The list was not sorted!
				throw new IOException("List of TokenPairCounts was not sorted such that ALL singles appeared before pairs");
			}
			TokenPairCount count = collector.add(newcount);
			if(count != null){
				emitPairCount(time,count,context);
			}
		}
		emitPairCount(time,collector.getCurrent(),context);
	}
	private void writeTimeperiodCount(Path timeperiodCountOutput,long pairsCount) throws IOException {
		FileSystem fs = HadoopToolsUtil.getFileSystem(timeperiodCountOutput);
		PrintWriter writer = new PrintWriter(fs.create(timeperiodCountOutput));
		writer.println(pairsCount);
		writer.flush();
		writer.close();
	}

	private void emitPairCount(long time, TokenPairCount currentcount, Reducer<BytesWritable,BytesWritable,BytesWritable,BytesWritable>.Context context) throws IOException, InterruptedException {
		long tok1count = this.unaryCounts.get(currentcount.firstObject());
		long tok2count = this.unaryCounts.get(currentcount.secondObject());
		BytesWritable key = new BytesWritable(currentcount.identifierBinary(time));
		TokenPairUnaryCount tpuc = new TokenPairUnaryCount(currentcount, tok1count,tok2count);
		context.write(key, new BytesWritable(IOUtils.serialize(tpuc)));
	}
	private void addUnaryWordCount(TokenPairCount currentcount) {
		this.unaryCounts.put(currentcount.firstObject(), currentcount.paircount);
	}
}
