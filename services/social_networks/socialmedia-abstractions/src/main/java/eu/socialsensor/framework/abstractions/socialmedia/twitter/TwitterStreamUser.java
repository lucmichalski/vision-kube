package eu.socialsensor.framework.abstractions.socialmedia.twitter;

import java.util.Date;

import twitter4j.User;
import eu.socialsensor.framework.common.domain.SocialNetworkSource;
import eu.socialsensor.framework.common.domain.StreamUser;

/**
 * Class that holds the information of a twitter user
 * @author manosetro
 * @email  manosetro@iti.gr
 */
public class TwitterStreamUser extends StreamUser {

	/**
	 * 
	 */
	private static final long serialVersionUID = -6485573747686458937L;

	public TwitterStreamUser(User user) {
		super(SocialNetworkSource.Twitter.toString(), Operation.NEW);
		if (user == null) return;
		
		//Id
		id = SocialNetworkSource.Twitter + "#" + user.getId();
		//The id of the user in the network
		userid = Long.toString(user.getId());
		//The name of the user
		name = user.getName();
		//The username of the user
		username = user.getScreenName();
		//streamId
		streamId = SocialNetworkSource.Twitter.toString();
		//The description of the user
		description = user.getDescription();
		//Profile picture of the user
		profileImage = user.getProfileImageURL();
		//Image url of the user
		imageUrl = user.getOriginalProfileImageURL();
		//Page URL of the user
		pageUrl = "https://twitter.com/" + user.getScreenName();
		url = user.getURL();
		
		verified = user.isVerified();
		listedCount = (long) user.getListedCount();
		
		//Statuses of the user
		items = user.getStatusesCount();
		//Creation date of user's profile
		Date date = user.getCreatedAt();
		if(date != null) {
			createdAt = date.toString();
		}
		//Location
		location = user.getLocation();
		//Followers of the user
		followers = (long) user.getFollowersCount();
		//Friends of the user
		friends =  (long) user.getFriendsCount();
		
		favoritiesCount = user.getFavouritesCount();
		
		timezone = user.getTimeZone();
	}
	
}
