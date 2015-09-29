package eu.socialsensor.framework.abstractions.socialmedia.twitpic;

import eu.socialsensor.framework.abstractions.socialmedia.twitpic.TwitPicMediaItem.TwitPicUser;
import eu.socialsensor.framework.common.domain.StreamUser;

public class TwitPicStreamUser extends StreamUser {

	/**
	 * 
	 */
	private static final long serialVersionUID = 2219372938038811930L;

	public TwitPicStreamUser(TwitPicUser user) {
		super("Twitpic", Operation.NEW);

		id = "Twitpic#"+user.id;
		description = user.bio;
		username = user.username;
		name = user.name;

		items = user.photo_count;
		location = user.location;

		createdAt = user.timestamp;
		profileImage = user.avatar_url;

	}
}