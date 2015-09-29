package eu.socialsensor.framework.retrievers;

import org.apache.log4j.Logger;

/**
 * @author manosetro
 * @email manosetro@iti.gr
 * This class implements a simple rate limits policy
 */
public class RateLimitsMonitor {
	
	private int requests = 0;
	private long lastCheckpoint = -1;
	private int maxRequests;
	private long minInterval;
	
	private Logger logger = Logger.getLogger(RateLimitsMonitor.class);
	
	public RateLimitsMonitor(int maxRequests, long minInterval) {
		this.maxRequests = maxRequests;
		this.minInterval = minInterval;
	}
	
	/* 
	 * In a more sophisticated design this operation will be part of the 
	 * monitor module.
	 */
	synchronized public void check() {
		if(lastCheckpoint < 0) {
			lastCheckpoint = System.currentTimeMillis();
		}
		
		requests++;
		long interval = System.currentTimeMillis() - lastCheckpoint;
		if(requests >= maxRequests) {
			try {
				long sleepTime = minInterval - interval;
				if(sleepTime > 0) {
					logger.info("Request limit reached. Wait for " + sleepTime/1000 + " seconds");
					Thread.sleep(sleepTime);
				}
			} catch (InterruptedException e) {
				logger.error(e.getMessage());
				return;
			}
			finally {
				requests = 0;
				lastCheckpoint = System.currentTimeMillis();
			}
		}
		else if(interval > minInterval) {
			requests = 0;
			lastCheckpoint = System.currentTimeMillis();
		}
	}
}
