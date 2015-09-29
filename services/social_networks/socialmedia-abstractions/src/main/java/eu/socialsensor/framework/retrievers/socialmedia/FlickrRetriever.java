package eu.socialsensor.framework.retrievers.socialmedia;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;

import com.flickr4java.flickr.Flickr;
import com.flickr4java.flickr.FlickrException;
import com.flickr4java.flickr.REST;
import com.flickr4java.flickr.people.PeopleInterface;
import com.flickr4java.flickr.people.User;
import com.flickr4java.flickr.photos.Extras;
import com.flickr4java.flickr.photos.Photo;
import com.flickr4java.flickr.photos.PhotoList;
import com.flickr4java.flickr.photos.PhotosInterface;
import com.flickr4java.flickr.photos.SearchParameters;

import eu.socialsensor.framework.abstractions.socialmedia.flickr.FlickrItem;
import eu.socialsensor.framework.abstractions.socialmedia.flickr.FlickrStreamUser;
import eu.socialsensor.framework.common.domain.Feed;
import eu.socialsensor.framework.common.domain.Item;
import eu.socialsensor.framework.common.domain.Keyword;
import eu.socialsensor.framework.common.domain.MediaItem;
import eu.socialsensor.framework.common.domain.Source;
import eu.socialsensor.framework.common.domain.StreamUser;
import eu.socialsensor.framework.common.domain.feeds.KeywordsFeed;
import eu.socialsensor.framework.common.domain.feeds.ListFeed;
import eu.socialsensor.framework.common.domain.feeds.LocationFeed;
import eu.socialsensor.framework.common.domain.feeds.SourceFeed;


/**
 * Class responsible for retrieving Flickr content based on keywords,users or location coordinates
 * The retrieval process takes place through Flickr API.
 * @author ailiakop
 * @email  ailiakop@iti.gr
 */
public class FlickrRetriever implements SocialMediaRetriever {

	private Logger logger = Logger.getLogger(FlickrRetriever.class);
	
	private static final int PER_PAGE = 500;
	
	private String flickrKey;
	private String flickrSecret;
	
	private int maxResults;
	private int maxRequests;
	
	private long maxRunningTime;

	private Flickr flickr;

	private HashMap<String, StreamUser> userMap;
	
	public String getKey() { 
		return flickrKey;
	}
	public String getSecret() {
		return flickrSecret;
	}

	public FlickrRetriever(String flickrKey, String flickrSecret) {
		
		this.flickrKey = flickrKey;
		this.flickrSecret = flickrSecret;
		
		userMap = new HashMap<String, StreamUser>();
		
		Flickr.debugStream = false;
		
		this.flickr = new Flickr(flickrKey, flickrSecret, new REST());
	}
	
	public FlickrRetriever(String flickrKey, String flickrSecret, Integer maxResults, Integer maxRequests, long maxRunningTime) {
		
		this.flickrKey = flickrKey;
		this.flickrSecret = flickrSecret;
		this.maxResults = maxResults;
		this.maxRequests = maxRequests;
		this.maxRunningTime = maxRunningTime;
		
		userMap = new HashMap<String, StreamUser>();
		
		Flickr.debugStream = false;
		
		this.flickr = new Flickr(flickrKey, flickrSecret, new REST());
	}
	
