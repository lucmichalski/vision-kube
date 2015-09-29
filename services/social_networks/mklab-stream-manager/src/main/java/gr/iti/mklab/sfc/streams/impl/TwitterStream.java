package gr.iti.mklab.sfc.streams.impl;

import org.apache.log4j.Logger;

import gr.iti.mklab.framework.Credentials;
import gr.iti.mklab.framework.common.domain.Source;
import gr.iti.mklab.framework.common.domain.config.Configuration;
import gr.iti.mklab.framework.retrievers.impl.TwitterRetriever;
import gr.iti.mklab.sfc.streams.Stream;
import gr.iti.mklab.sfc.streams.StreamException;
import gr.iti.mklab.sfc.streams.monitors.RateLimitsMonitor;

/**
 * Class responsible for setting up the connection to Twitter API for retrieving relevant Twitter content. 
 * Handles both the connection to Twitter REST API and Twitter Subscriber. 
 * 
 * @author manosetro
 * @email  manosetro@iti.gr
 */
public class TwitterStream extends Stream {
	
	public static Source SOURCE = Source.Twitter;
	
	private Logger  logger = Logger.getLogger(TwitterStream.class);

	@Override
	public synchronized void open(Configuration config) throws StreamException {

		logger.info("#Twitter : Open stream");
		
		if (config == null) {
			logger.error("#Twitter : Config file is null.");
			return;
		}
		
		String oAuthConsumerKey 		= 	config.getParameter(KEY);
		String oAuthConsumerSecret 		= 	config.getParameter(SECRET);
		String oAuthAccessToken 		= 	config.getParameter(ACCESS_TOKEN);
		String oAuthAccessTokenSecret 	= 	config.getParameter(ACCESS_TOKEN_SECRET);
		
		if (oAuthConsumerKey == null || oAuthConsumerSecret == null ||
				oAuthAccessToken == null || oAuthAccessTokenSecret == null) {
			logger.error("#Twitter : Stream requires authentication");
			throw new StreamException("Stream requires authentication");
		}
		
		logger.info("Initialize Twitter Retriever for REST api");
	
		Credentials credentials = new Credentials();
		credentials.setKey(oAuthConsumerKey);
		credentials.setSecret(oAuthConsumerSecret);
		credentials.setAccessToken(oAuthAccessToken);
		credentials.setAccessTokenSecret(oAuthAccessTokenSecret);
		
		maxRequests = Integer.parseInt(config.getParameter(MAX_REQUESTS));
		timeWindow = Long.parseLong(config.getParameter(TIME_WINDOW));
		
		rateLimitsMonitor = new RateLimitsMonitor(maxRequests, timeWindow);
		retriever = new TwitterRetriever(credentials);	
	}
	
	@Override
	public String getName() {
		return SOURCE.name();
	}
}

