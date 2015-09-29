package eu.socialsensor.framework.abstractions.socialmedia.topsy;

//import com.maruti.otterapi.search.Post;

import eu.socialsensor.framework.common.domain.Item;
import eu.socialsensor.framework.common.domain.SocialNetworkSource;

/**
 * Class that holds the information of a topsy post
 * @author ailiakop
 * @email ailiakop@iti.gr
 */
public class TopsyItem extends Item {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -6163805464886524821L;

	public TopsyItem(String id, Operation operation) {
		super(SocialNetworkSource.Topsy.toString(), operation);
		setId(SocialNetworkSource.Topsy + "#" + id);
	}
	
	public TopsyItem() {
		super(SocialNetworkSource.Topsy.toString(), Operation.NEW);
		
		/*
		if(post == null)
			return;
		
		//Id
		id = SocialNetworkSource.Topsy + "#" + Math.abs(post.hashCode());
		//SocialNetwork Name
		streamId = SocialNetworkSource.Topsy.toString();
		//Timestamp of the creation of the post

		if(post.getFirstpost_date() != null)
		publicationTime = Long.parseLong(post.getFirstpost_date()) * 1000;
		//title of the post
		title = post.getTitle();
		//description of the post
		description = post.getContent();
		
		//Media Items - WebPages in a post
		String pageURL = post.getUrl();
		
		URL url = null;
		try {
			url = new URL(pageURL);
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}
		//url
		MediaItem mediaItem = new MediaItem(url);
	
		String mediaId = SocialNetworkSource.Topsy + "#"+pageURL; 
		
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
		mediaItem.setUser(streamUser);
		//PageUrl
		mediaItem.setPageUrl(pageURL);
		//Title
		mediaItem.setTitle(title);
		//Description
		mediaItem.setDescription(description);
	
		mediaIds.add(mediaId);
		mediaItems.add(mediaItem);
		
		*/
	}
}