	@Override
	public List<Item> retrieveUserFeeds(SourceFeed feed) {
		
		List<Item> items = new ArrayList<Item>();
		
		long currRunningTime = System.currentTimeMillis();
		
		Date dateToRetrieve = feed.getDateToRetrieve();
		String label = feed.getLabel();
		
		int page=1, pages=1; //pagination
		int numberOfRequests = 0;
		int numberOfResults = 0;
		
		//Here we search the user by the userId given (NSID) - 
		// however we can get NSID via flickrAPI given user's username
		Source source = feed.getSource();
		String userID = source.getId();
		
		if(userID == null) {
			logger.info("#Flickr : No source feed");
			return items;
		}
		
		PhotosInterface photosInteface = flickr.getPhotosInterface();
		SearchParameters params = new SearchParameters();
		params.setUserId(userID);
		params.setMinUploadDate(dateToRetrieve);
		
		Set<String> extras = new HashSet<String>(Extras.ALL_EXTRAS);
		extras.remove(Extras.MACHINE_TAGS);
		params.setExtras(extras);
		
		while(page<=pages && numberOfRequests<=maxRequests && numberOfResults<=maxResults &&
				(System.currentTimeMillis()-currRunningTime)<maxRunningTime) {
			
			PhotoList<Photo> photos;
			try {
				numberOfRequests++;
				photos = photosInteface.search(params , PER_PAGE, page++);
			} catch (Exception e) {
				break;
			}
			
			pages = photos.getPages();
			numberOfResults += photos.size();

			if(photos.isEmpty()) {
				break;
			}
		
			for(Photo photo : photos) {

				String userid = photo.getOwner().getId();
				StreamUser streamUser = userMap.get(userid);
				if(streamUser == null) {
					streamUser = getStreamUser(userid);
					userMap.put(userid, streamUser);
				}

				FlickrItem flickrItem = new FlickrItem(photo, streamUser);
				flickrItem.setList(label);
				
				items.add(flickrItem);
			}
		}
		
		//logger.info("#Flickr : Done retrieving for this session");
//		logger.info("#Flickr : Handler fetched " + items.size() + " photos from " + userID + 
//				" [ " + lastItemDate + " - " + new Date(System.currentTimeMillis()) + " ]");
		
		// The next request will retrieve only items of the last day
		dateToRetrieve = new Date(System.currentTimeMillis() - (24*3600*1000));
		feed.setDateToRetrieve(dateToRetrieve);
		
		return items;
	}
	
	@Override
	public List<Item> retrieveKeywordsFeeds(KeywordsFeed feed) {
		
		List<Item> items = new ArrayList<Item>();
		
		Date dateToRetrieve = feed.getDateToRetrieve();
		String label = feed.getLabel();
		
		int page=1, pages=1;
		
		int numberOfRequests = 0;
		int numberOfResults = 0;
		
		long currRunningTime = System.currentTimeMillis();
		
		Keyword keyword = feed.getKeyword();
		List<Keyword> keywords = feed.getKeywords();
		
		if(keywords == null && keyword == null) {
			logger.error("#Flickr : Text is emtpy");
			return items;
		}
		
		List<String> tags = new ArrayList<String>();
		String text = "";
		
		if(keyword != null) {
			String[] parts = keyword.getName().split("\\s+");
			for(String key : parts) {
				if(key.length()>1) {
					tags.add(key.toLowerCase().replace("\"", ""));
					text += key.toLowerCase()+" ";
				}
			}	
		}
		else if(keywords != null) {
			for(Keyword key : keywords) {
				String [] words = key.getName().split("\\s+");
				for(String word : words) {
					if(!tags.contains(word) && word.length()>1) {
						tags.add(word);
						text += (word + " ");
					}
				}
			}
		}
		
		if(text.equals("")) {
			logger.error("#Flickr : Text is emtpy");
			return items;
		}
		
		PhotosInterface photosInteface = flickr.getPhotosInterface();
		SearchParameters params = new SearchParameters();
		params.setText(text);
		params.setMinUploadDate(dateToRetrieve);
		
		Set<String> extras = new HashSet<String>(Extras.ALL_EXTRAS);
		extras.remove(Extras.MACHINE_TAGS);
		params.setExtras(extras);
		
		while(page<=pages && numberOfRequests<=maxRequests && numberOfResults<=maxResults &&
				(System.currentTimeMillis()-currRunningTime)<maxRunningTime) {
			
			PhotoList<Photo> photos;
			try {
				numberOfRequests++;
				photos = photosInteface.search(params , PER_PAGE, page++);
			} catch (Exception e) {
				logger.error("Exception: " + e.getMessage());
				continue;
			}
			
			pages = photos.getPages();
			numberOfResults += photos.size();

			if(photos.isEmpty()) {
				break;
			}
		
			for(Photo photo : photos) {

				String userid = photo.getOwner().getId();
				StreamUser streamUser = userMap.get(userid);
				if(streamUser == null) {
					streamUser = getStreamUser(userid);
					userMap.put(userid, streamUser);
				}

				FlickrItem flickrItem = new FlickrItem(photo, streamUser);
				flickrItem.setList(label);
				
				items.add(flickrItem);
			}
		}
			
//		logger.info("#Flickr : Done retrieving for this session");
//		logger.info("#Flickr : Handler fetched " + items.size() + " photos from " + text + 
//				" [ " + lastItemDate + " - " + new Date(System.currentTimeMillis()) + " ]");
		
		dateToRetrieve = new Date(System.currentTimeMillis() - (24*3600*1000));
		feed.setDateToRetrieve(dateToRetrieve);
		
		return items;
	}
	
