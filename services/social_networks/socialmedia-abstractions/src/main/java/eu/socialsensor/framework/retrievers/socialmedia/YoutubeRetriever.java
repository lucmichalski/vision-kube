package eu.socialsensor.framework.retrievers.socialmedia;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;
import org.joda.time.DateTime;

import com.google.gdata.client.youtube.YouTubeQuery;
import com.google.gdata.client.youtube.YouTubeService;
import com.google.gdata.data.Link;
import com.google.gdata.data.extensions.Rating;
import com.google.gdata.data.media.mediarss.MediaDescription;
import com.google.gdata.data.media.mediarss.MediaPlayer;
import com.google.gdata.data.media.mediarss.MediaThumbnail;
import com.google.gdata.data.youtube.UserProfileEntry;
import com.google.gdata.data.youtube.VideoEntry;
import com.google.gdata.data.youtube.VideoFeed;
import com.google.gdata.data.youtube.YouTubeMediaContent;
import com.google.gdata.data.youtube.YouTubeMediaGroup;
import com.google.gdata.data.youtube.YtStatistics;
import com.google.gdata.util.ServiceException;

import eu.socialsensor.framework.abstractions.socialmedia.youtube.YoutubeItem;
import eu.socialsensor.framework.abstractions.socialmedia.youtube.YoutubeStreamUser;
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
import eu.socialsensor.framework.retrievers.socialmedia.SocialMediaRetriever;

/**
 * Class responsible for retrieving YouTube content based on keywords and YouTube users 
 * The retrieval process takes place through Google API 
 * @author ailiakop
 * @email  ailiakop@iti.gr
 */
public class YoutubeRetriever implements SocialMediaRetriever {

	private final String activityFeedUserUrlPrefix = "http://gdata.youtube.com/feeds/api/users/";
	private final String activityFeedVideoUrlPrefix = "http://gdata.youtube.com/feeds/api/videos";
	private final String uploadsActivityFeedUrlSuffix = "/uploads";
	
	private Logger logger = Logger.getLogger(YoutubeRetriever.class);
	private boolean loggingEnabled = false;
	
	private YouTubeService service;
	
	private int results_threshold;
	private int request_threshold;
	
	private long maxRunningTime;
	
	public YoutubeRetriever(String clientId, String developerKey) {	
		this.service = new YouTubeService(clientId, developerKey);
	}
	
	public YoutubeRetriever(String clientId, String developerKey, Integer maxResults, Integer maxRequests, Long maxRunningTime) {	
		this(clientId, developerKey);
		this.results_threshold = maxResults;
		this.request_threshold = maxRequests;
		this.maxRunningTime = maxRunningTime;
	}
	
	@Override
	public List<Item> retrieveUserFeeds(SourceFeed feed) {
		
		List<Item> items = new ArrayList<Item>();
		
		Date lastItemDate = feed.getDateToRetrieve();
		String label = feed.getLabel();
		
		boolean isFinished = false;
		
		Source source = feed.getSource();
		String uName = source.getName();
		
		int numberOfRequests = 0;
		
		if(uName == null){
			logger.error("#YouTube : No source feed");
			return items;
		}
				
		StreamUser streamUser = getStreamUser(uName);
		if(loggingEnabled)
			logger.info("#YouTube : Retrieving User Feed : "+uName);
		
		URL channelUrl = null;
		try {
			channelUrl = getChannelUrl(uName);
		} catch (MalformedURLException e) {
			logger.error("#YouTube Exception : " + e.getMessage());
			return items;
		}
		
		while(channelUrl != null) {
			
			try {
				VideoFeed videoFeed = service.getFeed(channelUrl, VideoFeed.class);
				//service.getEntry(channelUrl, UserProfileEntry.class);
				numberOfRequests ++ ;
				
				for(VideoEntry  video : videoFeed.getEntries()) {
					
					com.google.gdata.data.DateTime publishedTime = video.getPublished();
					DateTime publishedDateTime = new DateTime(publishedTime.toString());
					Date publicationDate = publishedDateTime.toDate();
					
					if(publicationDate.after(lastItemDate) && (video != null && video.getId() != null)) {
						YoutubeItem ytItem = new YoutubeItem(video);
						ytItem.setList(label);
						
						if(streamUser != null) {
							ytItem.setUserId(streamUser.getId());
							ytItem.setStreamUser(streamUser);
						}
						
						items.add(ytItem);
					}
					
					if(items.size()>results_threshold || numberOfRequests > request_threshold) {
						isFinished = true;
						break;
					}
						
				}
				
				if(isFinished)
					break;
				
				Link nextLink = videoFeed.getNextLink();
				channelUrl = nextLink==null ? null : new URL(nextLink.getHref());
				
			} catch (Exception e) {
				logger.error("#YouTube Exception : " + e.getMessage());
				return items;
			} 
		
		}
	
		if(loggingEnabled) {
			logger.info("#YouTube : Handler fetched " + items.size() + " videos from " + uName + 
					" [ " + lastItemDate + " - " + new Date(System.currentTimeMillis()) + " ]");
		}
	
		return items;
	}
	
