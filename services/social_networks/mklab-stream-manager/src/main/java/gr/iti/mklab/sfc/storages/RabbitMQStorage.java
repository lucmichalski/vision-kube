package gr.iti.mklab.sfc.storages;

import gr.iti.mklab.framework.common.domain.Item;
import gr.iti.mklab.framework.common.domain.config.Configuration;

import java.io.IOException;

import org.apache.commons.lang3.SerializationUtils;
import org.apache.log4j.Logger;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MessageProperties;

public class RabbitMQStorage implements Storage {

	private Logger  logger = Logger.getLogger(RabbitMQStorage.class);
	
	private static String HOSTNAME = "rabbitmq.hostname";
	private static String ITEMS_QUEUE = "rabbitmq.items.queue";
	
	//private static String WEBPAGES_CHANNEL = "rabbitmq.webpages.channel";
	//private static String MEDIA_CHANNEL = "rabbitmq.media.channel";
	
	private String hostname;
	
	private String itemsQueueName;
	//private String webPagesChannel;
	//private String mediaItemsChannel;
	
	private Connection connection;
	private Channel channel;
	
	public RabbitMQStorage(Configuration config) {
		this.hostname = config.getParameter(RabbitMQStorage.HOSTNAME);
		this.itemsQueueName = config.getParameter(RabbitMQStorage.ITEMS_QUEUE);	
		//this.webPagesChannel = config.getParameter(RabbitMQStorage.WEBPAGES_CHANNEL);
		//this.mediaItemsChannel = config.getParameter(RabbitMQStorage.MEDIA_CHANNEL);
	}
	
	@Override
	public boolean open() {
		ConnectionFactory factory = new ConnectionFactory();
		factory.setHost(hostname);
		try {
			connection = factory.newConnection();
			channel = connection.createChannel();
			
			channel.exchangeDeclare(itemsQueueName, "direct", true);
			channel.queueDeclare(itemsQueueName, true, false, false, null);

			channel.queueBind(itemsQueueName, itemsQueueName, itemsQueueName);
			
		} catch (Exception e) {
			logger.error(e);
			return false;
		}
		
		return true;
	}

	@Override
	public void store(Item item) throws IOException {
		byte[] messageBytes = SerializationUtils.serialize(item);

		channel.basicPublish(itemsQueueName, itemsQueueName, true,
		                     MessageProperties.PERSISTENT_TEXT_PLAIN,
		                     messageBytes);
	}

	@Override
	public boolean delete(String id) throws IOException {
		return false;
	}

	@Override
	public boolean checkStatus() {

		return false;
	}

	@Override
	public void close() {
		try {
			channel.close();
			connection.close();
		} catch (IOException e) {
			logger.error(e);
		}
	}

	@Override
	public String getStorageName() {
		return "RabbitMQ";
	}

}
