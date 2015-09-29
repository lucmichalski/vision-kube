package gr.iti.mklab.sfc.streams.impl;

import org.apache.log4j.Logger;

import gr.iti.mklab.framework.Credentials;
import gr.iti.mklab.framework.common.domain.Source;
import gr.iti.mklab.framework.common.domain.config.Configuration;
import gr.iti.mklab.framework.retrievers.impl.FlickrRetriever;
import gr.iti.mklab.sfc.streams.Stream;
import gr.iti.mklab.sfc.streams.StreamException;
import gr.iti.mklab.sfc.streams.monitors.RateLimitsMonitor;

/**
 * Class responsible for setting up the connection to Flickr API
 * for retrieving relevant Flickr content.
 * 
 * @author manosetro
 * @email  manosetro@iti.gr
 */
public class FlickrStream extends Stream {

	private Logger logger = Logger.getLogger(FlickrStream.class);
	
	public static final Source SOURCE = Source.Flickr;
	
	private String key;
	private String secret;

	
	@Override
	public void open(Configuration config) throws StreamException {
		logger.info("#Flickr : Open stream");
		
		if (config == null) {
			logger.error("#Flickr : Config file is null.");
			return;
		}
		
		key = config.getParameter(KEY);
		secret = config.getParameter(SECRET);
		
		if (key == null || secret==null) {
			logger.error("#Flickr : Stream requires authentication.");
			throw new StreamException("Stream requires authentication.");
		}
		
		Credentials credentials = new Credentials();
		credentials.setKey(key);
		credentials.setSecret(secret);
		
		maxRequests = Integer.parseInt(config.getParameter(MAX_REQUESTS));
		timeWindow = Long.parseLong(config.getParameter(TIME_WINDOW));
		
		rateLimitsMonitor = new RateLimitsMonitor(maxRequests, timeWindow);
		retriever = new FlickrRetriever(credentials);
	}
	
	@Override
	public String getName() {
		return SOURCE.name();
	}
	
}
