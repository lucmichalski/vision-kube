package gr.iti.mklab.sfc.storages;

import java.io.IOException;
import java.sql.SQLException;

import javax.ws.rs.core.MediaType;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

import gr.iti.mklab.framework.common.domain.config.Configuration;
import gr.iti.mklab.framework.common.domain.Item;

public class Neo4jStorage implements Storage {
	
	private static String HOST = "neo4j.host";
	
	private static String NODE_USER_LABEL = "neo4j.node.user.label";
	private static String NODE_USER_ID = "neo4j.node.userid";
	
	private static String RELATIONSHIP_RETWEETS = "neo4j.relationship.retweets";
	private static String RELATIONSHIP_MENTIONS = "neo4j.relationship.mentions";
	private static String RELATIONSHIP_PROPERTY_TIMESTAMP = "neo4j.relationship.property.timestamp";
	private static String RELATIONSHIP_PROPERTY_TWEETID = "neo4j.relationship.property.tweetid";
	
	private String storageName = "Neo4j";
	
	private String host;
	
	private String nodeUserLabel;
	private String nodeUserId;
	
	private String relationshipReTweets;
	private String relationshipMentions;
	
	private String relationshipPropertyTimestamp;
	private String relationshipPropertyTweedId;
	
	WebResource resource;
	ClientResponse response;
	
	public Neo4jStorage(Configuration config) {
		
		this.host = config.getParameter(HOST);
		
		this.nodeUserLabel = config.getParameter(NODE_USER_LABEL);
		this.nodeUserId = config.getParameter(NODE_USER_ID);
		
		this.relationshipReTweets = config.getParameter(RELATIONSHIP_RETWEETS);
		this.relationshipMentions = config.getParameter(RELATIONSHIP_MENTIONS);
		
		this.relationshipPropertyTimestamp = config.getParameter(RELATIONSHIP_PROPERTY_TIMESTAMP);
		this.relationshipPropertyTweedId = config.getParameter(RELATIONSHIP_PROPERTY_TWEETID);
	}

	@Override
	public boolean open() {
		resource = Client.create().resource(host + "db/data/cypher");
		try {
			createIndex(nodeUserLabel, nodeUserId);
		} 
		catch (SQLException e) {
			e.printStackTrace();
		}
		
		return true;
	}

	@Override
	public void store(Item item) throws IOException {
		String userId = item.getUserId().split("#")[1];
		String tweetId = item.getId().split("#")[1];
		long timestamp = item.getPublicationTime();

		try {
			getOrCreateVertex(userId, nodeUserId);
			//handle mentions
			String[] mentions = item.getMentions();
			for(String userMention : mentions) {
				
				//handle retweets
				if(!item.isOriginal()) {
					String userRetweets = item.getReferencedUserId().split("#")[1];
					getOrCreateVertex(userRetweets, nodeUserId);
					getOrCreateEdge(userId, userRetweets, relationshipReTweets);
					addEdgeProperty(userId, userRetweets, relationshipPropertyTweedId, tweetId);
					addEdgeProperty(userId, userRetweets, relationshipPropertyTimestamp, String.valueOf(timestamp));
				}
				else {
					userMention = userMention.split("#")[1];
					getOrCreateVertex(userMention, nodeUserId);
					getOrCreateEdge(userId, userMention, relationshipMentions);
					addEdgeProperty(userId, userMention, relationshipPropertyTweedId, tweetId);
					addEdgeProperty(userId, userMention, relationshipPropertyTimestamp, String.valueOf(timestamp));
				}
				
			}
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	@Override
	public boolean delete(String id) throws IOException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean checkStatus() {
		return true;
	}

	@Override
	public void close() {
		//resource.delete();
	}

	@Override
	public String getStorageName() {
		return storageName;
	}
	
	private void createIndex(String label, String property) throws SQLException {
		String createIndexQuery = "CREATE INDEX ON :" + nodeUserLabel 
				+ "(" + nodeUserId + ")";
		sendCypherQuery(createIndexQuery);
		response.close();
	}
	
	private void getOrCreateVertex(String nodeId, String nodeKey) throws SQLException {
		String mergeVertex = 
				"MERGE (n:" + nodeUserLabel + " {" + nodeKey + ":" + nodeId + "}) ";
		sendCypherQuery(mergeVertex);
		response.close();
	}
	
	private void getOrCreateEdge(String source, String destination, String label) throws SQLException {
		String mergeEdge = 
				"MATCH (n1:" + nodeUserLabel + " {" + nodeUserId + ":" + source + "})" +
				"MATCH (n2:" + nodeUserLabel + " {" + nodeUserId + ":" + destination + "})" +
				"CREATE (n1)-[:" + label + "]->(n2)";
		sendCypherQuery(mergeEdge);
		response.close();
	}
	
	private void addEdgeProperty(String source, String destination, String propertyName, 
			String propertyValue) throws SQLException {
		String addEdgeProperty = 
				"MATCH (n1:" + nodeUserLabel + " {" + nodeUserId + ":" + source + "})" +
				"MATCH (n2:" + nodeUserLabel + " {" + nodeUserId + ":" + destination + "})" +
				"MATCH (n1)-[r]->(n2)" +
				"SET r." + propertyName + " = " + propertyValue;
		sendCypherQuery(addEdgeProperty);
		response.close();
	}
	
	private ClientResponse sendCypherQuery(String query) {
		response = resource.accept(MediaType.APPLICATION_JSON)
				.type(MediaType.APPLICATION_JSON)
				.entity("{\"query\" : \"" + query + "\"}")
				.post(ClientResponse.class);
		return response;
	}
	
}
