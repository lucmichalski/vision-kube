package org.openimaj.text.nlp.sentiment.type;

import gnu.trove.map.hash.TObjectIntHashMap;
import gnu.trove.procedure.TObjectIntProcedure;

import java.util.HashMap;
import java.util.Map;

import org.openimaj.text.nlp.sentiment.model.wordlist.util.TFF;
import org.openimaj.text.nlp.sentiment.model.wordlist.util.TFF.Polarity;

/**
 * A Discrete count sentiment is one which a set of arbitrary sentiments are given a count in a given phrase .
 * 
 * The sentiments hold values for counts of words considered to be one of the N sentiments provided and the total number of words the sentiment was 
 * decided against is also provided. 
 * 
 * An assumption is made that a single term can only have a single sentiment, therefore
 * sum(sentiment_count) &lt; total
 * though perhaps not equal as some terms may be none of the N sentiments (stop words etc.)
 * 
 * @author Sina Samangooei (ss@ecs.soton.ac.uk)
 *
 */
public class DiscreteCountSentiment implements Sentiment, BipolarSentimentProvider, WeightedBipolarSentimentProvider, DiscreteCountBipolarSentimentProvider{
	
	private static final class BipolarTFFPolarityIterator implements TObjectIntProcedure<TFF.Polarity> {
		public int negative = 0;
		public int neutral = 0;
		public int positive = 0;

		@Override
		public boolean execute(Polarity a, int b) {
			if(a.equals(Polarity.positive) || a.equals(Polarity.strongpos) || a.equals(Polarity.weakpos)){
				positive+=b;
			}
			else if(a.equals(Polarity.both)){
				positive+=b;
				negative+=b;
			}
			else if(a.equals(Polarity.neutral)){
				neutral+=b;
			}
			else{
				negative+=b;
			}
			return true;
		}
	}
	private TObjectIntHashMap<TFF.Polarity> sentiments;
	private int total;
	
	/**
	 * all weights set to 0
	 */
	public DiscreteCountSentiment() {
		sentiments = new TObjectIntHashMap<TFF.Polarity>();
		for (Polarity polarity : TFF.Polarity.values()) {
			sentiments.put(polarity, 0);
		}
	}
	
	/**
	 * @param total instnatiate with a total number of words
	 */
	public DiscreteCountSentiment(int total) {
		this();
		this.total = total;
	}

	/**
	 * @param entry
	 * @param increment
	 */
	public void incrementClue(TFF.Clue entry, int increment){
		this.sentiments.adjustOrPutValue(entry.polarity, increment, increment);
	}
	
	@Override
	public BipolarSentiment bipolar() {
		BipolarTFFPolarityIterator instance = new BipolarTFFPolarityIterator();
		this.sentiments.forEachEntry(instance);
		if(instance.positive > instance.negative){
			if(instance.positive > instance.neutral){
				return BipolarSentiment.POSITIVE;
			}
			else{
				return BipolarSentiment.NEUTRAL;
			}
		}
		else{
			if(instance.negative > instance.neutral){
				return BipolarSentiment.NEGATIVE;
			}
			else{
				return BipolarSentiment.NEUTRAL;
			}
		}
	}
	
	@Override
	public BipolarSentiment bipolar(double deltaThresh) {
		BipolarTFFPolarityIterator instance = new BipolarTFFPolarityIterator();
		this.sentiments.forEachEntry(instance);
		if(instance.positive > instance.negative * deltaThresh){
			if(instance.positive > instance.neutral * deltaThresh){
				return BipolarSentiment.POSITIVE;
			}
			else if(instance.neutral > instance.positive * deltaThresh){
				return BipolarSentiment.NEUTRAL;
			}
		}
		else{
			if(instance.negative > instance.neutral * deltaThresh){
				return BipolarSentiment.NEGATIVE;
			}
			else if(instance.neutral > instance.negative * deltaThresh){
				return BipolarSentiment.NEUTRAL;
			}
		}
		return null;
	}

	@Override
	public WeightedBipolarSentiment weightedBipolar() {
		BipolarTFFPolarityIterator instance = new BipolarTFFPolarityIterator();
		this.sentiments.forEachEntry(instance);
		return new WeightedBipolarSentiment(
			instance.positive / (double)this.total,
			instance.negative / (double)this.total,
			instance.neutral / (double)this.total
		);
	}
	@Override
	public DiscreteCountBipolarSentiment countBipolarSentiment() {
		BipolarTFFPolarityIterator instance = new BipolarTFFPolarityIterator();
		this.sentiments.forEachEntry(instance);
		try {
			return new DiscreteCountBipolarSentiment(
				instance.positive ,
				instance.negative ,
				instance.neutral ,
				this.total
			);
		} catch (InvalidSentimentException e) {
			return null; // should never happen
		}
	}
	@Override
	public Map<String, ?> asMap() {
		HashMap<String, Integer> ret = new HashMap<String,Integer>();
		for (Polarity polarity: TFF.Polarity.values()) {
			ret.put(polarity.name(), this.sentiments.get(polarity));
		}
		ret.put("total", total);
		return ret;
	}

	@Override
	public void fromMap(Map<String, ?> map) throws UnrecognisedMapException {
		for (Polarity polarity : TFF.Polarity.values()) {
			Object value = map.get(polarity.name());
			if(value == null) throw new UnrecognisedMapException("Could not find polarity: " + polarity);
			this.sentiments.put(polarity, (Integer)value);
		}
		if(!map.containsKey("total")) throw new UnrecognisedMapException("Could not find total");
		this.total = (Integer) map.get("total");
	}
	
	@Override
	public boolean equals(Object obj) {
		if(!(obj instanceof DiscreteCountSentiment)) return false;
		DiscreteCountSentiment that = (DiscreteCountSentiment) obj;
		if(this.total != that.total) return false;
		for (Object clue : this.sentiments.keys()) {
			if(this.sentiments.get(clue) != that.sentiments.get(clue))return false;
		}
		return true;
	}

	

}
