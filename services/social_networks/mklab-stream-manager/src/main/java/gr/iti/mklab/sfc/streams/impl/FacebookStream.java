package gr.iti.mklab.sfc.streams.impl;

import java.util.Date;

import org.apache.log4j.Logger;
import org.mongodb.morphia.dao.BasicDAO;
import org.mongodb.morphia.query.Query;

import gr.iti.mklab.framework.Credentials;
import gr.iti.mklab.framework.client.mongo.DAOFactory;
import gr.iti.mklab.framework.common.domain.Item;
import gr.iti.mklab.framework.common.domain.Source;
import gr.iti.mklab.framework.common.domain.config.Configuration;
import gr.iti.mklab.framework.common.domain.feeds.Feed;
import gr.iti.mklab.framework.retrievers.Response;
import gr.iti.mklab.framework.retrievers.Retriever;
import gr.iti.mklab.framework.retrievers.impl.FacebookRetriever;
import gr.iti.mklab.sfc.streams.Stream;
import gr.iti.mklab.sfc.streams.StreamException;
import gr.iti.mklab.sfc.streams.monitors.RateLimitsMonitor;

/**
 * Class responsible for setting up the connection to Facebook API
 * for retrieving relevant Facebook content.
 * 
 * @author manosetro
 * @email  manosetro@iti.gr
 */
public class FacebookStream extends Stream {
	
	public static Source SOURCE = Source.Facebook;

	private Logger  logger = Logger.getLogger(FacebookStream.class);	
	
	@Override
	public synchronized void open(Configuration config) throws StreamException {
		logger.info("#Facebook : Open stream");
		
		if (config == null) {
			logger.error("#Facebook : Config file is null.");
			return;
		}
		
		
		String accessToken = config.getParameter(ACCESS_TOKEN);
		String key = config.getParameter(KEY);
		String secret = config.getParameter(SECRET);
		
		if (accessToken == null && key == null && secret == null) {
			logger.error("#Facebook : Stream requires authentication.");
			throw new StreamException("Stream requires authentication.");
		}
			
		if(accessToken == null) {
			accessToken = key + "|" + secret;
		}
		
		Credentials credentials = new Credentials();
		credentials.setAccessToken(accessToken);
		
		maxRequests = Integer.parseInt(config.getParameter(MAX_REQUESTS));
		timeWindow = Long.parseLong(config.getParameter(TIME_WINDOW));
		
		rateLimitsMonitor = new RateLimitsMonitor(maxRequests, timeWindow);
		retriever = new FacebookRetriever(credentials);	
	}

	@Override
	public String getName() {
		return SOURCE.name();
	}
	
	public static void main(String...args) throws Exception {
		Credentials credentials = new Credentials();
		credentials.setAccessToken("260504214011769|964663756aa84795ad1b37c2c3d86bf9");
		Retriever retriever = new FacebookRetriever(credentials );
		
		DAOFactory factory = new DAOFactory();
		BasicDAO<Feed, String> dao = factory.getDAO("160.40.50.207", "test", Feed.class);
		Query<Feed> q = dao.createQuery().filter("source", "Facebook");
		Feed feed = dao.findOne(q);
		
		System.out.println(feed.toString());
		Response response = retriever.retrieve(feed, 1);
		System.out.println(response.getNumberOfItems());
		for(Item item : response.getItems()) {
			if(item.getReference() == null) {
				System.out.println("=================================================");
				System.out.println(item.getTitle());
				System.out.println(item.getPublicationTime());
				System.out.println("Shares: " + item.getShares());
				System.out.println("Likes: " + item.getLikes());
			}
			else {
				System.out.println("\t *" + item.getTitle().replaceAll("\n", " ") + " (" + item.getLikes() + " )");
				System.out.println("\t  " + new Date(item.getPublicationTime()) + " " + item.getStreamUser().getName());
			}
			
		}
	}
}
