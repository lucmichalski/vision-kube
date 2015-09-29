package gr.iti.mklab.focused.crawler.bolts.items;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import gr.iti.mklab.framework.common.domain.Item;
import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;

public class PosTaggingBolt extends BaseRichBolt {

	/**
	 * 
	 */
	private static final long serialVersionUID = -5138980213290646197L;
	
	private OutputCollector _collector = null;
	
	private String _taggerModelFile; 
	private MaxentTagger _tagger = null;
	
	public PosTaggingBolt(String taggerModelFile) {
		_taggerModelFile = taggerModelFile;
	}
	
	@Override
	public void prepare(@SuppressWarnings("rawtypes") Map stormConf, TopologyContext context,
			OutputCollector collector) {
		_collector = collector;
		_tagger = new MaxentTagger(_taggerModelFile);
	}

	@Override
	public void execute(Tuple input) {
		Item item = (Item)input.getValueByField("Item");
		if(item == null)
			return;
		
		String title = item.getTitle();
		if(title != null) {
			List<TaggedWord> taggedSentences = tag(title);
			_collector.emit(new Values(item, taggedSentences));
		}
	}

	
	@Override
	public void declareOutputFields(OutputFieldsDeclarer declarer) {
		declarer.declare(new Fields("Item", "PosTags"));
	}
	
	private List<TaggedWord> tag(String text) {
		List<TaggedWord> taggedSentences = new ArrayList<TaggedWord>();
		List<List<HasWord>> sentences = MaxentTagger.tokenizeText(new StringReader(text));
		for(List<HasWord> sentence : sentences) {
			List<TaggedWord> taggedWords = _tagger.tagSentence(sentence);	
			taggedSentences.addAll(taggedWords);
		}
		
		return taggedSentences;
	}
	
}
