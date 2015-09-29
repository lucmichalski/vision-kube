package eu.socialsensor.framework.retrievers.socialmedia;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.plus.Plus;
import com.google.api.services.plus.Plus.People;
import com.google.api.services.plus.Plus.People.Get;
import com.google.api.services.plus.PlusRequestInitializer;
import com.google.api.services.plus.model.Activity;
import com.google.api.services.plus.model.ActivityFeed;
import com.google.api.services.plus.model.PeopleFeed;
import com.google.api.services.plus.model.Person;

import eu.socialsensor.framework.abstractions.socialmedia.gplus.GooglePlusItem;
import eu.socialsensor.framework.abstractions.socialmedia.gplus.GooglePlusStreamUser;
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
 * Class responsible for retrieving Google+ content based on keywords or google+ users
 * The retrieval process takes place through Google API
 * @author ailiakop
 * @email  ailiakop@iti.gr
 */
public class GooglePlusRetriever implements SocialMediaRetriever{
	private Logger logger = Logger.getLogger(GooglePlusRetriever.class);
	
	private static final HttpTransport transport = new NetHttpTransport();
	private static final JsonFactory jsonFactory = new JacksonFactory();
	
	private Plus plusSrv;
	private String userPrefix = "https://plus.google.com/+";
	private String GooglePlusKey;
	
	//private int pageLimit = 10;
	private int maxResults;
	private int maxRequests;
	
	private long maxRunningTime;
	
	public String getKey() { 
		return GooglePlusKey;
	}
	public String getSecret() {
		return null;
	}

	public GooglePlusRetriever(String key,Integer maxResults,Integer maxRequests,Long maxRunningTime) {
		GooglePlusKey = key;
		GoogleCredential credential = new GoogleCredential();
		plusSrv = new Plus.Builder(transport, jsonFactory, credential)
						.setApplicationName("SocialSensor")
						.setHttpRequestInitializer(credential)
						.setPlusRequestInitializer(new PlusRequestInitializer(GooglePlusKey)).build();
		
		this.maxResults = maxResults;
		this.maxRequests = maxRequests;
		this.maxRunningTime = maxRunningTime;
	}

	@Override
	public List<Item> retrieveUserFeeds(SourceFeed feed) {
		
		List<Item> items = new ArrayList<Item>();
		
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S");
		
		Date lastItemDate = feed.getDateToRetrieve();
		String label = feed.getLabel();
		
		int numberOfRequests = 0;
		
		boolean isFinished = false;
		
		Source source = feed.getSource();
		String uName = source.getName();
		String userID = source.getId();
		
		if(uName == null && userID == null) {
			logger.info("#GooglePlus : No source feed");
			return items;
		}
				
		//Retrieve userID from Google+
		StreamUser streamUser = null;
		Plus.People.Search searchPeople = null;
		List<Person> people;
		List<Activity> pageOfActivities;
		ActivityFeed activityFeed;
		Plus.Activities.List userActivities;
		
		try {
			if(userID == null) {
				searchPeople = plusSrv.people().search(uName);
				searchPeople.setMaxResults(20L);
				PeopleFeed peopleFeed = searchPeople.execute();
				people = peopleFeed.getItems();
				for(Person person : people) {
					if(person.getUrl().compareTo(userPrefix+uName) == 0) {
						userID = person.getId();
						streamUser = getStreamUser(userID);
						break;
					}
				}
			}
			else {
				streamUser = getStreamUser(userID);
			}
			
			//Retrieve activity with userID
			logger.info("Get public feed of " + userID);
			userActivities = plusSrv.activities().list(userID, "public");
			userActivities.setMaxResults(20L);
			activityFeed = userActivities.execute();
			pageOfActivities = activityFeed.getItems();
			numberOfRequests ++;
			
		} catch (Exception e) {
			logger.error(e);
			return items;
		}
		
		while(pageOfActivities != null && !pageOfActivities.isEmpty()) {
			try {
				for (Activity activity : pageOfActivities) {
				
					DateTime publicationTime = activity.getPublished();
					String PublicationTimeStr = publicationTime.toString();
					String newPublicationTimeStr = PublicationTimeStr.replace("T", " ").replace("Z", " ");
					
					Date publicationDate = null;
					try {
						publicationDate = (Date) formatter.parse(newPublicationTimeStr);
						
					} catch (ParseException e) {
						logger.error("#GooglePlus - ParseException: "+e);
						return items;
					}
					
					if(publicationDate.after(lastItemDate) && activity != null && activity.getId() != null
							&& items.size() < maxResults) {
						GooglePlusItem googlePlusItem = new GooglePlusItem(activity);
						googlePlusItem.setList(label);
						
						if(streamUser != null) {
							googlePlusItem.setStreamUser(streamUser);
						}
						
						items.add(googlePlusItem);
					}
					else {
						isFinished = true;
						break;
					}
					
				}
				numberOfRequests++;
				if(isFinished || numberOfRequests>maxRequests || (activityFeed.getNextPageToken() == null))
					break;
				 
				userActivities.setPageToken(activityFeed.getNextPageToken());
				activityFeed = userActivities.execute();
				pageOfActivities = activityFeed.getItems();
				
			} catch (IOException e) {
				logger.error("#GooglePlus Exception : "+e);
				return items;
			}
			
			if(isFinished){
				break;
			}
		}

		//logger.info("#GooglePlus : Done retrieving for this session");
//		logger.info("#GooglePlus : Handler fetched " + items.size() + " posts from " + uName + 
//				" [ " + lastItemDate + " - " + new Date(System.currentTimeMillis()) + " ]");
//		
		// The next request will retrieve only items of the last day
		Date dateToRetrieve = new Date(System.currentTimeMillis() - (24*3600*1000));
		feed.setDateToRetrieve(dateToRetrieve);
		
		return items;
	}
	
