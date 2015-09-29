package gr.iti.mklab.sfc.management;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;

import org.apache.log4j.Logger;

import gr.iti.mklab.framework.common.domain.Item;
import gr.iti.mklab.sfc.filters.ItemFilter;
import gr.iti.mklab.sfc.processors.Processor;
import gr.iti.mklab.sfc.storages.Storage;

/**
 * Class for storing items to databases
 * 
 * 
 * @author manosetro
 * @email  manosetro@iti.gr
 *
 */
public class Consumer extends Thread {
	
	private Logger _logger = Logger.getLogger(Consumer.class);
	
	private static int id = 0;
	
	private boolean isAlive = true;
	private List<Storage> storages = null;
	
	private BlockingQueue<Item> queue;
	
	private Collection<ItemFilter> filters;
	private Collection<Processor> processors;
	
	public Consumer(BlockingQueue<Item> queue, List<Storage> storages, Collection<ItemFilter> filters, Collection<Processor> processors) {
		this.storages = storages;
		this.queue = queue;
		this.filters = filters;
		this.processors = processors;
		
		this.setName("Consumer_" + (id++));
	}
	
	/**
	 * Stores an item if the latter is found waiting in the queue
	 */
	public void run() {			
		Item item = null;
		while (isAlive) {
			try {
				item = take();
				if (item == null) {
					_logger.error("Item is null.");
				} 
				else {
					process(item);
				}
			} catch(IOException e) {
				e.printStackTrace();
				_logger.error(e);
			}
		}
		
		//empty queue
		while ((item = poll()) != null) {
			try {
				process(item);
			} catch (IOException e) {
				e.printStackTrace();
				_logger.error(e);
			}
		}
	}
	
	/**
	 * Stores an item to all available databases
	 * @param item
	 * @throws IOException
	 */
	private void process(Item item) throws IOException {
		if (storages != null) {
			for(ItemFilter filter : filters) {
				if(!filter.accept(item)) {
					return;
				}
			}
			
			for(Processor processor : processors) {
				processor.process(item);	
			}
			
			for(Storage storage : storages) {
				storage.store(item);
			}
		}
	}
	
	/**
	 * Polls an item from the queue
	 * @return
	 */
	private Item poll() {			
		return queue.poll();		
	}
	
	/**
	 * Polls an item from the queue. Waits if the queue is empty. 
	 * @return
	 */
	private Item take() {				
		Item item = null;
		try {
			item = queue.take();
		} catch (InterruptedException e) {
			_logger.error(e);
		}	
		return item;
	}
	
	/**
	 * Stops the consumer thread
	 */
	public synchronized void die() {
		isAlive = false;
		try {
			this.interrupt();
		}
		catch(Exception e) {
			_logger.error(e);
		}
	}
}
