package eu.socialsensor.framework.retrievers.socialmedia;

import java.net.MalformedURLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.scribe.exceptions.OAuthConnectionException;

import com.tumblr.jumblr.JumblrClient;
import com.tumblr.jumblr.exceptions.JumblrException;
import com.tumblr.jumblr.types.Blog;
import com.tumblr.jumblr.types.Post;

import eu.socialsensor.framework.abstractions.socialmedia.tumblr.TumblrItem;
import eu.socialsensor.framework.abstractions.socialmedia.tumblr.TumblrStreamUser;
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
 * Class responsible for retrieving Tumblr content based on keywords or tumblr users
 * The retrieval process takes place through Tumblr API (Jumblr)
 * @author ailiakop
 * @email  ailiakop@iti.gr
 */
public class TumblrRetriever implements SocialMediaRetriever {
	
	private Logger logger = Logger.getLogger(TumblrRetriever.class);
	
	private JumblrClient client;

	private int maxResults;
	private int maxRequests;
	
	private long maxRunningTime;
	

	public TumblrRetriever(String consumerKey, String consumerSecret, Integer maxResults, Integer maxRequests, Long maxRunningTime) {
		
		this.maxResults = maxResults;
		this.maxRequests = maxRequests;
		this.maxRunningTime = maxRunningTime;
		
		client = new JumblrClient(consumerKey,consumerSecret);
	}

	
	@Override
	public List<Item> retrieveUserFeeds(SourceFeed feed){
		List<Item> items = new ArrayList<Item>();
		
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S");
		Date lastItemDate = feed.getDateToRetrieve();
		
		int numberOfRequests = 0;
		
		boolean isFinished = false;
		
		Source source = feed.getSource();
		String uName = source.getName();
		
		if(uName == null){
			logger.info("#Tumblr : No source feed");
			return null;
		}
		
		Blog blog = client.blogInfo(uName);
		TumblrStreamUser tumblrStreamUser = new TumblrStreamUser(blog);
		List<Post> posts;
		Map<String,String> options = new HashMap<String,String>();
		
		Integer offset = 0;
		Integer limit = 20;
		options.put("limit", limit.toString());
	
		while(true){
			
			options.put("offset", offset.toString());
			
			posts = blog.posts(options);
			if(posts == null || posts.isEmpty())
				break;
			
			numberOfRequests ++;
			
			for(Post post : posts){
				
				if(post.getType().equals("photo") || post.getType().equals("video") || post.getType().equals("link")){
					
					String retrievedDate = post.getDateGMT().replace(" GMT", "");
					retrievedDate+=".0";
					
					Date publicationDate = null;
					try {
						publicationDate = (Date) formatter.parse(retrievedDate);
						
					} catch (ParseException e) {
						return items;
					}
					
					if(publicationDate.after(lastItemDate) && post != null && post.getId() != null){
						
						TumblrItem tumblrItem = null;
						try {
							tumblrItem = new TumblrItem(post,tumblrStreamUser);
						} catch (MalformedURLException e) {
							
							return items;
						}
						
						items.add(tumblrItem);
						
					}
				
				}
				if(items.size()>maxResults || numberOfRequests>maxRequests){
					isFinished = true;
					break;
				}
			}
			if(isFinished)
				break;
			
			offset+=limit;
		}

		//logger.info("#Tumblr : Done retrieving for this session");
//		logger.info("#Tumblr : Handler fetched " +totalRetrievedItems + " posts from " + uName + 
//				" [ " + lastItemDate + " - " + new Date(System.currentTimeMillis()) + " ]");
		
		return items;
	}
	
	@Override
	public List<Item> retrieveKeywordsFeeds(KeywordsFeed feed){
		
		List<Item> items = new ArrayList<Item>();
		
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S");
		Date currentDate = new Date(System.currentTimeMillis());
		Date indexDate = currentDate;
		Date lastItemDate = feed.getDateToRetrieve();
		DateUtil dateUtil = new DateUtil();
		
		int numberOfRequests=0;
		
		long currRunningTime = System.currentTimeMillis();
		
		boolean isFinished = false;
		
		Keyword keyword = feed.getKeyword();
		List<Keyword> keywords = feed.getKeywords();
		
		if(keywords == null && keyword == null){
			logger.info("#Tumblr : No keywords feed");
			return items;
		}
		
		String tags = "";
		if(keyword != null){
			for(String key : keyword.getName().split("\\s+")) {
				if(key.length()>1) {
					tags+=key.toLowerCase()+" ";
				}
			}
		}
		else if(keywords != null) {
			for(Keyword key : keywords) {
				String [] words = key.getName().split("\\s+");
				for(String word : words) {
					if(!tags.contains(word) && word.length()>1) {
						tags += word.toLowerCase()+" ";
					}
				}
			}
		}
		
		if(tags.equals(""))
			return items;
		
		while(indexDate.after(lastItemDate) || indexDate.equals(lastItemDate)){
			
			Map<String,String> options = new HashMap<String,String>();
			Long checkTimestamp = indexDate.getTime();
			Integer check = checkTimestamp.intValue();
			options.put("featured_timestamp", check.toString());
			List<Post> posts;
			try{
				posts = client.tagged(tags);
			}catch(JumblrException e){
				return items;
			}catch(OAuthConnectionException e1){
				return items;
			}
			
			if(posts == null || posts.isEmpty())
				break;
			
			numberOfRequests ++;
			
			for(Post post : posts){
				
				if(post.getType().equals("photo") || post.getType().equals("video") ||  post.getType().equals("link")) {
					
					String retrievedDate = post.getDateGMT().replace(" GMT", "");
					retrievedDate+=".0";
					Date publicationDate = null;
					try {
						publicationDate = (Date) formatter.parse(retrievedDate);
						
					} catch (ParseException e) {
						return items;
					}
					
					if(publicationDate.after(lastItemDate) && post != null && post.getId() != null){
						//Get the blog
						String blogName = post.getBlogName();
						Blog blog = client.blogInfo(blogName);
						TumblrStreamUser tumblrStreamUser = new TumblrStreamUser(blog);
						
						TumblrItem tumblrItem = null;
						try {
							tumblrItem = new TumblrItem(post, tumblrStreamUser);
						} catch (MalformedURLException e) {
							return items;
						}
						
						if(tumblrItem != null){
							items.add(tumblrItem);
						}
						
					}
				
				}
				
				if(items.size()>maxResults || numberOfRequests>=maxRequests || (System.currentTimeMillis() - currRunningTime) > maxRunningTime){
					isFinished = true;
					break;
				}
			}
			
			if(isFinished)
				break;
			
			indexDate = dateUtil.addDays(indexDate, -1);
				
		}
		
//		logger.info("#Tumblr : Done retrieving for this session");
//		logger.info("#Tumblr : Handler fetched " + items.size() + " posts from " + tags + 
//				" [ " + lastItemDate + " - " + new Date(System.currentTimeMillis()) + " ]");
		
		return items;
		
	}
	@Override
	public List<Item> retrieveLocationFeeds(LocationFeed feed) throws JumblrException {
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
				if(!userFeed.getSource().getNetwork().equals("Tumblr"))
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
		if(client != null){
			client = null;
		}
	}
	public class DateUtil
	{
	    public Date addDays(Date date, int days)
	    {
	        Calendar cal = Calendar.getInstance();
	        cal.setTime(date);
	        cal.add(Calendar.DATE, days); //minus number decrements the days
	        return cal.getTime();
	    }
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
