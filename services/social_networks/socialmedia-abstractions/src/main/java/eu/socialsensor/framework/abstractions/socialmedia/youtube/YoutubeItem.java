package eu.socialsensor.framework.abstractions.socialmedia.youtube;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import org.apache.log4j.Logger;

import com.google.gdata.data.Person;
import com.google.gdata.data.extensions.Rating;
import com.google.gdata.data.media.mediarss.MediaDescription;
import com.google.gdata.data.media.mediarss.MediaPlayer;
import com.google.gdata.data.media.mediarss.MediaThumbnail;
import com.google.gdata.data.youtube.VideoEntry;
import com.google.gdata.data.youtube.YouTubeMediaContent;
import com.google.gdata.data.youtube.YouTubeMediaGroup;
import com.google.gdata.data.youtube.YtStatistics;

import eu.socialsensor.framework.common.domain.Item;
import eu.socialsensor.framework.common.domain.MediaItem;
import eu.socialsensor.framework.common.domain.SocialNetworkSource;

/**
 * Class that holds the information of a youtube video
 * @author ailiakop
 * @email  ailiakop@iti.gr
 */
public class YoutubeItem extends Item {

	/**
	 * 
	 */
	private static final long serialVersionUID = 6355819301582285835L;
	
	private Logger logger = Logger.getLogger(YoutubeItem.class);
	
	public YoutubeItem(String id, Operation operation) {
		super(SocialNetworkSource.Youtube.toString(), operation);
		setId(SocialNetworkSource.Youtube+"#"+id);
	}
	
	public YoutubeItem(VideoEntry videoEntry) {
		super(SocialNetworkSource.Youtube.toString(), Operation.NEW);
		
		if (videoEntry == null || videoEntry.getId() == null) 
			return;
		
		YouTubeMediaGroup mediaGroup = videoEntry.getMediaGroup();
		//Id
		id = SocialNetworkSource.Youtube+"#"+mediaGroup.getVideoId();
		//SocialNetwork Name
		streamId = SocialNetworkSource.Youtube.toString();
		//Timestamp of the creation of the video
		publicationTime = mediaGroup.getUploaded().getValue();
		//Title of the video
		title = mediaGroup.getTitle().getPlainTextContent();
		//Description of the video
		MediaDescription desc = mediaGroup.getDescription();
		description = desc==null ? "" : desc.getPlainTextContent();
		//User that uploaded the video
		List<Person> authors = videoEntry.getAuthors();
		if(authors.size()>0) {
			streamUser = new YoutubeStreamUser(authors.get(0));
		}
		else{
			if(mediaGroup.getUploader()!=null){
				streamUser = new YoutubeStreamUser(mediaGroup.getUploader());
			}
		}
		uid = streamUser.getId();
		//Popularity
		YtStatistics statistics = videoEntry.getStatistics();
		if(statistics!=null){
			likes = statistics.getFavoriteCount();
			
		}

		//Getting the video
		List<MediaThumbnail> thumbnails = mediaGroup.getThumbnails();
		MediaPlayer mediaPlayer = mediaGroup.getPlayer();
		
		// Page Url of this Item
		url = mediaPlayer.getUrl();
				
		String videoID = videoEntry.getId().substring(videoEntry.getId().indexOf("video:")+("video:").length());
		List<YouTubeMediaContent> mediaContent = mediaGroup.getYouTubeContents();
		
		String videoURL = null;
		for(YouTubeMediaContent content : mediaContent){
			if(content.getType().equals("application/x-shockwave-flash")) {
				videoURL = content.getUrl();
				break;
			}
		}	
		if (videoURL == null) 
			videoURL = mediaPlayer.getUrl();
		
		URL url = null;
		try {
			url = new URL(videoURL);
		} catch (MalformedURLException e1) {
			logger.error("Video URL is distorted");
		}
		
		int size = 0;
		MediaThumbnail thumbnail = null;
		for(MediaThumbnail thumb : thumbnails) {
			int t_size = thumb.getWidth()*thumb.getHeight();
			if(size < t_size) {
				size = t_size;
				thumbnail = thumb; 
			}
		}
		
		if(thumbnail != null) {
			//url
			MediaItem mediaItem = new MediaItem(url);
			
			String mediaId = SocialNetworkSource.Youtube + "#"+videoID; 
			
			//id
			mediaItem.setId(mediaId);
			//SocialNetwork Name
			mediaItem.setStreamId(streamId);
			//Reference
			mediaItem.setRef(id);
			//Type 
			mediaItem.setType("video");
			//Time of publication
			mediaItem.setPublicationTime(publicationTime);
			//Author
			if(streamUser != null) {
				mediaItem.setUser(streamUser);
				mediaItem.setUserId(streamUser.getId());
			}
			//PageUrl
			String pageUrl = mediaPlayer.getUrl();
			mediaItem.setPageUrl(pageUrl);
			//Thumbnail
			String thumbUrl = thumbnail.getUrl();
			mediaItem.setThumbnail(thumbUrl);	
			//Title
			mediaItem.setTitle(title);
			//Description
			mediaItem.setDescription(description);
			//Tags
			mediaItem.setTags(tags);
			//Popularity
			if(statistics!=null){
				mediaItem.setLikes(statistics.getFavoriteCount());
				mediaItem.setViews(statistics.getViewCount());
			}
			Rating rating = videoEntry.getRating();
			if(rating != null) {
				mediaItem.setRatings(rating.getAverage());
			}
			//Size
			mediaItem.setSize(thumbnail.getWidth(), thumbnail.getHeight());
			
			mediaItems.add(mediaItem);
			mediaIds.add(mediaId);
		}

	}
	
	public YoutubeItem(VideoEntry videoEntry, YoutubeStreamUser user) {
		
		this(videoEntry);
		
		//User that posted the post
		streamUser = user;
		uid = streamUser.getId();
		
		for(MediaItem mItem : this.mediaItems) {
			mItem.setUserId(uid);
		}
		
	}
	
	
}
