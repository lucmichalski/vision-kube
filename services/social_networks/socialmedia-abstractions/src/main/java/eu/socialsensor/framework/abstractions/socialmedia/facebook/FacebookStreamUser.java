package eu.socialsensor.framework.abstractions.socialmedia.facebook;

import com.restfb.types.CategorizedFacebookType;
import com.restfb.types.Location;
import com.restfb.types.Page;
import com.restfb.types.User;

import eu.socialsensor.framework.common.domain.SocialNetworkSource;
import eu.socialsensor.framework.common.domain.StreamUser;

/**
 * Class that holds the information of a facebook user or a facebook page
 * @author ailiakop
 * @email  ailiakop@iti.gr
 */
public class FacebookStreamUser extends StreamUser {

	
	/**
	 * 
	 */
	private static final long serialVersionUID = 3234574808873979755L;

	/**
	 * Maps the information of a facebook user
	 * @param user
	 */
	public FacebookStreamUser(User user) {
		super(SocialNetworkSource.Facebook.toString(), Operation.NEW);
		
		if (user == null) return;
		
		//Id
		id = SocialNetworkSource.Facebook+"#"+user.getId();
		
		//The id of the user in the network
		userid = user.getId();
		
		//The name of the user
		if(user.getFirstName() == null && user.getLastName() == null)
			name = user.getName();
		else if(user.getMiddleName() != null) 
			name = user.getFirstName()+" "+user.getMiddleName()+" "+user.getLastName();
		else
			name = user.getFirstName()+" "+user.getLastName();
		
		//The username of the user
		username = user.getUsername();
		
		//streamId
		streamId =  SocialNetworkSource.Facebook.toString();
		
		//The description of the user
		description = user.getAbout();
		
		//The link to the user's profile
		pageUrl = user.getLink(); 
		if(pageUrl == null) {
			pageUrl = username==null ? ("https://www.facebook.com/profile.php?id="+userid) : ("http://www.facebook.com/"+username);
		}
		
		profileImage = "https://graph.facebook.com/" + userid + "/picture";
		imageUrl = profileImage;
		
		//Last time user's profile was updated
		if(user.getUpdatedTime() != null)
			lastUpdated = user.getUpdatedTime().getTime();
		
		//Location
		if(user.getLocation()!= null)
			location = user.getLocation().getName();
		
		//Is the user a verified user
		if(user.getVerified() != null)
			verified = user.getVerified();
		
		
	}

	/**
	 * Maps the information of a facebook page
	 * @param page
	 */
	public FacebookStreamUser(Page page) {
		super(SocialNetworkSource.Facebook.toString(), Operation.NEW);
		if (page == null) return;
		
		//Id
		id = SocialNetworkSource.Facebook+"#"+page.getId();
		
		//The id of the page in the network
		userid = page.getId();
		
		//The name of the page
		name = page.getName();
		
		//The username of the page
		username = page.getUsername();
		
		//The name of the Social Network
		streamId = SocialNetworkSource.Facebook.toString();
		
		//The description of the page
		description = page.getAbout();
		
		//Link to the page
		linkToProfile = page.getLink();
		
		pageUrl = page.getLink(); 
		if(pageUrl == null) {
			pageUrl = username==null ? ("https://www.facebook.com/profile.php?id="+userid) : ("http://www.facebook.com/"+username);
		}
		
		//Avatar of the page
		profileImage = page.getPicture();
		if(profileImage == null) {
			profileImage = "https://graph.facebook.com/" + userid + "/picture";
		}
		imageUrl = profileImage;
		
		//Number of people talking about the page
		followers = page.getTalkingAboutCount();
		
		//Location 
		Location loc = page.getLocation();
		if(loc != null) {
			location = loc.getCity();
		}
		
	}
	
	/**
	 * Maps the information of a categorized facebook user 
	 * @param user
	 */
	public FacebookStreamUser(CategorizedFacebookType user) {
		super(SocialNetworkSource.Facebook.toString(), Operation.NEW);

		if (user == null) return;

		//Id
		id = SocialNetworkSource.Facebook+"#"+user.getId();
		//The id of the page in the network
		userid = user.getId();
		//The name of the page
		name = user.getName();
		//Link to the page
		pageUrl = "https://www.facebook.com/profile.php?id="+userid;
		//Avatar of the page
		profileImage = "https://graph.facebook.com/" + userid + "/picture";
		imageUrl = profileImage;
		
	}
	
}
