package gr.iti.mklab.sfc.streams.monitors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import org.apache.log4j.Logger;

import gr.iti.mklab.framework.common.domain.feeds.Feed;
import gr.iti.mklab.framework.retrievers.Retriever;

/**
 * @author manosetro
 * @email manosetro@iti.gr
 * 
 * FeedsMonitor monitors feed fetchers. Currently supports only start and stop 
 * operations. In future, it will support re-scheduling based on adaptive request rates.
 * Reference: "Maintaining Dynamic Channel Profiles on The Web". 
 * 
 */
public class FeedsMonitor {
	
	private Logger _logger = Logger.getLogger(FeedsMonitor.class);
	
	private Map<String, FeedFetchTask> feedFetchTasks;
	private ScheduledExecutorService scheduler;
	
	private Retriever retriever;
	
	public FeedsMonitor(Retriever retriever) {
		feedFetchTasks = new HashMap<String, FeedFetchTask>();
		
		this.retriever = retriever;
	}
	
	public void addFeed(Feed feed) {
	
		FeedFetchTask fetcher = new FeedFetchTask(feed, retriever);
		feedFetchTasks.put(feed.getId(), fetcher);
	}
	
	public void addFeeds(Feed[] feeds) {
		for(Feed feed : feeds) {
			addFeed(feed);
		}
	}
	public void addFeeds(List<Feed> feeds) {
		for(Feed feed : feeds) {
			addFeed(feed);
		}
		//_logger.info(feeds.size()+" added to monitor");
	}
	public void removeFeed(Feed feed) {
		String feedId = feed.getId();
		FeedFetchTask feedFetcher = feedFetchTasks.get(feedId);
		feedFetcher.stop();
		feedFetchTasks.remove(feedId);
	}
	
	public Integer collectRetrievedItems() {
		Integer totalRetrievedItems = 0;
		
		for(FeedFetchTask  feedFetcherTask : feedFetchTasks.values()) {
			if(feedFetcherTask.getRetrievedItems() != null && feedFetcherTask.getRetrievedItems()!=0)
				totalRetrievedItems += feedFetcherTask.getRetrievedItems();
		}
		
		return totalRetrievedItems;
	}
	
	public void resetMonitor() {
		for(FeedFetchTask  feedFetcherTask : feedFetchTasks.values()) {
			feedFetcherTask.stop();
		}
		feedFetchTasks.clear();
	}
	
	public void startMonitor() {
		
		for(FeedFetchTask  feedFetcherTask : feedFetchTasks.values()) {
			ScheduledFuture<?> taskHandle = scheduler.scheduleAtFixedRate(
					feedFetcherTask, 0, feedFetcherTask.getRate(), feedFetcherTask.getTimeUnit());
			
			feedFetcherTask.addHandle(taskHandle);
		}
	}
	
	public void setSizeOfThreads(int size){
		scheduler = Executors.newScheduledThreadPool(size);
	}
	
	public boolean isMonitorFinished() {
		int allComplete = 0;
		List<FeedFetchTask> finishedTasks = new ArrayList<FeedFetchTask>();
		while(allComplete < feedFetchTasks.size()){
			for(FeedFetchTask  feedFetcherTask : feedFetchTasks.values()) {
				if(feedFetcherTask.isCompleted() && !finishedTasks.contains(feedFetcherTask)){
					finishedTasks.add(feedFetcherTask);
					
					allComplete++;
				}
			}
		}
		return true;
	}
	
	
	public void startMonitor(Feed feed) {
		String feedId = feed.getId();
		FeedFetchTask feedFetcher = feedFetchTasks.get(feedId);
		if(feedFetcher==null) {
			return;
		}
		ScheduledFuture<?> taskHandle = scheduler.scheduleAtFixedRate(
				feedFetcher, 0, feedFetcher.getScheduleRate(), feedFetcher.timeUnit);
		feedFetcher.addHandle(taskHandle);
	}
	
	public void stopMonitor() {
		for(FeedFetchTask  feedFetcher : feedFetchTasks.values()) {
			_logger.info("Stop: " + feedFetcher.getFeedId());
			feedFetcher.stop();
		}
	}
}
