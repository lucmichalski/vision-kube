package eu.socialsensor.framework.abstractions.socialmedia.flickr;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.flickr4java.flickr.people.User;
import com.flickr4java.flickr.photos.GeoData;
import com.flickr4java.flickr.photos.Photo;
import com.flickr4java.flickr.tags.Tag;

import eu.socialsensor.framework.common.domain.Item;
import eu.socialsensor.framework.common.domain.Location;
import eu.socialsensor.framework.common.domain.MediaItem;
import eu.socialsensor.framework.common.domain.SocialNetworkSource;
import eu.socialsensor.framework.common.domain.StreamUser;

/**
 * Class that holds the information of a flickr photo
 * @author ailiakop
 * @email  ailiakop@iti.gr
 */
public class FlickrItem extends Item {

	/**
	 * 
	 */
	private static final long serialVersionUID = 4323341976887218659L;

	public FlickrItem(String id, Operation operation) {
		super(SocialNetworkSource.Flickr.toString(), operation);
		setId(SocialNetworkSource.Flickr+"#"+id);
	}
	
	@SuppressWarnings("deprecation")
	public FlickrItem(Photo photo) {
		super(SocialNetworkSource.Flickr.toString(), Operation.NEW);
		if (photo == null || photo.getId() == null) return;
		
		//Id
		id = SocialNetworkSource.Flickr + "#" + photo.getId();
		//SocialNetwork Name
		streamId = SocialNetworkSource.Flickr.toString();
		//Timestamp of the creation of the photo
		publicationTime = photo.getDatePosted().getTime();
		//Title of the photo
		if(photo.getTitle()!=null){
			
				title = photo.getTitle();
				text = photo.getTitle();
			
		}
		//Description of the photo
		description = photo.getDescription();
		//Tags of the photo
		Collection<Tag> photoTags = photo.getTags();
		if (photoTags != null) {
			List<String> tagsList = new ArrayList<String>();
			for(Tag tag : photoTags) {
				String tagStr = tag.getValue();
				if(tagStr != null && !tagStr.contains(":"))
					tagsList.add(tagStr);
			}
			tags = tagsList.toArray(new String[tagsList.size()]);
		}
		
		//User that posted the photo
        User user = photo.getOwner();
        if(user != null) {
        	streamUser = new FlickrStreamUser(user);
        	uid = streamUser.getId();
        }
        
		//Location
		if(photo.hasGeoData()){
			
			GeoData geo = photo.getGeoData();
			
			double latitude = (double)geo.getLatitude();
			double longitude = (double) geo.getLongitude();
			
			location = new Location(latitude, longitude);
		}
		
		url = photo.getUrl();
		
		//Popularity
		numOfComments = (long) photo.getComments();
		
		//Getting the photo
		try {
			String url = null;
			String thumbnail = photo.getMediumUrl();
			if(thumbnail==null) {
				thumbnail = photo.getThumbnailUrl();
			}
			URL mediaUrl = null;
			if((url = photo.getLargeUrl()) != null) {
				mediaUrl = new URL(url);
			
			}
			else if ((url = photo.getMediumUrl()) != null) {
				mediaUrl = new URL(url);
			}
			
			if(mediaUrl != null) {
				//url
				MediaItem mediaItem = new MediaItem(mediaUrl);
				
				String mediaId = SocialNetworkSource.Flickr + "#"+photo.getId(); 
				
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
				mediaItem.setPageUrl(photo.getUrl());
				//Thumbnail
				mediaItem.setThumbnail(thumbnail);
				//Title
				mediaItem.setTitle(title);
				//Description
				mediaItem.setDescription(description);
				//Tags
				mediaItem.setTags(tags);
				//Popularity
				mediaItem.setComments(new Long(photo.getComments()));
				mediaItem.setViews(new Long(photo.getViews()));
				//Location
				mediaItem.setLocation(location);
				
				//Store mediaItems and their ids 
				mediaItems.add(mediaItem);
				mediaIds.add(mediaId);
				
			}
			
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
	}

	public FlickrItem(Photo photo, StreamUser streamUser) {
		this(photo);

		//User that posted the photo
		this.streamUser = streamUser;
		uid = streamUser.getId();

		for(MediaItem mItem : mediaItems) {
			mItem.setUserId(uid);
		}

	}
	
}
