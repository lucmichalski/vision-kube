package eu.socialsensor.framework.abstractions.socialmedia.gplus;

import com.google.api.services.plus.model.Activity.Actor;
import com.google.api.services.plus.model.Person;

import eu.socialsensor.framework.common.domain.SocialNetworkSource;
import eu.socialsensor.framework.common.domain.StreamUser;

/**
 * Class that holds the information of a google plus user
 * @author ailiakop
 * @email  ailiakop@iti.gr
 */
public class GooglePlusStreamUser extends StreamUser {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -2715125421888395821L;

	public GooglePlusStreamUser(Actor actor) {
		super(SocialNetworkSource.GooglePlus.toString(), Operation.NEW);
		if (actor == null) return;
		
		//Id
		id = SocialNetworkSource.GooglePlus + "#"+actor.getId();
		
		//The id of the user in the network
		userid = actor.getId();
		
		//The name of the user
		name = actor.getDisplayName();
		
		//The username of the user
		username = actor.getDisplayName();
		
		//streamId
		streamId = SocialNetworkSource.GooglePlus.toString();
		
		//Profile picture of the user
		profileImage = actor.getImage().getUrl();
		
		//The link to the user's profile
		linkToProfile = actor.getUrl();
		
		verified = false;
		
	}

	public GooglePlusStreamUser(Person person) {
		super(SocialNetworkSource.GooglePlus.toString(), Operation.NEW);
		if (person == null) 
			return;
		
		//Id
		id = SocialNetworkSource.GooglePlus + "#"+person.getId();
		
		//The id of the user in the network
		userid = person.getId();
		
		//The name of the user
		name = person.getDisplayName();
		
		//The username of the user
		username = person.getDisplayName();
		
		//The brief description of this person.
		description = person.getTagline();
		
		//streamId
		streamId = SocialNetworkSource.GooglePlus.toString();
		
		//Profile picture of the user
		profileImage = person.getImage().getUrl();
		imageUrl = profileImage;
		
		//The link to the user's profile
		pageUrl =  person.getUrl();
		
		verified = person.getVerified();
		
		if(person.getCircledByCount() != null)
			followers = (long) person.getCircledByCount();
		
		location = person.getCurrentLocation();

	}
	
	
}
