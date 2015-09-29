package eu.socialsensor.framework.retrievers.socialmedia;


import java.util.ArrayList;
import java.util.List;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.jackson2.JacksonFactory;

import eu.socialsensor.framework.abstractions.socialmedia.vimeo.VimeoMediaItem;
import eu.socialsensor.framework.abstractions.socialmedia.vimeo.VimeoMediaItem.VimeoVideo;
import eu.socialsensor.framework.common.domain.Feed;
import eu.socialsensor.framework.common.domain.Item;
import eu.socialsensor.framework.common.domain.MediaItem;
import eu.socialsensor.framework.common.domain.StreamUser;
import eu.socialsensor.framework.common.domain.feeds.KeywordsFeed;
import eu.socialsensor.framework.common.domain.feeds.ListFeed;
import eu.socialsensor.framework.common.domain.feeds.LocationFeed;
import eu.socialsensor.framework.common.domain.feeds.SourceFeed;

/**
 * The retriever that implements the Vimeo simplified retriever 
 * @author manosetro
 * @email  manosetro@iti.gr
 */
public class VimeoRetriever implements SocialMediaRetriever {

	static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
	static final JsonFactory JSON_FACTORY = new JacksonFactory();
	
	private HttpRequestFactory requestFactory;
	private String requestPrefix = "http://vimeo.com/api/v2/video/";
	
	public VimeoRetriever() {
		requestFactory = HTTP_TRANSPORT.createRequestFactory(
				new HttpRequestInitializer() {
					@Override
					public void initialize(HttpRequest request) {
						request.setParser(new JsonObjectParser(JSON_FACTORY));
					}
				});
	}
	
	public MediaItem getMediaItem(String id) {
	
		GenericUrl url = new GenericUrl(requestPrefix + id + ".json");
		
		HttpRequest request;
		try {
			request = requestFactory.buildGetRequest(url);
			HttpResponse response = request.execute();
			VimeoVideo[] videos = response.parseAs(VimeoVideo[].class);
			if(videos != null && videos.length>0) {
				MediaItem mediaItem = new VimeoMediaItem(videos[0]);
				return mediaItem;
			}
		} catch (Exception e) {
			//e.printStackTrace();
		}
		return null;
		 
		
	}

	@Override
	public List<Item> retrieve(Feed feed) {
		return new ArrayList<Item>();
	}

	@Override
	public void stop() {
		// TODO Auto-generated method stub	
	}

	@Override
	public List<Item> retrieveKeywordsFeeds(KeywordsFeed feed) throws Exception {
		return new ArrayList<Item>();
	}

	@Override
	public List<Item> retrieveUserFeeds(SourceFeed feed) throws Exception {
		return new ArrayList<Item>();
	}

	@Override
	public List<Item> retrieveLocationFeeds(LocationFeed feed) throws Exception {
		return new ArrayList<Item>();
	}

	@Override
	public StreamUser getStreamUser(String uid) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<Item> retrieveListsFeeds(ListFeed feed) {
		return new ArrayList<Item>();
	}

}
