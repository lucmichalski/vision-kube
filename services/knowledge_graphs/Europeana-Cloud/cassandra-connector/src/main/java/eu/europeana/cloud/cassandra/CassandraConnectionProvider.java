package eu.europeana.cloud.cassandra;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.Host;
import com.datastax.driver.core.Metadata;
import com.datastax.driver.core.Session;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Connector to Cassandra cluster.
 */
@Component
public class CassandraConnectionProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraConnectionProvider.class);

    private final Cluster cluster;

    private final Session session;

    private final ConsistencyLevel consistencyLevel = ConsistencyLevel.QUORUM;

    private String hosts;
        
    private String port;

    private String keyspaceName;
    /**
     * Constructor. Use it when your Cassandra cluster does not support authentication.
     * 
     * @param hosts
     *            cassandra node hosts, comma separated
     * @param port
     *            cassandra node cql service port
     * @param keyspaceName
     *            name of keyspace
     */
    public CassandraConnectionProvider(String hosts, int port, String keyspaceName) {
        this.hosts = hosts;
        this.port = String.valueOf(port);
        this.keyspaceName = keyspaceName;
        
        String[] contactPoints = hosts.split(",");
        cluster = Cluster.builder().addContactPoints(contactPoints).withPort(port).build();
        Metadata metadata = cluster.getMetadata();
        LOGGER.info("Connected to cluster: {}", metadata.getClusterName());
        for (Host h : metadata.getAllHosts()) {
            LOGGER.info("Datatacenter: {}; Hosts: {}; Rack: {}", h.getDatacenter(), h.getAddress(), h.getRack());
        }
        session = cluster.connect(keyspaceName);
    }

    
    /**
     * Constructor. Use it when your Cassandra cluster does support authentication.
     * 
     * @param hosts
     *            cassandra node hosts, comma separated
     * @param port
     *            cassandra node cql service port
     * @param keyspaceName
     *            name of keyspace
     * @param userName
     *            user name
     * @param password
     *            password
     */
    public CassandraConnectionProvider(String hosts, int port, String keyspaceName, String userName, String password) {
        this.hosts = hosts;
        this.port = String.valueOf(port);
        this.keyspaceName = keyspaceName;
        
        String[] contactPoints = hosts.split(",");
        cluster = Cluster.builder().addContactPoints(contactPoints).withCredentials(userName, password).withPort(port).build();
        Metadata metadata = cluster.getMetadata();
        LOGGER.info("Connected to cluster: {}", metadata.getClusterName());
        for (Host h : metadata.getAllHosts()) {
            LOGGER.info("Datatacenter: {}; Hosts: {}; Rack: {}", h.getDatacenter(), h.getAddress(), h.getRack());
        }
        session = cluster.connect(keyspaceName);
    }
        
        
    @PreDestroy
    private void closeConnections() {
        LOGGER.info("Cluster is shutting down.");
        cluster.close();
    }


     /**
     * Expose a singleton instance connection to a database on the requested
     * host and keyspace
     *
     * @return A session to a Cassandra connection
     */
    public Session getSession() {
        return session;
    }


    /**
     * Returns the default consistency level;
     * 
     * @return the consistencyLevel
     */
    public ConsistencyLevel getConsistencyLevel() {
        return consistencyLevel;
    }
    
     /**
     * Expose the contact server IP address
     *
     * @return The host name
     */
    public String getHosts() {
        return hosts;
    }

    /**
     * Expose the contact server port
     *
     * @return The host port
     */
    public String getPort() {
        return port;
    }

    /**
     * Expose the keyspace
     *
     * @return The keyspace name
     */
    public String getKeyspaceName() {
        return keyspaceName;
    }
}
