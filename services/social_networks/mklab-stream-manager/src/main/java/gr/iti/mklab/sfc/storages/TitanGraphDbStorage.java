package gr.iti.mklab.sfc.storages;

import java.io.IOException;

import org.apache.commons.configuration.BaseConfiguration;

import com.thinkaurelius.titan.core.TitanFactory;
import com.thinkaurelius.titan.core.TitanGraph;
import com.tinkerpop.blueprints.Compare;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Vertex;

import gr.iti.mklab.framework.common.domain.config.Configuration;
import gr.iti.mklab.framework.common.domain.Item;

public class TitanGraphDbStorage implements Storage {

	private static String STORAGE_BACKEND = "titan.storage.backend";
	private static String STORAGE_TABLENAME = "titan.storage.tablename";
	private static String VERTEX_USER_ID = "titan.vertex.userid";
	private static String EDGE_RETWEETS = "titan.edge.retweets";
	private static String EDGE_MENTIONS = "titan.edge.mentions";
	private static String EDGE_PROPERTY_TIMESTAMP = "titan.edge.property.timestamp";
	private static String EDGE_PROPERTY_TWEETID = "titan.edge.property.tweetid";
	
	private String storageName = "Titan";
	
	private String storageBackend;
	private String storageTableName;
	
	private String vertexUserId;
	private String edgeReTweets;
	private String edgeMentions;
	
	private String edgePropertyTimestamp;
	private String edgePropertyTweedId;
	
	private TitanGraph titanGraph;
	
	public TitanGraphDbStorage(Configuration config) {
		this.storageBackend = config.getParameter(TitanGraphDbStorage.STORAGE_BACKEND);
		this.storageTableName = config.getParameter(TitanGraphDbStorage.STORAGE_TABLENAME);
		
		this.vertexUserId = config.getParameter(TitanGraphDbStorage.VERTEX_USER_ID);
		
		this.edgeReTweets = config.getParameter(TitanGraphDbStorage.EDGE_RETWEETS);
		this.edgeMentions = config.getParameter(TitanGraphDbStorage.EDGE_MENTIONS);
		
		this.edgePropertyTimestamp = config.getParameter(TitanGraphDbStorage.EDGE_PROPERTY_TIMESTAMP);
		this.edgePropertyTweedId = config.getParameter(TitanGraphDbStorage.EDGE_PROPERTY_TWEETID);
	}
	
	@Override
	public boolean open() {
		BaseConfiguration conf = new BaseConfiguration();
		conf.setProperty("storage.backend", storageBackend);
		conf.setProperty("storage.tablename", storageTableName);
		titanGraph = TitanFactory.open(conf);
		titanGraph.makeKey(vertexUserId).dataType(String.class).indexed(Vertex.class).make();
		titanGraph.makeLabel(edgeMentions).make();
		titanGraph.makeLabel(edgeReTweets).make();
		titanGraph.commit();
		return true;
	}

	@Override
	public void store(Item item) throws IOException {
		String userId = item.getUserId();
		String tweetId = item.getId();
		long timestamp = item.getPublicationTime();
//		String title = item.getTitle();
		
		Vertex source = getOrCreateVertex(userId, vertexUserId);
		
		//handle mentions
		String[] mentions = item.getMentions();
		for(String userMention : mentions) {
			Edge edge;
			//handle retweets
			if(!item.isOriginal()) {
				String userRetweets = item.getReferencedUserId();
				Vertex destination = getOrCreateVertex(userRetweets, vertexUserId);
				edge = titanGraph.addEdge(null, source, destination, edgeReTweets);
//				edge.setProperty("title", title);
			}
			else {
				Vertex destination = getOrCreateVertex(userMention, vertexUserId);
				edge = titanGraph.addEdge(null, source, destination, edgeMentions);
//				edge.setProperty("title", title);
			}
			edge.setProperty(edgePropertyTweedId, tweetId);
			edge.setProperty(edgePropertyTimestamp, timestamp);
			
		}
		
		titanGraph.commit();
	}

	@Override
	public boolean delete(String id) throws IOException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void close() {
		titanGraph.shutdown();
	}
	
	private Vertex getOrCreateVertex(String vertexId, String vertexKey) {
		Vertex userId;
		if(titanGraph.query().has(vertexKey, Compare.EQUAL, vertexId).vertices().iterator().hasNext()) {
			userId = titanGraph.query().has(vertexKey, Compare.EQUAL, vertexId).vertices().iterator().next();
		}
		else {
			userId = titanGraph.addVertex(null);
			userId.setProperty(vertexKey, vertexId);
		}
		return userId;
	}

	@Override
	public boolean checkStatus() {
		if(titanGraph.isOpen()) {
			return true;
		}
		else {
			return false;
		}
	}

	@Override
	public String getStorageName() {
		return this.storageName;
	}

}
