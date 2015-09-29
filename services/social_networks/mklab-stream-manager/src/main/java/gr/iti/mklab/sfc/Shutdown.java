package gr.iti.mklab.sfc;

import gr.iti.mklab.sfc.streams.StreamException;

import org.apache.log4j.Logger;

/**
 * Class in case system is shutdown. 
 * Responsible to close all services that are running at the time being
 * 
 * @author Manos Schinas
 *
 */
public class Shutdown extends Thread {
	
	private StreamsManager _manager = null;
	private Logger _logger = Logger.getLogger(Shutdown.class);
	
	public Shutdown(StreamsManager manager) {
		this._manager = manager;
	}

	public void run() {
		_logger.info("Shutting down stream manager...");
		if (_manager != null) {
			try {
				_manager.close();
			} catch (StreamException e) {
				_logger.error(e);
				e.printStackTrace();
			}
		}
		_logger.info("Done...");
	}
}