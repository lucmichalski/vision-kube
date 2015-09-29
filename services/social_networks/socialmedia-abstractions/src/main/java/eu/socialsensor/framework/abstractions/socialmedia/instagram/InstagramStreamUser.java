package eu.socialsensor.framework.abstractions.socialmedia.instagram;

import org.jinstagram.entity.common.User;
import org.jinstagram.entity.users.basicinfo.Counts;
import org.jinstagram.entity.users.basicinfo.UserInfoData;

import eu.socialsensor.framework.common.domain.SocialNetworkSource;
import eu.socialsensor.framework.common.domain.StreamUser;

/**
 * Class that holds the information of an instagram user
 * @author ailiakop
 * @email  ailiakop@iti.gr
 */
public class InstagramStreamUser extends StreamUser {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -186352302816765493L;

	public InstagramStreamUser(User user) {
		super(SocialNetworkSource.Instagram.toString(), Operation.NEW);
		if (user == null) return;
		
		//Id
		id = SocialNetworkSource.Instagram + "#" + user.getId();
		//The id of the user in the network
		Long userLId = user.getId();
		userid = userLId.toString();
		//The name of the user
		name = user.getFullName();
		//The username of the user
		username = user.getUserName();
		//streamId
		streamId = SocialNetworkSource.Instagram.toString();
		//The description of the user
		description = user.getBio();
		//Profile picture of the user
		profileImage = user.getProfilePictureUrl();
		//The link to the user's profile
		linkToProfile = user.getWebsiteUrl();
		//The link to the user's profile
		pageUrl = "http://instagram.com/" + username;
	}
	
	public InstagramStreamUser(UserInfoData user) {
		super(SocialNetworkSource.Instagram.toString(), Operation.NEW);
		if (user == null) return;

		//Id
		id = SocialNetworkSource.Instagram + "#" + user.getId();
		//The id of the user in the network
		Long userLId = user.getId();
		userid = userLId.toString();
		//The name of the user
		name = user.getFullName();
		//The username of the user
		username = user.getUsername();
		//streamId
		streamId = SocialNetworkSource.Instagram.toString();
		//The description of the user
		description = user.getBio();
		//Profile picture of the user
		profileImage = user.getProfile_picture();
		//The link to the user's profile
		pageUrl = "http://instagram.com/" + username;
		
		Counts counts = user.getCounts();
		if(counts != null) {
			items = counts.getMedia();
			friends = (long) counts.getFollows();
			followers = (long) counts.getFollwed_by();
		}
		
	}
}
