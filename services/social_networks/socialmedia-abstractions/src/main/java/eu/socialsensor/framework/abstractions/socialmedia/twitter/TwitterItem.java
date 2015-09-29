package eu.socialsensor.framework.abstractions.socialmedia.twitter;

import eu.socialsensor.framework.common.domain.Item;
import eu.socialsensor.framework.common.domain.Location;
import eu.socialsensor.framework.common.domain.MediaItem;
import eu.socialsensor.framework.common.domain.SocialNetworkSource;
import eu.socialsensor.framework.common.domain.WebPage;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import twitter4j.GeoLocation;
import twitter4j.HashtagEntity;
import twitter4j.MediaEntity;
import twitter4j.MediaEntity.Size;
import twitter4j.Place;
import twitter4j.Status;
import twitter4j.URLEntity;
import twitter4j.User;
import twitter4j.UserMentionEntity;

/**
 * Class that holds the information of a twitter status
 * @author manosetro
 * @email  manosetro@iti.gr
 */
public class TwitterItem extends Item {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	//private static final String RETWEET = "retweetCount";

	public TwitterItem(String id, Operation operation) {
		super(SocialNetworkSource.Twitter.toString(), operation);
		setId(SocialNetworkSource.Twitter+"#"+id);
	}
    
	public TwitterItem(Status status) {
		
		if (status == null) return;
		
		operation = Operation.NEW;
		
		//Id
		id = SocialNetworkSource.Twitter+"#"+status.getId();
		//SocialNetwork Name
		streamId = SocialNetworkSource.Twitter.toString();
		//Timestamp of the creation of the tweet
		publicationTime = status.getCreatedAt().getTime();
		//User that wrote the tweet
		User user = status.getUser();
		if (user != null) {
			streamUser = new TwitterStreamUser(user);
			uid = streamUser.getId();
		}
		
		url = "https://twitter.com/" + streamUser.getUsername() + "/statuses/" + status.getId();
		
		//Store/Update on the basis that it is an original tweet or a retweet
		Status retweetStatus = status.getRetweetedStatus();
		if(retweetStatus != null) {
			
			original = false;
			
			reference = SocialNetworkSource.Twitter + "#" + retweetStatus.getId();
			//super.referencedUser = retweetStatus.getUser().getScreenName();
			super.referencedUserId = SocialNetworkSource.Twitter + "#" + retweetStatus.getUser().getId();
		}
		else {
			original = true;
		}
		
		//Title of the tweet
		title = status.getText();
		lang = status.getLang();
		
		//Tags 
		HashtagEntity[] hashtags = status.getHashtagEntities();
		tags = null;
		if (hashtags != null) {
			tags = new String[hashtags.length];
			for (int i=0;i<tags.length;i++){
				tags[i] = hashtags[i].getText();
			}
		}
		//User that are mentioned inside the tweet
		UserMentionEntity[] userMentions = status.getUserMentionEntities();
		List<String> mentions = new ArrayList<String>();
		for(UserMentionEntity userMention : userMentions) {
			//String screenname = userMention.getScreenName();
			String mentionedUserId = SocialNetworkSource.Twitter + "#" + userMention.getId();
			mentions.add(mentionedUserId);
		}
		super.mentions = mentions.toArray(new String[mentions.size()]);
		if(status.getInReplyToUserId() > 0) {
			super.inReply = SocialNetworkSource.Twitter + "#" + status.getInReplyToUserId();
		}
		
		//Popularity
		likes = (long) status.getFavoriteCount();
		shares = (long) status.getRetweetCount();
			
		//Location
		GeoLocation geoLocation = status.getGeoLocation();
		if (geoLocation != null) {
			double latitude = status.getGeoLocation().getLatitude();
			double longitude = status.getGeoLocation().getLongitude();
			
			location = new Location(latitude, longitude);
		}
		Place place = status.getPlace();
		if (place != null) { 
			String placeName = place.getFullName();
			String country = place.getCountry();
			String city = place.getName();
			if(location==null) {
				location = new Location(placeName);
			}
			else {
				location.setName(placeName);
			}
			if(city != null)
				location.setCityName(city);
			if(country != null)
				location.setCountryName(country);
		}
		
		//WebPages inside the tweet
		URLEntity[] urlEntities = status.getURLEntities();
		Set<URL> urls = new HashSet<URL>();
		webPages = new ArrayList<WebPage>();
		
		if (urlEntities != null) {
			for (URLEntity urlEntity : urlEntities) {
				
				String urlStr = urlEntity.getExpandedURL();
				if (urlStr == null) {
					urlStr = urlEntity.getURL();
					if (urlStr == null) {
						urlStr = urlEntity.getDisplayURL();
					}
				}
				
				if(urlStr == null)
					continue;
				
				try {
					URL url = new URL(urlStr);
					urls.add(url);
					
					WebPage webPage = new WebPage(urlStr, id);
					webPage.setStreamId(streamId);
					webPage.setDate(new Date(publicationTime));
					
					webPages.add(webPage);
				} catch (Exception e) {
					continue;
				}	
				
			}
		}
		links = urls.toArray(new URL[urls.size()]);
			
		//MediaItems inside the tweet
		MediaEntity[] mediaEntities = status.getMediaEntities();
		if (mediaEntities != null) {
			for (MediaEntity mediaEntity : mediaEntities) {
				String mediaUrl = mediaEntity.getMediaURL();
				if (mediaUrl == null) {
					mediaUrl = mediaEntity.getMediaURLHttps();
				}
				URL temp_url;
				try {
					temp_url = new URL(mediaUrl);
				} catch (MalformedURLException e) {
					continue;
				}
				
				String pageUrl = mediaEntity.getExpandedURL();
				if(pageUrl == null) {
					pageUrl = mediaEntity.getURL();
				}
				
				//url
				MediaItem mediaItem = new MediaItem(temp_url);
					
				String mediaId = SocialNetworkSource.Twitter + "#" + mediaEntity.getId();
					
				//id
				mediaItem.setId(mediaId);
				//SocialNetwork Name
				mediaItem.setStreamId(streamId);
				//Reference
				mediaItem.setRef(id);
				//Type 
				mediaItem.setType("image");
				//Time of publication
				mediaItem.setPublicationTime(publicationTime);
				//Author
				if(streamUser != null) {
					mediaItem.setUser(streamUser);
					mediaItem.setUserId(streamUser.getId());
				}
				//PageUrl
				mediaItem.setPageUrl(pageUrl);
				//Thumbnail
				String thumbnail = mediaUrl+":thumb";
				mediaItem.setThumbnail(thumbnail);
				//Title
				mediaItem.setTitle(title);
				//Description
				mediaItem.setDescription(description);
				//Tags
				mediaItem.setTags(tags);
				//Popularity
				mediaItem.setLikes(likes);
				mediaItem.setShares(shares);
				//Location
				mediaItem.setLocation(location);
				//Size
				Map<Integer, Size> sizes = mediaEntity.getSizes();
				Size size = sizes.get(Size.MEDIUM);
				if(size != null) {
					mediaItem.setSize(size.getWidth(), size.getHeight());
				}

				mediaItems.add(mediaItem);
				mediaIds.add(mediaId);
			}
		}
	
	}

}
