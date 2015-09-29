package gr.iti.mklab.focused.crawler.bolts.webpages;

import static backtype.storm.utils.Utils.tuple;

import java.util.Date;
import java.util.Map;
import java.util.PriorityQueue;

import org.apache.log4j.Logger;

import gr.iti.mklab.framework.common.domain.WebPage;
import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.utils.Utils;

public class RankerBolt extends BaseRichBolt {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private Logger logger;
	
	private static long avgTimeDiff = 10 * 60 * 1000; // 10 minutes 
	
	private OutputCollector _collector;
	private PriorityQueue<WebPage> _queue;

	private String inputField;

	public RankerBolt(String inputField) {
		this.inputField = inputField;
	}
	
	public void prepare(@SuppressWarnings("rawtypes") Map stormConf, TopologyContext context,
			OutputCollector collector) {
		this._collector = collector;
		this._queue = new PriorityQueue<WebPage>();
		
		logger = Logger.getLogger(RankerBolt.class);
		
		Thread[] threads = new Thread[4];
		for(int i=0; i<4; i++) {
			threads[i] = new Thread(new RankerThread(_queue));
			threads[i].start();
		}
	}

	public void execute(Tuple input) {
		try {
			String json = input.getStringByField(inputField);
			
			WebPage webPage = WebPage.toObject(json, WebPage.class);
			
			if(webPage != null) {
				double score = getScore(webPage);
				webPage.setScore(score);
			
				synchronized(_queue) {
					_queue.offer(webPage);
				}
			}
		} catch(Exception e) {
				logger.error("Exception: "+e.getMessage());
		}
	}

	public void declareOutputFields(OutputFieldsDeclarer declarer) {
		declarer.declare(new Fields("webpages"));
	}

	private double getScore(WebPage wp) {
		
		int shares = wp.getShares();
		Date date = wp.getDate();
		long publicationTime = date==null? 0L : date.getTime();
		
		double sharesScore = 1 - Math.exp(-0.05 * shares);
		sharesScore = (sharesScore + 1) / 2;
		
		long current = System.currentTimeMillis();
		double pubTimeScore = Math.exp(-(current - publicationTime)/avgTimeDiff);
		pubTimeScore = (pubTimeScore + 1)/2;
		
		return sharesScore * pubTimeScore;
	}
	
	class RankerThread extends Thread {

		PriorityQueue<WebPage> queue;
		
		public RankerThread(PriorityQueue<WebPage> queue) {	
			this.queue = queue;
		}

		public void run() {
			while(true) {
				try {
					WebPage rankedWebPage = null;
					synchronized(_queue) {
						rankedWebPage = queue.poll();
					}
					
					if(rankedWebPage == null) {
						Utils.sleep(500);
					}
					else {

						synchronized(_collector) {
							_collector.emit(tuple(rankedWebPage));
						}
					}
				}
				catch(Exception e) {
					Utils.sleep(500);
					continue;
				}
			}
		};
	}

}
