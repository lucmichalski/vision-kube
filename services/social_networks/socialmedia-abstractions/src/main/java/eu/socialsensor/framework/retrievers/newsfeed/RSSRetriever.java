package eu.socialsensor.framework.retrievers.newsfeed;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;

import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.io.SyndFeedInput;
import com.sun.syndication.io.XmlReader;

import eu.socialsensor.framework.abstractions.newsfeed.rss.RSSItem;
import eu.socialsensor.framework.common.domain.Feed;
import eu.socialsensor.framework.common.domain.Item;
import eu.socialsensor.framework.common.domain.feeds.URLFeed;
import eu.socialsensor.framework.retrievers.Retriever;

/**
 * Class for retrieving rss feeds from official sources
 * The retrieval process takes place through ROME API. 
 * @author ailiakop
 * @email ailiakop@iti.gr
 */
public class RSSRetriever implements Retriever {
	
	public final Logger logger = Logger.getLogger(RSSRetriever.class);
	
	private long oneMonthPeriod = 2592000000L;
	
	@Override
	public List<Item> retrieve(Feed feed) {
		
		List<Item> items = new ArrayList<Item>();
		
		URLFeed ufeed = (URLFeed) feed;
		System.out.println("["+new Date()+"] Retrieving RSS Feed: " + ufeed.getURL());
		
		Integer totalRetrievedItems = 0;
		if(ufeed.getURL().equals(""))
			return items;
			
		URL url = null;
		try {
			url = new URL(ufeed.getURL());
		} catch (MalformedURLException e) {
			logger.error(e);
			return items;
		}
			
		XmlReader reader;
		try {
			reader = new XmlReader(url);
			SyndFeed rssData = new SyndFeedInput().build(reader);
			
			@SuppressWarnings("unchecked")
			List<SyndEntry> rssEntries = rssData.getEntries();
			
		
			for (SyndEntry rss : rssEntries) {		
				if(rss.getLink() != null) {
							
					if(rss.getPublishedDate() != null && rss.getPublishedDate().getTime()>0 && 
							Math.abs(System.currentTimeMillis() - rss.getPublishedDate().getTime())<oneMonthPeriod) {
								
						RSSItem rssItem = new RSSItem(rss);
								
						String label = feed.getLabel();
						rssItem.setList(label);
						
						items.add(rssItem);	
						totalRetrievedItems++;
						
						try {
							Thread.sleep(100);
						} catch (InterruptedException e) {
							logger.error(e);
							continue;
						}
					}
				}
			}
		} catch (IOException e) {
			logger.error(e);
			return items;
		} catch (Exception e) {
			logger.error(e);
			return items;
		}
	
		return items;
	}

	
	@Override
	public void stop() {
	
	}
	
	public static void main(String...args) {
		RSSRetriever retriever = new RSSRetriever();
		
		Feed feed = new URLFeed("http://ecowatch.com/feed/", new Date(System.currentTimeMillis()-3600000), "ecowatch");
		
		retriever.retrieve(feed);
	}
}
