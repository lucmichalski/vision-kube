package gr.iti.mklab.sfc.streams.impl;

import gr.iti.mklab.framework.common.domain.Source;
import gr.iti.mklab.framework.common.domain.config.Configuration;
import gr.iti.mklab.framework.retrievers.impl.RssRetriever;
import gr.iti.mklab.sfc.streams.Stream;

/**
 * Class responsible for setting up the connection for retrieving RSS feeds.
 * 
 * @author Manos Schinas
 * 
 * @email  manosetro@iti.gr
 */
public class RssStream extends Stream {
	
	public static Source SOURCE = Source.RSS;
	
	@Override
	public void open(Configuration config) {
		
		this.maxRequests = Integer.MAX_VALUE;
		this.timeWindow = Integer.parseInt(config.getParameter(TIME_WINDOW));
		
		retriever = new RssRetriever();
	}

	@Override
	public String getName() {
		return SOURCE.name();
	}
	
}
