package eu.socialsensor.framework.abstractions.socialmedia.facebook;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import com.restfb.types.CategorizedFacebookType;
import com.restfb.types.Comment;
import com.restfb.types.Comment.Attachment;
import com.restfb.types.Comment.Media;
import com.restfb.types.NamedFacebookType;
import com.restfb.types.Place;
import com.restfb.types.Post;
import com.restfb.types.Post.Comments;
import com.restfb.types.Post.Likes;
import com.restfb.types.User;

import eu.socialsensor.framework.common.domain.Item;
import eu.socialsensor.framework.common.domain.Location;
import eu.socialsensor.framework.common.domain.MediaItem;
import eu.socialsensor.framework.common.domain.SocialNetworkSource;
import eu.socialsensor.framework.common.domain.WebPage;

/**
 * Class that holds the information of a facebook post
 * @author ailiakop
 * @author ailiakop@iti.gr
 *
 */
public class FacebookItem extends Item {
	
	
	private static final long serialVersionUID = 2267260425325527385L;

	public FacebookItem(String id, Operation operation) {
		super(SocialNetworkSource.Facebook.toString(), operation);
		setId(SocialNetworkSource.Facebook+"#"+id);
	}
	
	public FacebookItem(Post post) {
		
		super(SocialNetworkSource.Facebook.toString(), Operation.NEW);
		
		if (post == null || post.getId() == null) return;
		
		//Id
		id = SocialNetworkSource.Facebook+"#"+post.getId();
		//SocialNetwork Name
		streamId = SocialNetworkSource.Facebook.toString();
		//Timestamp of the creation of the post
		publicationTime = post.getCreatedTime().getTime();
		//post's descritpion
		description = post.getDescription();
		//is this the original or a shared fb post
		original = true;
		
		//Message that post contains
  		String msg = post.getMessage();
  		if(msg != null) {
  			if(msg.length() > 100) {
  				title = msg.subSequence(0, 100) + "...";
  			}
  			else {
  				title = msg;
  			}
  		}
 		else {
 			if(post.getCaption() != null) {
 				title = post.getCaption();
 			}
 			else if(post.getName() != null) {
 				title = post.getName();
 			}
 			else if(description != null) {
 				if(description.length()>100)
 					title = description.subSequence(0, 100)+"...";
 				else 
 					title = description;
 			}
 			else {
 				title = "";
 			}	
 		}
		

  		if(msg != null)
  			text = msg;
  		else if(description != null) 
  		 	text = description;
  		else
  		 	text = title;
  		 
		//Location 
		Place place = post.getPlace();
		if(place != null) {
			String placeName = place.getName();
			com.restfb.types.Location loc = place.getLocation();
			if(loc != null) {
				Double latitude = loc.getLatitude();
				Double longitude = loc.getLongitude();
		
				location = new Location(latitude, longitude, placeName);
			}
		}
		//Popularity of the post
		if(post.getLikesCount() != null)
			likes = post.getLikesCount();
		else {
			Likes likesPosts = post.getLikes();
			if(likesPosts!=null) {
				if(likesPosts.getCount()==null) {
					List<NamedFacebookType> likeData = likesPosts.getData();
					if(likeData != null) {
						likes = (long) likeData.size();
					}
				}
				else {
					likes = likesPosts.getCount();
				}
			}
		}
		
		if(post.getSharesCount() != null)
			shares = post.getSharesCount();
		
		Comments cmnts = post.getComments();
		if(cmnts != null) {
			Long n = cmnts.getTotalCount();
			if(n != null) {
				numOfComments = n;
			}
			else {
				List<Comment> data = cmnts.getData();
				if(data != null)
					numOfComments = (long) data.size();
			}
		}
		
		//Media Items - WebPages in a post
		String type = post.getType();
		
		if(type.equals("photo")) {
			
			url = post.getLink();
			String picture = post.getPicture();
			
			try {
				if (picture != null) { 
					URL p_url = null;
					StringBuilder b = new StringBuilder(picture);
					int index = picture.lastIndexOf("_s.");
					if(index>0) {
						b.replace(index, index+3, "_n." );
						picture = picture.replaceAll("_s.", "_n.");
						p_url = new URL(picture);
					
						if(p_url != null){
							
							String mediaId = SocialNetworkSource.Facebook+"#"+post.getId();
							//url
							MediaItem mediaItem = new MediaItem(p_url);
							
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
							String pageUrl = post.getLink();
							mediaItem.setPageUrl(pageUrl);
							//Thumbnail
							String thumbnail = post.getPicture();
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
							
							Comments postComments = post.getComments();
							if(postComments != null) {
								List<Comment> commentsList = postComments.getData();
								if(commentsList != null) {
									Integer commentsCount = commentsList.size();
									mediaItem.setComments(commentsCount.longValue());
								}
							}
							
							
							//Store mediaItems and their ids 
							mediaItems.add(mediaItem);
							
							mediaIds.add(mediaId);
						}
					}
				}
			} catch (MalformedURLException e) { 
				e.printStackTrace();
			}
		}
		else if(type.equals("link")) {
			
			url = "https://www.facebook.com/" + post.getId();
			
			webPages = new ArrayList<WebPage>();
			String picture = post.getPicture(); ///!!!!
			if (picture != null) { 
				
				URL p_url = null;
				StringBuilder b = new StringBuilder(picture);
				int index = picture.lastIndexOf("_s.");
				if(index>0) {
					b.replace(index, index+3, "_n." );
					picture = picture.replaceAll("_s.", "_n.");
					try {
						p_url = new URL(picture);
					} catch (MalformedURLException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				
					if(p_url != null){
						
						String mediaId = SocialNetworkSource.Facebook+"#"+post.getId();
						//url
						MediaItem mediaItem = new MediaItem(p_url);
						
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
						mediaItem.setUserId(streamUser.getId());
						
						//PageUrl
						String pageUrl = post.getLink();
						mediaItem.setPageUrl(pageUrl);
						//Thumbnail
						String thumbnail = post.getPicture();
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
						//Store mediaItems and their ids 
						mediaItems.add(mediaItem);
						mediaIds.add(mediaId);
						
					}
				}
			}
			
			String link = post.getLink();
			if (link != null) {
				
				WebPage webPage = new WebPage(link, id);
				webPage.setStreamId(streamId);
				webPages.add(webPage);
			}
		}
		else if(type.equals("video")) {
			
			url = "https://www.facebook.com/" + post.getId();
			
			String vUrl = post.getSource();
			String picture = post.getPicture();
			if(picture!=null){
				
				URL videoUrl = null;
				try {
					videoUrl = new URL(vUrl);
					
					String mediaId = SocialNetworkSource.Facebook+"#"+post.getId();
					//url
					MediaItem mediaItem = new MediaItem(videoUrl);
					
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
					String pageUrl = post.getLink();
					mediaItem.setPageUrl(pageUrl);
					//Thumbnail
					String thumbnail = post.getPicture();
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
					
					//Store mediaItems and their ids 
					mediaItems.add(mediaItem);
					mediaIds.add(mediaId);
					
				} catch (MalformedURLException e) {
					
				}
			}
			
		}
		else {
			url = "https://www.facebook.com/" + post.getId();
		}
	
	}
    
	
	public FacebookItem(Post post, FacebookStreamUser user) {
		
		this(post);
		
		//User that posted the post
		streamUser = user;
		uid = streamUser.getId();
		
		for(MediaItem mi : this.getMediaItems()) {
			mi.setUserId(uid);
		}
		
	}
	
	public FacebookItem(Comment comment, Post post, User user) {
		super(SocialNetworkSource.Facebook.toString(), Operation.NEW);
		
		if (comment == null) return;
		
		//Id
		id =SocialNetworkSource.Facebook+"#"+comment.getId();
		//Reference to the original post
		reference = SocialNetworkSource.Facebook+"#"+post.getId();
		//SocialNetwork Name
		streamId = SocialNetworkSource.Facebook.toString();
		//Timestamp of the creation of the post
		publicationTime = comment.getCreatedTime().getTime();
		//Message that post contains
		String msg = comment.getMessage();
		if(msg != null) {
			if(msg.length()>100) {
				title = msg.subSequence(0, 100)+"...";
			}
			else{
				title = msg;
			}
			
			description = "Comment";
		}
		
		//All the text inside the comment
		text = msg; 
		
		url = "https://www.facebook.com/" + post.getId();
		
		//User that posted the comment
		if(user != null) {
			streamUser = new FacebookStreamUser(user);
			uid = streamUser.getId();
		}
		else {
			CategorizedFacebookType from = comment.getFrom();
			streamUser = new FacebookStreamUser(from);
			uid = streamUser.getId();
		}
		
		original = false;
		
		//Popularity of the post
		if(comment.getLikeCount() != null)
			likes = comment.getLikeCount();
		
		Attachment attachment = comment.getAttachment();
		if(attachment != null) {
			Media media = attachment.getMedia();

			String url = attachment.getUrl();
			try {
				URL mediaUrl = new URL(url);

				String mediaId = SocialNetworkSource.Facebook+"#"+media.getId();
				//url
				MediaItem mediaItem = new MediaItem(mediaUrl);

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
				String pageUrl = post.getLink();
				mediaItem.setPageUrl(pageUrl);
				//Thumbnail
				String thumbnail = post.getPicture();
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

				//Store mediaItems and their ids 
				mediaItems.add(mediaItem);
				mediaIds.add(mediaId);
			} catch (MalformedURLException e) {
				e.printStackTrace();
			}
		}
	
	}
}
