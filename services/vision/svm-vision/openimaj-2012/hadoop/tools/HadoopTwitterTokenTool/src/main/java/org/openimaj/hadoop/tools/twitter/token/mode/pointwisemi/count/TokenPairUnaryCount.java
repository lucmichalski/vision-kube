package org.openimaj.hadoop.tools.twitter.token.mode.pointwisemi.count;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Scanner;

/**
 * A Pair count with a unary count for each item of the pair.
 * 
 * The values here are the 3 counting functions:
 * c(x,y) = Number of times the pair x and y were seen together
 * c(x) = Number of times x was seen with ANY other token
 * c(y) = Number of times y was seen with ANY other token
 *
 *
@References(references = { 
	@Reference(
		author    = {Benjamin Van Durme and Ashwin Lall},
		title     = {Streaming Pointwise Mutual Information},
		booktitle = {NIPS},
		year      = {2009},
		pages     = {1892-1900}
	)
})
 * 
 * @author Sina Samangooei (ss@ecs.soton.ac.uk)
 *
 *
 */
public class TokenPairUnaryCount extends TokenPairCount{
	/**
	 * count of token 1 pairs
	 */
	public long tok1count;
	/**
	 * count of token 2 pairs
	 */
	public long tok2count;

	/**
	 * Convenience
	 */
	public TokenPairUnaryCount() {
	}
	/**
	 * @param tok1 the first token (x)
	 * @param tok2 the second token (y)
	 * @param paircount the count of the tokens together (c(x,y))
	 * @param tok1count the count of the first token with any other token (c(x))
	 * @param tok2count the count of the second token with any other token (c(y))
	 */
	public TokenPairUnaryCount(String tok1, String tok2, long paircount, long tok1count, long tok2count){
		super(tok1,tok2);
		this.paircount = paircount;
		this.tok1count = tok1count;
		this.tok2count = tok2count;
	}
	
	/**
	 * same as {@link TokenPairUnaryCount#TokenPairUnaryCount(String, String, long, long, long)} using the values from
	 * the {@link TokenPairCount} instance
	 * @param tpc
	 * @param tok1count
	 * @param tok2count
	 */
	public TokenPairUnaryCount(TokenPairCount tpc, long tok1count,long tok2count) {
		this(tpc.firstObject(),tpc.secondObject(),tpc.paircount,tok1count,tok2count);
	}
	
	@Override
	public void writeBinary(DataOutput out) throws IOException {
		super.writeBinary(out);
		out.writeLong(tok1count);
		out.writeLong(tok2count);
	}
	
	@Override
	public void readBinary(DataInput in) throws IOException {
		super.readBinary(in);
		this.tok1count = in.readLong();
		this.tok2count = in.readLong();
	}
	
	@Override
	public void writeASCII(PrintWriter out) throws IOException {
		super.writeASCII(out);
		out.println(this.tok1count);
		out.println(this.tok2count);
	}
	@Override
	public void readASCII(Scanner in) throws IOException {
		super.readASCII(in);
		this.tok1count = Long.parseLong(in.nextLine());
		this.tok2count = Long.parseLong(in.nextLine());
	}
	
	/**
	 * Calculate the Pointwise mutual information score such that:
	 * PMI(x,y) = log( p(x,y) / ( p(x) p(y) ) )
	 * where we can estimate the probabilities as:
	 * p(x,y) = c(x,y) / n
	 * p(x) = c(x) / n
	 * p(y) = c(y) / n
	 * 
	 * where n is the total number of pairs observed
	 * 
	 * @param n the total number of pairs observed
	 * @return the PMI estimate 
	 */
	public double pmi(double n){
		return Math.log((this.paircount / n) / ( ( this.tok1count / n ) * ( this.tok2count / n ) )) ;
	}
}