	@Override
	public List<Item> retrieveKeywordsFeeds(KeywordsFeed feed){
		List<Item> items = new ArrayList<Item>();
		
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S");
		Date lastItemDate = feed.getDateToRetrieve();
		String label = feed.getLabel();
		
		int totalRequests = 0;
		
		long currRunningTime = System.currentTimeMillis();
		
		boolean isFinished = false;
		
		Keyword keyword = feed.getKeyword();
		List<Keyword> keywords = feed.getKeywords();
		
		if(keywords == null && keyword == null) {
			logger.info("#GooglePlus : No keywords feed");
			return items;
		}
		
		String tags = "";
		
		if(keyword!=null) {
			for(String key : keyword.getName().split(" ")) {
				tags+=key.toLowerCase()+" ";
			}
		}
		else if(keywords != null) {
			for(Keyword key : keywords) {
				String [] words = key.getName().split(" ");
				for(String word : words) {
					if(!tags.contains(word) && word.length() > 1)
						tags += word.toLowerCase()+" ";
				}
			}
		}
		
		if(tags.equals(""))
			return items;
		
		Plus.Activities.Search searchActivities;
		ActivityFeed activityFeed;
		List<Activity> pageOfActivities;
		try {
			searchActivities = plusSrv.activities().search(tags);
			searchActivities.setMaxResults(20L);
			searchActivities.setOrderBy("recent");
			activityFeed = searchActivities.execute();
			pageOfActivities = activityFeed.getItems();
			totalRequests++;
		} catch (IOException e1) {
			return items;
		}
		
		Map<String, StreamUser> users = new HashMap<String, StreamUser>();
		
		while(pageOfActivities != null && !pageOfActivities.isEmpty()) {
			
			for (Activity activity : pageOfActivities) {
				
				if(activity.getObject().getAttachments() != null){
					
					DateTime publicationTime = activity.getPublished();
					String PublicationTimeStr = publicationTime.toString();
					String newPublicationTimeStr = PublicationTimeStr.replace("T", " ").replace("Z", " ");
					
					Date publicationDate = null;
					try {
						publicationDate = (Date) formatter.parse(newPublicationTimeStr);
						
					} catch (ParseException e) {
						logger.error("#GooglePlus - ParseException: " + e.getMessage());
						return items;
					}
					
					if(publicationDate.after(lastItemDate) && activity != null && activity.getId() != null) {
						GooglePlusItem googlePlusItem = new GooglePlusItem(activity);
						googlePlusItem.setList(label);
						
						String userID = googlePlusItem.getStreamUser().getUserid();
						StreamUser streamUser = null;
						if(userID != null && !users.containsKey(userID)) {
							streamUser = getStreamUser(userID);
							users.put(userID, streamUser);	
						}
						else {
							streamUser = users.get(userID);
						}
						if(streamUser != null)
							googlePlusItem.setStreamUser(streamUser);
						
						items.add(googlePlusItem);
						
					}
					if(items.size() > maxResults){
						isFinished = true;
						break;
					}
		
				}
		
			 }
			
			 totalRequests++;

			 if(totalRequests>maxRequests || isFinished || (activityFeed.getNextPageToken() == null) || (System.currentTimeMillis() - currRunningTime) > maxRunningTime)
				 break;
			 
			 searchActivities.setPageToken(activityFeed.getNextPageToken());
			 try {
				activityFeed = searchActivities.execute();
			} catch (IOException e) {
				logger.error("GPlus Retriever Exception: " + e.getMessage());
			}
			 pageOfActivities = activityFeed.getItems();
		
		}
		
//		logger.info("#GooglePlus : KeywordsFeed "+feed.getId()+" is done retrieving for this session");
//		logger.info("#GooglePlus : Handler fetched " + items.size() + " posts from " + tags + 
//				" [ " + lastItemDate + " - " + new Date(System.currentTimeMillis()) + " ]");
		
		// The next request will retrieve only items of the last day
		Date dateToRetrieve = new Date(System.currentTimeMillis() - (24*3600*1000));
		feed.setDateToRetrieve(dateToRetrieve);
		
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
		
		switch(feed.getFeedtype()){
			case SOURCE:
				SourceFeed userFeed = (SourceFeed) feed;
				if(!userFeed.getSource().getNetwork().equals("GooglePlus"))
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
	public void stop() {
		if(plusSrv != null) {
			plusSrv = null;
		}
	}
	
	@Override
	public MediaItem getMediaItem(String id) {
		return null;
	}
	
	@Override
	public StreamUser getStreamUser(String uid) {
		
		People peopleSrv = plusSrv.people();
		try {
			Get getRequest = peopleSrv.get(uid);
			Person person = getRequest.execute();
			
			StreamUser streamUser = new GooglePlusStreamUser(person);
			
			return streamUser;
		} catch (IOException e) {
			logger.error("Exception for user " + uid);
		}
		
		return null;
	}
	
	
	public static void main(String...args) {
		String uid = "102155862500050097100";
		
		Source source = new Source(null, 0);
		source.setId(uid);
		SourceFeed feed = new SourceFeed(source, new Date(System.currentTimeMillis()-24*3600000), "1");
		
		GooglePlusRetriever retriever = new GooglePlusRetriever("AIzaSyB-knYzMRW6tUzobP-V1hTWYAXEps1Wngk", 1, 1000, 60l);
		
		List<Item> items = retriever.retrieveUserFeeds(feed);
		for(Item item : items) {
			System.out.println(item.toJSONString());
		}
	}
}
