package gr.iti.mklab.focused.crawler.bolts.media;

import gr.iti.mklab.framework.client.search.solr.SolrMediaItemHandler;
import gr.iti.mklab.framework.client.search.solr.beans.MediaItemBean;
import gr.iti.mklab.framework.common.domain.MediaItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;

import org.apache.log4j.Logger;

import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.tuple.Tuple;


public class MediaTextIndexerBolt extends BaseRichBolt {

	/**
	 * 
	 */
	private static final long serialVersionUID = -7500656732029697927L;
	
	private Logger logger;
	
	private String service;

	private SolrMediaItemHandler _solrMediaHandler;

	private ArrayBlockingQueue<MediaItem> queue;
	
	public MediaTextIndexerBolt(String service) {
		this.service = service;
	}
	
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
    	
    }

	public void prepare(@SuppressWarnings("rawtypes") Map conf, TopologyContext context, 
			OutputCollector collector) {
		logger = Logger.getLogger(MediaTextIndexerBolt.class);
		
		queue = new ArrayBlockingQueue<MediaItem>(5000);
		try {
			_solrMediaHandler = SolrMediaItemHandler.getInstance(service);
		} catch (Exception e) {
			e.printStackTrace();
			_solrMediaHandler = null;
			logger.error(e);
		}
		
		Thread thread = new Thread(new TextIndexer());
		thread.start();
	}

	public void execute(Tuple tuple) {
		
		try {
			MediaItem mediaItem = (MediaItem) tuple.getValueByField("MediaItem");
		
			if(mediaItem == null || _solrMediaHandler == null)
				return;
			
			queue.add(mediaItem);
		}
		catch(Exception ex) {
			ex.printStackTrace();
			logger.error(ex);
		}
		
	}
 
	public class TextIndexer implements Runnable {

		public void run() {
			while(true) {
				try {
					// Just wait 15 seconds
					Thread.sleep(15 * 1000);

					List<MediaItem> mItems = new ArrayList<MediaItem>();
					queue.drainTo(mItems);
					
					if(mItems.isEmpty())
						continue;
					
					List<MediaItemBean> miBeans = new ArrayList<MediaItemBean>();
					for(MediaItem mi : mItems) {
						miBeans.add(new MediaItemBean(mi));
					}

					boolean inserted = _solrMediaHandler.insert(miBeans);
					
					if(inserted) {
						logger.info(mItems.size() + " media items indexed in Solr.");
					}
					else {
						logger.error("Indexing in Solr failed for some media items.");
					}
				} catch (Exception e) {
					logger.error(e);
					continue;
				}
			}
		}
		
	}
}