package eu.socialsensor.framework.retrievers.socialmedia;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

//import com.maruti.otterapi.Otter4JavaException;
//import com.maruti.otterapi.TopsyConfig;
//import com.maruti.otterapi.search.Post;
//import com.maruti.otterapi.search.Search;
//import com.maruti.otterapi.search.SearchCriteria;
//import com.maruti.otterapi.search.SearchResponse;

import eu.socialsensor.framework.common.domain.Feed;
import eu.socialsensor.framework.common.domain.Item;
import eu.socialsensor.framework.common.domain.MediaItem;
import eu.socialsensor.framework.common.domain.StreamUser;
import eu.socialsensor.framework.common.domain.feeds.KeywordsFeed;
import eu.socialsensor.framework.common.domain.feeds.ListFeed;
import eu.socialsensor.framework.common.domain.feeds.LocationFeed;
import eu.socialsensor.framework.common.domain.feeds.SourceFeed;

/**
 * Class responsible for retrieving Topsy image content based on keywords
 * The retrieval process takes place through Topsy API.
 * @author ailiakop
 * @email  ailiakop@iti.gr
 */

public class TopsyRetriever implements SocialMediaRetriever{
	private Logger logger = Logger.getLogger(TopsyRetriever.class);
	
	//private TopsyConfig topsyConfig;
	
	public TopsyRetriever(String apiKey) {
		
		
		//topsyConfig = new TopsyConfig();
		//topsyConfig.setApiKey(apiKey);
		//topsyConfig.setSetProxy(false);
	}
	
	@Override
	public List<Item> retrieveUserFeeds(SourceFeed feed){
		return new ArrayList<Item>();
	}
	
	@Override
	public List<Item> retrieveKeywordsFeeds(KeywordsFeed feed){
		
		List<Item> items = new ArrayList<Item>();
		
		/*
		Date dateToRetrieve = feed.getDateToRetrieve();
		
		//SimpleDateFormat formatter = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z");
		
		Search searchTopsy = new Search();
		searchTopsy.setTopsyConfig(topsyConfig);
		SearchResponse results = null;
		try {
			SearchCriteria criteria = new SearchCriteria();
			criteria.setQuery(feed.getKeyword().getName());
			criteria.setType("image");
			results = searchTopsy.search(criteria);
			List<Post> posts = results.getResult().getList();
			for(Post post : posts){
				String since = post.getFirstpost_date();
			
				if(since != null) {
					Long publicationDate = Long.parseLong(since) * 1000;
					if(publicationDate > dateToRetrieve.getTime()) {
						TopsyItem topsyItem = new TopsyItem(post);						
						items.add(topsyItem);
					}
				}
	
			}			
		} catch (Otter4JavaException e) {
			e.printStackTrace();
		}
		*/
		
		return items;
	}
	
	@Override
	public List<Item> retrieveLocationFeeds(LocationFeed feed){
		return new ArrayList<Item>();
	}
	
	@Override
	public List<Item> retrieveListsFeeds(ListFeed feed) {
		return new ArrayList<Item>();
	}
	
	@Override
	public List<Item> retrieve (Feed feed) {
		
		switch(feed.getFeedtype()) {
			case SOURCE:
				SourceFeed userFeed = (SourceFeed) feed;
				
				return retrieveUserFeeds(userFeed);
				
			case KEYWORDS:
				KeywordsFeed keyFeed = (KeywordsFeed) feed;
				
				return retrieveKeywordsFeeds(keyFeed);
				
			case LOCATION:
				LocationFeed locationFeed = (LocationFeed) feed;
				
				return retrieveLocationFeeds(locationFeed);
			
			case LIST:
				ListFeed listFeed = (ListFeed) feed;
				return retrieveListsFeeds(listFeed);
			default:
				logger.error("Unkonwn Feed Type: " + feed.toJSONString());
				break;
		}
	
		return new ArrayList<Item>();
	}
	
	@Override
	public void stop(){

	}

	@Override
	public MediaItem getMediaItem(String id) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public StreamUser getStreamUser(String uid) {
		// TODO Auto-generated method stub
		return null;
	}

}
