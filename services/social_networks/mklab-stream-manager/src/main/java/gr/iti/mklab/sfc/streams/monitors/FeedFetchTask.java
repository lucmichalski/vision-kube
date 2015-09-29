package gr.iti.mklab.sfc.streams.monitors;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import gr.iti.mklab.framework.common.domain.feeds.Feed;
import gr.iti.mklab.framework.retrievers.Retriever;

/**
 * A feed task that supports a retrieval process for one input feed. 
 * 
 * @author Manos Schinas
 * @email manosetro@iti.gr
 * 
 */
public class FeedFetchTask implements Runnable {

	private ScheduledFuture<?> taskHandle;
	private Retriever retriever;
	
	public TimeUnit timeUnit = MILLISECONDS;
	private long rate = 30 * 60 * 1000; 			// Request rate: 30 minutes by default  

	private Feed feed;
	private Integer retrievedItems;
	
	private boolean completed = false;
	private boolean needToPause = false;

	public FeedFetchTask(Feed feed, Retriever retriever) {
		
		this.feed = feed;
		this.retriever = retriever;
	}
	
	@Override
	public void run() {
		System.out.println("Feed Task that handles Feed : "+feed.getId()+" starts retrieving");
		try {
			retrievedItems = retriever.retrieve(feed).getNumberOfItems();
		} catch (Exception e) {
			
		}
		System.out.println("Feed Task that handles Feed : "+feed.getId()+" done retrieving");
		completed = true;
	}
	
	public Integer getRetrievedItems() {
		return retrievedItems;
	}
	
	public boolean isCompleted() {
		return completed;
	}
	
	public boolean getPaused() {
		return needToPause;
	}

	public long getScheduleRate() {
		return rate;
	}

	public long getRate() {
		return rate;
	}
	public Feed getFeed() {
		return feed;
	}
	public void setRate(long rate) {
		this.rate = rate;
	}
	
	public TimeUnit getTimeUnit() {
		return timeUnit;
	}
	
	public void addHandle(ScheduledFuture<?> taskHandle) {
		this.taskHandle = taskHandle;
	}

	public boolean stop() {
		System.out.println("Feed Task that handles Feed : "+feed.getId()+" is destroyed");
		return taskHandle.cancel(true);
	}

	public String getFeedId() {
		return feed.getId();
	}
	
}
