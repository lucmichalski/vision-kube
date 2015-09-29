package gr.iti.mklab.sfc.streams.impl;

import org.apache.log4j.Logger;

import gr.iti.mklab.framework.Credentials;
import gr.iti.mklab.framework.common.domain.Source;
import gr.iti.mklab.framework.common.domain.config.Configuration;
import gr.iti.mklab.framework.retrievers.impl.GooglePlusRetriever;
import gr.iti.mklab.sfc.streams.Stream;
import gr.iti.mklab.sfc.streams.StreamException;
import gr.iti.mklab.sfc.streams.monitors.RateLimitsMonitor;

/**
 * Class responsible for setting up the connection to Google API
 * for retrieving relevant Google+ content.
 * 
 * @author manosetro
 * @email  manosetro@iti.gr
 */
public class GooglePlusStream extends Stream {
	
	public static final Source SOURCE = Source.GooglePlus;
	
	private Logger logger = Logger.getLogger(GooglePlusStream.class);
	
	private String key;

	@Override
	public void open(Configuration config) throws StreamException {
		logger.info("#GooglePlus : Open stream");
		
		if (config == null) {
			logger.error("#GooglePlus : Config file is null.");
			return;
		}
		
		key = config.getParameter(KEY);
		
		if (key == null) {
			logger.error("#GooglePlus : Stream requires authentication.");
			throw new StreamException("Stream requires authentication.");
		}
		
		Credentials credentials = new Credentials();
		credentials.setKey(key);
		
		maxRequests = Integer.parseInt(config.getParameter(MAX_REQUESTS));
		timeWindow = Long.parseLong(config.getParameter(TIME_WINDOW));
		
		rateLimitsMonitor = new RateLimitsMonitor(maxRequests, timeWindow);
		retriever = new GooglePlusRetriever(credentials);
		
	}
	
	@Override
	public String getName() {
		return SOURCE.name();
	}
}