	@Override
	public List<Item> retrieveKeywordsFeeds(KeywordsFeed feed) {
		
		List<Item> items = new ArrayList<Item>();
		
		Date lastItemDate = feed.getDateToRetrieve();
		String label = feed.getLabel();
		
		int startIndex = 1;
		int maxResults = 25;
		int currResults = 0;
		int numberOfRequests = 0;
		
		long currRunningTime = System.currentTimeMillis();
		
		boolean isFinished = false;
		
		Keyword keyword = feed.getKeyword();
		List<Keyword> keywords = feed.getKeywords();
		
		if(keywords == null && keyword != null) {
			logger.error("#YouTube : No keywords feed");
			return items;
		}
	
		String tags = "";
		
		if(keyword != null) {
			for(String key : keyword.getName().split(" ")) {
				if(key.length() > 1) {
					tags += key.toLowerCase()+" ";
				}
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
		//one call - 25 results
		if(tags.equals(""))
			return items;
	
		YouTubeQuery query;
		try {
			query = new YouTubeQuery(new URL(activityFeedVideoUrlPrefix));
		} catch (MalformedURLException e1) {
		
			return items;
		}
		
		query.setOrderBy(YouTubeQuery.OrderBy.PUBLISHED);
		query.setFullTextQuery(tags);
		query.setSafeSearch(YouTubeQuery.SafeSearch.NONE);
		query.setMaxResults(maxResults);
		
		VideoFeed videoFeed = new VideoFeed();
		
		while(true) {
			try {
				query.setStartIndex(startIndex);
				videoFeed = service.query(query, VideoFeed.class);
				
				numberOfRequests++;
				
				currResults = videoFeed.getEntries().size();
				startIndex +=currResults;
				
				for(VideoEntry  video : videoFeed.getEntries()) {
					com.google.gdata.data.DateTime publishedTime = video.getPublished();
					DateTime publishedDateTime = new DateTime(publishedTime.toString());
					Date publicationDate = publishedDateTime.toDate();
					
					if(publicationDate.after(lastItemDate) && (video != null && video.getId() != null)){
						YoutubeItem ytItem = new YoutubeItem(video);
						ytItem.setList(label);
						
						StreamUser tempStreamUser = ytItem.getStreamUser();
						if(tempStreamUser != null) {
							StreamUser user = this.getStreamUser(tempStreamUser);
							if(user != null) {
								ytItem.setUserId(user.getId());
								ytItem.setStreamUser(user);
							}
						}

						items.add(ytItem);
					}
					
					if(items.size()>results_threshold || numberOfRequests >= request_threshold || (System.currentTimeMillis() - currRunningTime) > maxRunningTime) {
						isFinished = true;
						break;
					}
				}
			
			}
			catch(Exception e) {
				e.printStackTrace();
				logger.error("YouTube Retriever error during retrieval of " + tags);
				logger.error("Exception: " + e.getMessage());
				return items;
			}
			
			if(maxResults>currResults || isFinished)	
				break;
		
		}
	
		if(loggingEnabled) {
			logger.info("#YouTube : Handler fetched " + items.size() + " videos from " + tags + 
					" [ " + lastItemDate + " - " + new Date(System.currentTimeMillis()) + " ]");
		}
		
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
		switch(feed.getFeedtype()) {
			case SOURCE:
				SourceFeed userFeed = (SourceFeed) feed;
				if(!userFeed.getSource().getNetwork().equals("Youtube"))
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
		 
		return null;
	}

	public void stop(){
		if(service != null){
			service = null;
		}
	}
	
	private URL getChannelUrl(String channel) throws MalformedURLException {
		StringBuffer urlStr = new StringBuffer(activityFeedUserUrlPrefix);
		urlStr.append(channel).append(uploadsActivityFeedUrlSuffix);
		
		return new URL(urlStr.toString());
	}
		
	public MediaItem getMediaItem(String id) {
		try {
			URL entryUrl = new URL(activityFeedVideoUrlPrefix +"/"+ id);
			VideoEntry entry = service.getEntry(entryUrl, VideoEntry.class);
			if(entry != null) {
				YouTubeMediaGroup mediaGroup = entry.getMediaGroup();
				List<YouTubeMediaContent> mediaContent = mediaGroup.getYouTubeContents();
				List<MediaThumbnail> thumbnails = mediaGroup.getThumbnails();
				
				String videoURL = null;
				for(YouTubeMediaContent content : mediaContent) {
					if(content.getType().equals("application/x-shockwave-flash")) {
						videoURL = content.getUrl();
						break;
					}
				}
				
				if(videoURL != null) {
					MediaPlayer mediaPlayer = mediaGroup.getPlayer();
					YtStatistics statistics = entry.getStatistics();
					
					Long publicationTime = entry.getPublished().getValue();
					
					String mediaId = "Youtube#" + mediaGroup.getVideoId();
					URL url = new URL(videoURL);
				
					String title = mediaGroup.getTitle().getPlainTextContent();
		
					MediaDescription desc = mediaGroup.getDescription();
					String description = desc==null ? "" : desc.getPlainTextContent();
					//url
					MediaItem mediaItem = new MediaItem(url);
					
					//id
					mediaItem.setId(mediaId);
					//SocialNetwork Name
					mediaItem.setStreamId("Youtube");
					//Type 
					mediaItem.setType("video");
					//Time of publication
					mediaItem.setPublicationTime(publicationTime);
					//PageUrl
					String pageUrl = mediaPlayer.getUrl();
					mediaItem.setPageUrl(pageUrl);
					//Thumbnail
					MediaThumbnail thumb = null;
					int size = 0;
					for(MediaThumbnail thumbnail : thumbnails) {
						int t_size = thumbnail.getHeight() * thumbnail.getWidth();
						if(t_size > size) {
							thumb = thumbnail;
							size = t_size;
						}
					}
					//Title
					mediaItem.setTitle(title);
					mediaItem.setDescription(description);
					
					//Popularity
					if(statistics!=null){
						mediaItem.setLikes(statistics.getFavoriteCount());
						mediaItem.setViews(statistics.getViewCount());
					}
					Rating rating = entry.getRating();
					if(rating != null) {
						mediaItem.setRatings(rating.getAverage());
					}
					//Size
					if(thumb!=null) {
						mediaItem.setThumbnail(thumb.getUrl());
						mediaItem.setSize(thumb.getWidth(), thumb.getHeight());
					}
					
					String uploader = mediaGroup.getUploader();
					StreamUser user = getStreamUser(uploader);
					if(user != null) {
						mediaItem.setUser(user);
						mediaItem.setUserId(user.getId());
					}
					
					return mediaItem;
				}
			}
		} catch (Exception e) {
			logger.error(e.getMessage());
		} 
		return null;
	}

	@Override
	public StreamUser getStreamUser(String uid) {
		URL profileUrl;
		try {
			profileUrl = new URL(activityFeedUserUrlPrefix + uid);
			UserProfileEntry userProfile = service.getEntry(profileUrl , UserProfileEntry.class);
			
			StreamUser user = new YoutubeStreamUser(userProfile);
			
			return user;
		} catch (MalformedURLException e) {
			//e.printStackTrace();
			logger.error(e.getMessage());
		} catch (IOException e) {
			//e.printStackTrace();
			logger.error(e.getMessage());
		} catch (ServiceException e) {
			//e.printStackTrace();
			logger.error(e.getMessage());
		}
		
		return null;
	}

	private StreamUser getStreamUser(StreamUser u) {
		URL profileUrl;
		try {
			profileUrl = new URL(u.getLinkToProfile());
			UserProfileEntry userProfile = service.getEntry(profileUrl , UserProfileEntry.class);
			
			StreamUser user = new YoutubeStreamUser(userProfile);
			
			return user;
		} catch (MalformedURLException e) {
			logger.error(e.getMessage());
		} catch (IOException e) {
			logger.error(e.getMessage());
		} catch (ServiceException e) {
			logger.error(e.getMessage());
		}
		
		return null;
	}
	
}
