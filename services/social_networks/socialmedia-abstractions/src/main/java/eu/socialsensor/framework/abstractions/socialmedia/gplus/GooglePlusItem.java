package eu.socialsensor.framework.abstractions.socialmedia.gplus;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.google.api.services.plus.model.Activity;
import com.google.api.services.plus.model.Activity.Actor;
import com.google.api.services.plus.model.Activity.PlusObject;
import com.google.api.services.plus.model.Activity.PlusObject.Attachments;
import com.google.api.services.plus.model.Activity.PlusObject.Attachments.Embed;
import com.google.api.services.plus.model.Activity.PlusObject.Attachments.FullImage;
import com.google.api.services.plus.model.Activity.PlusObject.Attachments.Image;
import com.google.api.services.plus.model.Activity.PlusObject.Attachments.Thumbnails;
import com.google.api.services.plus.model.Comment;

import eu.socialsensor.framework.common.domain.Item;
import eu.socialsensor.framework.common.domain.Location;
import eu.socialsensor.framework.common.domain.MediaItem;
import eu.socialsensor.framework.common.domain.SocialNetworkSource;
import eu.socialsensor.framework.common.domain.WebPage;

/**
 * Class that holds the information of a google plus activity
 * @author ailiakop
 * @email  ailiakop@iti.gr
 */
public class GooglePlusItem extends Item {
	
	private static final long serialVersionUID = 1077924633642822831L;

