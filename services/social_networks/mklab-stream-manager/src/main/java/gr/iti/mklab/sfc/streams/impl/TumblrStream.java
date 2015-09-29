package gr.iti.mklab.sfc.streams.impl;

import org.apache.log4j.Logger;

import gr.iti.mklab.framework.Credentials;
import gr.iti.mklab.framework.common.domain.Source;
import gr.iti.mklab.framework.common.domain.config.Configuration;
import gr.iti.mklab.framework.retrievers.impl.TumblrRetriever;
import gr.iti.mklab.sfc.streams.Stream;
import gr.iti.mklab.sfc.streams.StreamException;
import gr.iti.mklab.sfc.streams.monitors.RateLimitsMonitor;

/**
 * Class responsible for setting up the connection to Tumblr API
 * for retrieving relevant Tumblr content.
 * @author ailiakop
 * @email  ailiakop@iti.gr
 */
public class TumblrStream extends Stream {
	
	public static final Source SOURCE = Source.Tumblr;
	
	private String consumerKey;
	private String consumerSecret;
	
	private Logger logger = Logger.getLogger(TumblrStream.class);

	
	@Override
	public void open(Configuration config) throws StreamException {
		logger.info("#Tumblr : Open stream");
		
		if (config == null) {
			logger.error("#Tumblr : Config file is null.");
			return;
		}
		
		consumerKey = config.getParameter(KEY);
		consumerSecret = config.getParameter(SECRET);
		
		if (consumerKey == null || consumerSecret==null) {
			logger.error("#Tumblr : Stream requires authentication.");
			throw new StreamException("Stream requires authentication.");
		}
		
		Credentials credentials = new Credentials();
		credentials.setKey(consumerKey);
		credentials.setSecret(consumerSecret);
		
		maxRequests = Integer.parseInt(config.getParameter(MAX_REQUESTS));
		timeWindow = Long.parseLong(config.getParameter(TIME_WINDOW));
		
		rateLimitsMonitor = new RateLimitsMonitor(maxRequests, timeWindow);
		retriever = new TumblrRetriever(credentials);
		
	}

	@Override
	public String getName() {
		return SOURCE.name();
	}
	
}
