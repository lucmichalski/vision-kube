package gr.iti.mklab.sfc.management;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

import org.apache.log4j.Logger;

import gr.iti.mklab.framework.common.domain.config.Configuration;
import gr.iti.mklab.framework.common.domain.Item;
import gr.iti.mklab.sfc.filters.ItemFilter;
import gr.iti.mklab.sfc.processors.Processor;
import gr.iti.mklab.sfc.storages.Storage;
import gr.iti.mklab.sfc.streams.StreamException;
import gr.iti.mklab.sfc.streams.StreamsManagerConfiguration;

/**
 * @brief  Thread-safe class for managing the storage of items to databases 
 * The storage may be accomplished using multiple consumer-threads.
 * 
 * @author Manos Schinas 
 * @email  manosetro@iti.gr
 *
 */
public class StorageHandler {
	
	public final Logger logger = Logger.getLogger(StorageHandler.class);
	
	// Internal queue used as a buffer of incoming items 
	private BlockingQueue<Item> queue = new LinkedBlockingDeque<Item>();
	
	private int numberOfConsumers = 16;
	private List<Consumer> consumers = new ArrayList<Consumer>(numberOfConsumers);
	
	private List<Storage> storages = new ArrayList<Storage>();
	
	private List<ItemFilter> filters = new ArrayList<ItemFilter>();
	private List<Processor> processors = new ArrayList<Processor>();
	
	private Map<String, Boolean> workingStatus = new HashMap<String, Boolean>();
	
	enum StorageHandlerState {
		OPEN, CLOSE
	}
	
	private StorageHandlerState state = StorageHandlerState.CLOSE;
	
	public StorageHandler(StreamsManagerConfiguration config) {
		try {	
			state = StorageHandlerState.OPEN;
			
			createFilters(config);
			logger.info(filters.size() + " filters initialized!");
			
			createProcessors(config);
			logger.info(processors.size() + " processors initialized!");
			
			initializeStorageHandler(config);	
			
		} catch (StreamException e) {
			logger.error("Error during storage handler initialization: " + e.getMessage());
		}
		
		//this.statusThread = new StorageStatusThread(this);	
		//this.statusThread.start();
		
	}
	
	public StorageHandlerState getState() {
		return state;
	}
	
	/**
	 * Starts the consumer threads responsible for storing
	 * items to the database.
	 */
	public void start() {
		for(int i=0; i<numberOfConsumers; i++) {
			Consumer consumer = new Consumer(queue, storages, filters, processors);
			consumers.add(consumer);
		}
		
		for(Consumer consumer : consumers) {
			consumer.start();
		}
	}

	public void handle(Item item) {
		try {
			queue.add(item);
		}
		catch(Exception e) {
			logger.error(e);
		}
	}

	public void handle(Item[] items) {
		for (Item item : items) {
			handle(item);
		}
	}
	
	
	public void delete(String id) {
		for(Storage storage : storages) {
			try {
				storage.delete(id);
			} catch (IOException e) {
				logger.error(e);
			}	
		}
	}
	
	/**
	 * Initializes the databases that are going to be used in the service
	 * @param config
	 * @return
	 * @throws StreamException
	 */
	private void initializeStorageHandler(StreamsManagerConfiguration config) throws StreamException {
		for (String storageId : config.getStorageIds()) {
			Configuration storageConfig = config.getStorageConfig(storageId);
			try {
				String storageClass = storageConfig.getParameter(Configuration.CLASS_PATH);
				Constructor<?> constructor = Class.forName(storageClass).getConstructor(Configuration.class);
				Storage storageInstance = (Storage) constructor.newInstance(storageConfig);
				storages.add(storageInstance);
				
				if(storageInstance.open()) {
					workingStatus.put(storageId, true);
				}
				else {
					workingStatus.put(storageId, false);	
				}
			} catch (Exception e) {
				throw new StreamException("Error during storage initialization", e);
			}
		}
	}
	
	private void createFilters(StreamsManagerConfiguration config) throws StreamException {
		for (String filterId : config.getFilterIds()) {
			try {
				Configuration fconfig = config.getFilterConfig(filterId);
				String className = fconfig.getParameter(Configuration.CLASS_PATH);
				Constructor<?> constructor = Class.forName(className).getConstructor(Configuration.class);
				ItemFilter filterInstance = (ItemFilter) constructor.newInstance(fconfig);
			
				filters.add(filterInstance);
			}
			catch(Exception e) {
				e.printStackTrace();
				logger.error("Error during filter " + filterId + "initialization", e);
			}
		}
	}
	
	private void createProcessors(StreamsManagerConfiguration config) throws StreamException {
		for (String processorId : config.getProcessorsIds()) {
			try {
					
				Configuration pconfig = config.getProcessorConfig(processorId);
				String className = pconfig.getParameter(Configuration.CLASS_PATH);
				Constructor<?> constructor = Class.forName(className).getConstructor(Configuration.class);
				Processor processorInstance = (Processor) constructor.newInstance(pconfig);
			
				processors.add(processorInstance);
			}
			catch(Exception e) {
				e.printStackTrace();
				logger.error("Error during processor " + processorId + " initialization", e);
			}
		}
	}
	
	/**
	 * Stops all consumer threads and all the databases used
	 */
	public void stop() {
		for(Consumer consumer : consumers) {
			consumer.die();
		}
		for(Storage storage : storages) {
			storage.close();
		}
		
		state = StorageHandlerState.CLOSE;
	}
}