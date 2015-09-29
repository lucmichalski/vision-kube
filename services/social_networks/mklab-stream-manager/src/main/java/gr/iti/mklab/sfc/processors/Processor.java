package gr.iti.mklab.sfc.processors;

import gr.iti.mklab.framework.common.domain.Item;
import gr.iti.mklab.framework.common.domain.config.Configuration;


public abstract class Processor {

	protected Configuration configuration;

	public Processor(Configuration configuration) {
		this.configuration = configuration;
	}
	
	public abstract  void process(Item item);
	
}
