package eu.europeana.cloud.service.dls.solr;

import org.apache.solr.client.solrj.SolrServer;

/**
 * Interface for Solr connection providers.
 */
public interface SolrConnectionProvider {

    /**
     * Return solr server instance.
     * 
     * @return instance of Solr server
     */
    SolrServer getSolrServer();

}