	@Override
	public List<Item> retrieveLocationFeeds(LocationFeed feed){
		
		List<Item> items = new ArrayList<Item>();
		
		long currRunningTime = System.currentTimeMillis();
		
		Date dateToRetrieve = feed.getDateToRetrieve();
		String label = feed.getLabel();
		
		Double[][] bbox = feed.getLocation().getbbox();
		
		if(bbox == null || bbox.length==0)
			return items;
		
		int page=1, pages=1;
		int numberOfRequests = 0;
		int numberOfResults = 0;
		
		PhotosInterface photosInteface = flickr.getPhotosInterface();
		SearchParameters params = new SearchParameters();
		params.setBBox(bbox[0][0].toString(), bbox[0][1].toString(), bbox[1][0].toString(), bbox[1][1].toString());
		params.setMinUploadDate(dateToRetrieve);
		
		Set<String> extras = new HashSet<String>(Extras.ALL_EXTRAS);
		extras.remove(Extras.MACHINE_TAGS);
		params.setExtras(extras);
		
		while(page<=pages && numberOfRequests<=maxRequests && numberOfResults<=maxResults &&
				(System.currentTimeMillis()-currRunningTime)<maxRunningTime) {
			
			PhotoList<Photo> photos;
			try {
				photos = photosInteface.search(params , PER_PAGE, page++);
			} catch (FlickrException e) {
				break;
			}
			
			pages = photos.getPages();
			numberOfResults += photos.size();

			if(photos.isEmpty()) {
				break;
			}
		
			for(Photo photo : photos) {

				String userid = photo.getOwner().getId();
				StreamUser streamUser = userMap.get(userid);
				if(streamUser == null) {
					streamUser = getStreamUser(userid);

					userMap.put(userid, streamUser);
				}

				FlickrItem flickrItem = new FlickrItem(photo, streamUser);
				flickrItem.setList(label);
				
				items.add(flickrItem);
			}
		}
		
		logger.info("#Flickr : Handler fetched " + items.size() + " photos "+ 
				" [ " + dateToRetrieve + " - " + new Date(System.currentTimeMillis()) + " ]");
		
		return items;
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
				if(!userFeed.getSource().getNetwork().equals("Flickr"))
					return new ArrayList<Item>();
				
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
		if(flickr != null)
			flickr = null;
	}
	
	@Override
	public MediaItem getMediaItem(String id) {
		return null;
	}
	
	@Override
	public StreamUser getStreamUser(String uid) {
		try {
			PeopleInterface peopleInterface = flickr.getPeopleInterface();
			User user = peopleInterface.getInfo(uid);
			
			StreamUser streamUser = new FlickrStreamUser(user);
			return streamUser;
		}
		catch(Exception e) {
			return null;
		}
		
	}
	
	public static void main(String...args) {
		
		String flickrKey = "029eab4d06c40e08670d78055bf61205";
		String flickrSecret = "bc4105126a4dfb8c";
		
		FlickrRetriever retriever = new FlickrRetriever(flickrKey, flickrSecret, 1, 1000, 60000);
		
		Keyword keyword = new Keyword("\"uk\" amazing", 0d); 
		Feed feed = new KeywordsFeed(keyword, new Date(System.currentTimeMillis()-14400000), "1");
		
		List<Item> items = retriever.retrieve(feed );
		System.out.println(items.size());
	}
	
}
