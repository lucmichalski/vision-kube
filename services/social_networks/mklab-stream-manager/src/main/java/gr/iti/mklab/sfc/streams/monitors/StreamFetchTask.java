package gr.iti.mklab.sfc.streams.monitors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.Logger;

import gr.iti.mklab.framework.common.domain.feeds.Feed;
import gr.iti.mklab.framework.retrievers.Response;
import gr.iti.mklab.sfc.streams.Stream;


/**
 * Class for handling a Stream Task that is responsible for retrieving
 * content for the stream it is assigned to.
 * 
 * @author Manos Schinas
 * @email  manosetro@iti.gr
 */
public class StreamFetchTask implements  Callable<Integer> {
	
	private final Logger logger = Logger.getLogger(StreamFetchTask.class);
	
	private Stream stream;
	
	private Map<String, Pair<Feed, Long>> feeds = Collections.synchronizedMap(new HashMap<String, Pair<Feed, Long>>());

	private int maxRequests;
	private long period;
	
	private AtomicInteger requests = new AtomicInteger(0);

	private long lastExecutionTime = 0l;
	
	public StreamFetchTask(Stream stream) throws Exception {
		this.stream = stream;
		
		this.maxRequests = stream.getMaxRequests();
		this.period = stream.getTimeWindow() * 60000;
		
	}
	
	/**
	 * Adds the input feeds to search for relevant content in the stream
	 * 
	 * @param Feed feed
	 */
	public void addFeed(Feed feed) {
		this.feeds.put(feed.getId(), Pair.of(feed, 0L));
	}
	
	/**
	 * Adds the input feeds to search for relevant content in the stream
	 * 
	 * @param List<Feed> feeds
	 */
	public void addFeeds(List<Feed> feeds) {
		for(Feed feed : feeds) {
			addFeed(feed);
		}
	}
	
	/**
	 * Remove input feed from task
	 * 
	 * @param Feed feed
	 */
	public void removeFeed(Feed feed) {
		this.feeds.remove(feed.getId());
	}

	/**
	 * Remove input feed from task
	 * 
	 * @param Feed feed
	 */
	public void removeFeeds(List<Feed> feeds) {
		for(Feed feed : feeds) {
			removeFeed(feed);
		}
	}
	
	public List<Feed> getFeedsToPoll() {
		List<Feed> feedsToPoll = new ArrayList<Feed>();
		
		long currentTime = System.currentTimeMillis();
		// Check for new feeds
		for(Pair<Feed, Long> feed : feeds.values()) {
			if((currentTime - feed.getRight()) > period) { 
				feedsToPoll.add(feed.getKey());
			}
		}
		return feedsToPoll;
	}
	
	/**
	 * Retrieves content using the feeds assigned to the task
	 * making rest calls to stream's API. 
	 */
	@Override
	public Integer call() throws Exception {
		
		int totalItems = 0;
		
		try {
			
			long currentTime = System.currentTimeMillis();
			if(currentTime -  lastExecutionTime > period) {
				requests.set(0);
				lastExecutionTime = currentTime;
			}

			List<Feed> feedsToPoll = getFeedsToPoll();
			if(!feedsToPoll.isEmpty()) {
				
				int numOfFeeds = feedsToPoll.size();
				
				int remainingRequests = (maxRequests - requests.get()) / numOfFeeds;
				if(remainingRequests < 1) {
					logger.info("Remaining Requests: " + remainingRequests + " for " + stream.getName());
					return totalItems;
				}
				
				for(Feed feed : feedsToPoll) {
					
					logger.info("Poll for " + feed);
					
					Response response = stream.poll(feed, remainingRequests);
					totalItems += response.getNumberOfItems();
					
					requests.addAndGet(response.getRequests());
					
					this.feeds.put(feed.getId(), Pair.of(feed, System.currentTimeMillis()));
				}
				
				return totalItems;
			}
			
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("ERROR IN STREAM FETCH TASK: " + e.getMessage());
		}	
		
		return totalItems;
	}
}
