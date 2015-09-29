package eu.socialsensor.framework.abstractions.socialmedia.twitpic;

import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.google.api.client.util.Key;

import eu.socialsensor.framework.common.domain.MediaItem;
import eu.socialsensor.framework.common.domain.StreamUser;

/**
 * Class that holds the information regarding the twitpic media item
 * @author manosetro
 * @email  manosetro@iti.gr
 */
public class TwitPicMediaItem extends MediaItem {

	/**
	 * 
	 */
	private static final long serialVersionUID = -4938035976936525314L;

	private static SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	
	private static String urlBase = "http://d3j5vwomefv46c.cloudfront.net/photos/large/";
	private static String thumbBase = "http://d3j5vwomefv46c.cloudfront.net/photos/thumb/";
	private static String pageBase = "http://twitpic.com/";
	
	public TwitPicMediaItem(TwitPicImage image) throws Exception {
		super(new URL(urlBase + image.id + "." + image.type));
		
		//Id
		this.setId("Twitpic#"+image.id);
		//SocialNetwork Name
		this.setStreamId("Twitpic");
		//Type 
		this.setType("image");
		//Time of publication
		try {
			Date date = formatter.parse(image.timestamp);
			this.setPublicationTime(date.getTime());
		}
		catch(Exception e){
			
		}
		//PageUrl
		this.setPageUrl(pageBase + image.short_id);
		//Thumbnail
		this.setThumbnail(thumbBase + image.id + "." + image.type);
		//Title
		this.setTitle(image.message);
		//Tags
		if(image.tags != null) {
			this.setTags(image.tags.split(","));
		}
		//Popularity
		comments = new Long(image.number_of_comments);
		views = new Long(image.views);
		//Size
		this.setSize(image.width, image.height);
		
		this.setUserId(image.user_id);
		StreamUser user = new TwitPicStreamUser(image.user);
		this.setUser(user);
	}


	/**
	 * Class that holds the information regarding the twitpic image
	 * @author manosetro
	 * @email  manosetro@iti.gr
	 */
	public static class TwitPicImage {
		@Key
		public String id, message, tags, short_id, type;
		@Key
		public int views, number_of_comments, height, width;
		@Key
		public String timestamp;
		@Key
		public String user_id, location;
		@Key
		public TwitPicUser user;
	}

	/**
	 * Class that holds the information regarding the twitpic image
	 * @author manosetro
	 * @email  manosetro@iti.gr
	 */
	public static class TwitPicUser {
		@Key
		public String id, username, name, bio, avatar_url, timestamp, location;
		@Key
		public int photo_count;
	}
	
}
