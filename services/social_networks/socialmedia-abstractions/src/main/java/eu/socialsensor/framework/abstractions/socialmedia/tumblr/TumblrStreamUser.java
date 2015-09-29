package eu.socialsensor.framework.abstractions.socialmedia.tumblr;

import com.tumblr.jumblr.types.Blog;
import com.tumblr.jumblr.types.User;

import eu.socialsensor.framework.common.domain.SocialNetworkSource;
import eu.socialsensor.framework.common.domain.StreamUser;

/**
 * Class that holds the information of a tumblr user
 * @author ailiakop
 * @email  ailiakop@iti.gr
 */
public class TumblrStreamUser extends StreamUser {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -4580766580534059162L;

	public TumblrStreamUser(Blog blog) {
		super(SocialNetworkSource.Tumblr.toString(), Operation.NEW);
		
		//Id
		id = SocialNetworkSource.Tumblr + "#"+blog.getName();
		//The id of the user in the network
		userid = blog.getName();
		//The name of the blog
		name = blog.getName();
		//streamId
		streamId = SocialNetworkSource.Tumblr.toString();
		//The description of the blog
		blog.getDescription();
		//Profile picture of the blog
		//profileImage = blog.avatar();
		//Likes of the blog
		//likes = blog.getLikeCount();
		//Posts of the blog
		items = blog.getPostCount();
		
	}
	
	public TumblrStreamUser(User user) {
		super(SocialNetworkSource.Tumblr.toString(), Operation.NEW);
		
		//Id
		id = SocialNetworkSource.Tumblr + "#"+user.getName();
		
		//The id of the user in the network
		userid = user.getName();
		
		//The name of the blog
		name = user.getName();
		
		//streamId
		streamId = SocialNetworkSource.Tumblr.toString();
		//Profile picture of the blog
		//profileImage = blog.avatar();
		//Likes of the blog
		//likes = blog.getLikeCount();
		
	}
}
