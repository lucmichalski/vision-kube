package gr.iti.mklab.sfc.filters;

import gr.iti.mklab.framework.common.domain.Item;
import gr.iti.mklab.framework.common.domain.config.Configuration;

import org.apache.log4j.Logger;

/**
 * 
 * @author Manos Schinas - manosetro@iti.gr
 *
 * This filter discard items that have many embedded URLs as possible spam.
 * 	
 */
public class UrlItemFilter extends ItemFilter {

	private int maxUrls = 4;

	public UrlItemFilter(Configuration configuration) {
		super(configuration);
		String lenStr =configuration.getParameter("maxUrls", "4");
		this.maxUrls  = Integer.parseInt(lenStr);
		
		Logger.getLogger(UrlItemFilter.class).info("Initialized. Max Number of URLs: " + maxUrls);
	}
	
	@Override
	public boolean accept(Item item) {
		if(item == null) {
			incrementDiscarded();
			return false;
		}
		
		String[] urls = item.getLinks();
		if(urls == null) {
			incrementAccepted();
			return true;
		}
		
		if(urls.length >= maxUrls) {
			incrementDiscarded();
			return false;
		}
		
		incrementAccepted();
		return true;
	}

	@Override
	public String name() {
		return "UrlItemFilter";
	}
	
}
