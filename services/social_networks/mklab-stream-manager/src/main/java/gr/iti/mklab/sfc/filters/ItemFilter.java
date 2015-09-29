package gr.iti.mklab.sfc.filters;

import gr.iti.mklab.framework.common.domain.Item;
import gr.iti.mklab.framework.common.domain.config.Configuration;

public abstract class ItemFilter {
	
	protected Configuration configuration;

	private int discarded = 0;
	private int accepted = 0;

	public ItemFilter(Configuration configuration) {
		this.configuration = configuration;
	}
	
	public abstract boolean accept(Item item);
	
	public abstract String name();
	
	public String status() {
		return discarded + " items discarded, " + accepted + " items accepted.";
	}
	
	public void incrementAccepted() {
		synchronized (this) {
	    	accepted++;
	    }
	}
	
	public void incrementDiscarded() {
		synchronized (this) {
			discarded++;
	    }
	}
	
}
