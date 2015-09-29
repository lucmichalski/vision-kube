package gr.iti.mklab.focused.crawler.bolts.items;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import gr.iti.mklab.focused.crawler.utils.Snapshots;
import gr.iti.mklab.focused.crawler.utils.Vocabulary;
import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.tuple.Tuple;

public class EventDetectionBolt extends BaseRichBolt {

	private Snapshots<Vocabulary> _vocabularySnapshots;
	private Snapshots<Map<String, Double>> _idfShiftsSnapshots;
	
	private int _windows;
	private long _windowLength;

	private Vocabulary _currentVocabulary;

	private Logger _logger;
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -3868431827526927531L;

	public EventDetectionBolt(int windows, long windowLength) {
		_windows = windows;
		_windowLength = windowLength;
	}
	
	@Override
	public void prepare(@SuppressWarnings("rawtypes") Map stormConf, TopologyContext context,
			OutputCollector collector) {
		_logger = Logger.getLogger(EventDetectionBolt.class);
		
		_vocabularySnapshots = new Snapshots<Vocabulary>(_windows);
		_idfShiftsSnapshots = new Snapshots<Map<String, Double>>(_windows-1);
		
		_currentVocabulary = new Vocabulary();
		
		Thread thread = new Thread(new EventDetector());
		thread.start();
	}

	@Override
	public void execute(Tuple input) {
		try {
			@SuppressWarnings("unchecked")
			List<String> tokens = (List<String>) input.getValueByField("tokens");
			_currentVocabulary.addWords(tokens);
		}
		catch(Exception e) {
			_logger.error(e);
		}
	}

	@Override
	public void declareOutputFields(OutputFieldsDeclarer declarer) {
		
	}

	private class EventDetector implements Runnable {

		@Override
		public void run() {
			long t = 0;
			while(true) {
				try {
					Thread.sleep(Math.max(_windowLength * 1000 - t, 0));
				} catch (InterruptedException e) {
					_logger.error("event detector thread interrupted. ", e);
					break;
				}
				
				t = System.currentTimeMillis();
				
				_logger.info("Vocabulary: " + _currentVocabulary.size());
				Vocabulary previousVocabulary = null;
				try {
					previousVocabulary = _vocabularySnapshots.getLast();
				}
				catch(Exception e){
					_logger.error("Cannot take previous vocabulary. ");
				}
				
				Map<String, Double> idfShift = null;
				if(previousVocabulary != null) {
					idfShift = _currentVocabulary.getShift(previousVocabulary);
					_logger.info("IDF shifts: " + idfShift.size());
				}
				
				_logger.info("Vocabulary Snapshots: " + _vocabularySnapshots.size());
				
				Map<String, Double> CEs = new HashMap<String, Double>();
				if(_vocabularySnapshots.size() >= _windows) {
					for(String word : _currentVocabulary.getWords()) {
						double currentIdf = _currentVocabulary.getIdf(word);
						boolean isCandidate = true;
						for(Vocabulary pV : _vocabularySnapshots) {
							if(pV.hasWord(word)) {
								double previousIdf = pV.getIdf(word);
								if(currentIdf > previousIdf) {
									isCandidate = false;
									break;
								}
							}
							else {
								isCandidate = false;
								break;
							}
						}
						if(isCandidate) {
							Double currentShift = idfShift.get(word);
							for(Map<String, Double> pShifts : _idfShiftsSnapshots) {
								if(pShifts.containsKey(word)) {
									double previousShift = pShifts.get(word);
									if(currentShift < previousShift) {
										isCandidate = false;
										break;
									}
								}
								else {
									isCandidate = false;
									break;
								}
							}
						}
						
						if(isCandidate) {
							CEs.put(word, currentIdf);
						}
					}
				}
				
				_logger.info("Candidate events: " + CEs.size());
				_logger.info("Candidate events: " + CEs);
				_logger.info("=================================");
				
				_vocabularySnapshots.add(_currentVocabulary);
				
				if(idfShift != null)
					_idfShiftsSnapshots.add(idfShift);
				
				_currentVocabulary = new Vocabulary();
				
				t = System.currentTimeMillis() - t;
				
			}
			
		}
	}
}
