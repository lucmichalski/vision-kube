package gr.iti.mklab.sfc.streams;

import org.apache.log4j.Logger;

import gr.iti.mklab.framework.common.domain.Item;
import gr.iti.mklab.framework.common.domain.config.Configuration;
import gr.iti.mklab.framework.common.domain.feeds.Feed;
import gr.iti.mklab.framework.retrievers.Response;
import gr.iti.mklab.framework.retrievers.Retriever;
import gr.iti.mklab.sfc.management.StorageHandler;
import gr.iti.mklab.sfc.streams.monitors.RateLimitsMonitor;

/**
 * Class responsible for handling the stream of information regarding 
 * a social network or a news feed source.
 * It is responsible for the configuration of the connection to the selected API
 * and the retrieval/storing of relevant content.
 * 
 * @author manosetro
 * @email  manosetro@iti.gr
 *
 */
public abstract class Stream {

	protected static final String KEY = "Key";
	protected static final String SECRET = "Secret";
	protected static final String ACCESS_TOKEN = "AccessToken";
	protected static final String ACCESS_TOKEN_SECRET = "AccessTokenSecret";
	protected static final String CLIENT_ID = "ClientId";
	
	protected static final String MAX_REQUESTS = "maxRequests";
	protected static final String TIME_WINDOW = "timeWindow";
	
	// Default value 10 requests / minute
	protected int maxRequests = 10;
	protected long timeWindow = 1;
	
	//protected BlockingQueue<Feed> feedsQueue;
	protected Retriever retriever = null;
	protected StorageHandler handler = null;
	
	protected RateLimitsMonitor rateLimitsMonitor;
	
	private Logger  logger = Logger.getLogger(Stream.class);
	
	/**
	 * Opens a stream for updates delivery
	 * @param config
	 *      Stream configuration parameters
	 * @throws StreamException
	 *      In any case of error during stream open
	 */
	public abstract void open(Configuration config) throws StreamException;
	
	/**
	 * Closes a stream 
	 * @throws StreamException
	 *      In any case of error during stream close
	 */
	public void close() throws StreamException {
		if(retriever != null) {
			logger.info("Stop retriever");
			retriever.stop();
		}
		
		logger.info("Close Stream  : " + this.getClass().getName());
	}
		
	/**
	 * Sets the handler that is responsible for the handling 
	 * of the retrieved items
	 * @param handler
	 */
	public void setHandler(StorageHandler handler) {
		this.handler = handler;
	}
	
	/**
	 * Searches with the wrapper of the stream for a particular
	 * feed (feed can be keywordsFeeds, userFeeds, locationFeeds, listFeeds or URLFeeds)
	 * @param feeds
	 * @throws StreamException
	 */
	public synchronized Response poll(Feed feed, int requests) throws StreamException {
		Response response = new Response(); 
		if(retriever != null) {
			if(feed == null) {
				logger.error("Feeds is null in poll method.");
				return response;
			}
		
			try {
				response = retriever.retrieve(feed, requests);

				if(handler != null) {
					long sinceDate = 0l;
					for(Item item : response.getItems()) {
						if(sinceDate < item.getPublicationTime()) {
							sinceDate = item.getPublicationTime();
						}
						handler.handle(item);
					}
					
					// Set new since date 
					feed.setSinceDate(sinceDate);
				}
			}
			catch(Exception e) {
				logger.error("Exception for feed " + feed.getId() + " of type " + feed.getClass());
				logger.error(e.getMessage());
			}
			
			logger.info("Retrieved items for " + getName() + " are : " + response.getNumberOfItems());
		}
		else {
			throw new StreamException("Retriever is null for " + getName());
		}
		
		return response;
	}
	
	/**
	 * Adds a feed to the stream for future searching
	 * @param feed
	 * @return
	 *
	public boolean addFeed(Feed feed) {
		if(feedsQueue == null) {
			return false;
		}
		
		return feedsQueue.offer(feed);
	}
	*/
	
	/**
	 * Adds a set of feeds to the stream for future searching
	 * @param feeds
	 * @return
	 
	public boolean addFeeds(List<Feed> feeds) {
		for(Feed feed : feeds) {
			if(!addFeed(feed)) {
				return false;
			}
		}
		return true;
	}
	*/
	
	/*
	@Override
	public void run() {
		while(true) {
			try {
				Feed feed = feedsQueue.take();
				monitor.addFeed(feed);
				monitor.startMonitor(feed);
				
			} catch (InterruptedException e) {
				logger.error(e);
				return;
			}
		}
	}
	*/
	
	public abstract String getName();

	public int getMaxRequests() {
		return maxRequests;
	}
	
	public long getTimeWindow() {
		return timeWindow;
	}
	
}