	public GooglePlusItem(String id, Operation operation) {
		super(SocialNetworkSource.GooglePlus.toString(), operation);
		setId(SocialNetworkSource.GooglePlus+"#"+id);
	}
	
	
	public GooglePlusItem(Activity activity) {
		
		super(SocialNetworkSource.GooglePlus.toString(), Operation.NEW);
		
		if(activity == null || activity.getId() == null) return;
		
		//Id
		id = SocialNetworkSource.GooglePlus + "#" + activity.getId();
		//SocialNetwork Name
		streamId = SocialNetworkSource.GooglePlus.toString();
		//Timestamp of the creation of the post
		publicationTime =  activity.getPublished().getValue();
		//Title of the post
		title = activity.getTitle();
		//User that made the post
        Actor actor = activity.getActor();
        if(actor != null) {
                streamUser = new GooglePlusStreamUser(actor);
                uid = streamUser.getId();
        }
		//Location
		if(activity.getGeocode() != null){
			
			String locationInfo = activity.getGeocode();
			String[] parts = locationInfo.split(" ");
			double latitude = Double.parseDouble(parts[0]);
			double longitude = Double.parseDouble(parts[1]);
			
			location = new Location(latitude, longitude,activity.getPlaceName());
		}
		
		PlusObject object = activity.getObject();
		if(object == null)
			return;
		
		description = object.getContent();
		if(description != null) {
			try {
				List<String> tagsList = new ArrayList<String>();
				Document doc = Jsoup.parse(description);
				Elements elements = doc.getElementsByClass("ot-hashtag");
				for(Element e : elements) {
					String tag = e.text();
					if(tag != null)
						tagsList.add(tag.replaceAll("#", ""));
				}
				tags = tagsList.toArray(new String[tagsList.size()]);
				
			}
			catch(Exception e) {
			}
		}
		
		
		//Popularity
		if(object.getPlusoners() != null)
			likes = object.getPlusoners().getTotalItems();
			
		if(activity.getObject().getResharers() != null)
			shares = object.getResharers().getTotalItems();
			
		if(activity.getObject().getReplies() != null)
			numOfComments = object.getReplies().getTotalItems();
		
		url = activity.getUrl();
		if(url == null) {
			url = object.getUrl();
		}
		
		//Media Items - WebPages in a post
		webPages = new ArrayList<WebPage>();
		String pageURL = activity.getUrl();
		
		List<Attachments> attachmentsList = object.getAttachments();
		if(attachmentsList == null)
			attachmentsList = new ArrayList<Attachments>();
		
		for(Attachments attachment : attachmentsList) {
			
			if(attachment != null) {
				String type = attachment.getObjectType();
				
				if(type == null)
					continue;
				
				if(type.equals("video")) {
		    		
					if(attachment.getId() == null)
						continue;
					
					Image image = attachment.getImage();
					Embed embed = attachment.getEmbed();
					
					if(embed != null){
				
			    		String videoUrl = embed.getUrl();
			    		
						URL mediaUrl = null;
			    		try {	
			    			mediaUrl = new URL(videoUrl);
			    		} catch (MalformedURLException e1) {
			    			return;
			    		}
			    		//url
			    		MediaItem mediaItem = new MediaItem(mediaUrl);
			    		
			    		String mediaId = SocialNetworkSource.GooglePlus + "#"+attachment.getId(); 
			    		
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
						mediaItem.setPageUrl(pageURL);
						//Thumbnail
						String thumbUrl = image.getUrl();
						mediaItem.setThumbnail(thumbUrl);
						//Title
						mediaItem.setTitle(attachment.getDisplayName());
						//Description
						mediaItem.setDescription(attachment.getDisplayName());
						//Tags
						mediaItem.setTags(tags);
						//Popularity
						mediaItem.setLikes(likes);
						mediaItem.setShares(shares);
			    		
			    		mediaItems.add(mediaItem);			
			    		mediaIds.add(mediaId);		
				    	
					}
		    	}	
		    	else if(type.equals("photo")) {		
		    		
		    		if(attachment.getId() == null)
						continue;
		    		
	    			FullImage image = attachment.getFullImage();
	    			String imageUrl = image.getUrl();
		    		Image thumbnail = attachment.getImage();
		    		
		    		Integer width = image.getWidth().intValue();
					Integer height = image.getHeight().intValue();
					
		    		if(thumbnail != null && (width > 250 && height > 250)){
		    			URL mediaUrl = null;
			    		try {
			    			mediaUrl = new URL(imageUrl);
			    		} catch (MalformedURLException e2) {
			    			return;
			    		}

		    			//url
		    			MediaItem mediaItem = new MediaItem(mediaUrl);
		    			
		    			String mediaId = SocialNetworkSource.GooglePlus + "#"+attachment.getId(); 
		    			
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
							mediaItem.setUserId(streamUser.getId());
							mediaItem.setUser(streamUser);
						}
						//PageUrl
						mediaItem.setPageUrl(pageURL);
						//Thumbnail
						String thumnailUrl = thumbnail.getUrl();
						mediaItem.setThumbnail(thumnailUrl);
						//Title
						mediaItem.setTitle(attachment.getDisplayName());
						//Description
						mediaItem.setDescription(attachment.getDisplayName());
						//Tags
						mediaItem.setTags(tags);
						//Popularity
						mediaItem.setLikes(likes);
						mediaItem.setShares(shares);
		    			//Size
						mediaItem.setSize(width,height);
		        		
		        		mediaItems.add(mediaItem);
		        		mediaIds.add(mediaId);		
		        		
		    		}
		    	}
		    	else if(type.equals("album")) {		
		    		
		    		for(Thumbnails image : attachment.getThumbnails()){
		    			
		    			com.google.api.services.plus.model.Activity.PlusObject.Attachments.Thumbnails.Image thumbnail = image.getImage();
		    			
		    			if(thumbnail != null && image.getImage().getWidth()>250 && image.getImage().getHeight()>250){
		    				URL mediaUrl = null;
			    			try {
			    				mediaUrl = new URL(image.getImage().getUrl());
			    			} catch (MalformedURLException e3) {
				    			return;
				    		}
			    			
			    			
		    				MediaItem mediaItem = new MediaItem(mediaUrl);
		    				
		    				String mediaId = SocialNetworkSource.GooglePlus + "#"+attachment.getId(); 
		    				
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
								mediaItem.setUserId(streamUser.getId());
								mediaItem.setUser(streamUser);
							}
							//PageUrl
							mediaItem.setPageUrl(pageURL);
							//Thumbnail
							String thumbnailUrl = thumbnail.getUrl();
							mediaItem.setThumbnail(thumbnailUrl);
							//Title
							mediaItem.setTitle(title);
							//Description
							mediaItem.setDescription(attachment.getDisplayName());
							//Tags
							mediaItem.setTags(tags);
							//Popularity
							mediaItem.setLikes(likes);
							mediaItem.setShares(shares);
			    			//Size
							Long width = image.getImage().getWidth();
			        		Long height = image.getImage().getHeight();
			        		if(width != null && height != null) {
			        			mediaItem.setSize(width.intValue(), height.intValue());
			        		}
			        		
			        		mediaItems.add(mediaItem);
			        		mediaIds.add(mediaId);		
		    			}
			    		
		    		}
		    	}
		    	else if(type.equals("article")) {		
		    		String link = attachment.getUrl();
					if (link != null) {
						WebPage webPage = new WebPage(link, id);
						webPage.setStreamId(streamId);
						webPages.add(webPage);
					}
		    	}
			}
		}

		List<URL> urls = new ArrayList<URL>();
		for(WebPage wp : webPages) {
			try {
				urls.add(new URL(wp.getUrl()));
			}
			catch(Exception e) {
				continue;
			}
		}
		links = urls.toArray(new URL[urls.size()]);
		
	}
	
	public GooglePlusItem(Activity activity, GooglePlusStreamUser user) {
		this(activity);
		
		//User that posted the post
		streamUser = user;
		if(user != null)
			uid = user.getId();
		
		
	}
	
	public GooglePlusItem(Comment comment, Activity activity, GooglePlusStreamUser user){
		super(SocialNetworkSource.GooglePlus.toString(), Operation.NEW);
		
		if (comment == null) return;
		
		//Id
		id = SocialNetworkSource.GooglePlus+"#"+comment.getId();
		//Reference to the original post
		reference = SocialNetworkSource.GooglePlus+"#"+activity.getId();
		//SocialNetwork Name
		streamId = SocialNetworkSource.GooglePlus.toString();
		//Timestamp of the creation of the post
		publicationTime = comment.getPublished().getValue();
		description = "Comment";
		//User that posted the post
		if(user != null) {
			streamUser = user;
			uid = streamUser.getId();
		}
		//Popularity of the post
		if(comment.getPlusoners() != null){
			likes = new Long(comment.getPlusoners().size());
		}
	}

}
