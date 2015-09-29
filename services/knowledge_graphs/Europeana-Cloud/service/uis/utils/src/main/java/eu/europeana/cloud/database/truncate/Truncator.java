/* Truncator.java - created on Jan 10, 2014, Copyright (c) 2013 Europeana Foundation, all rights reserved */
package eu.europeana.cloud.database.truncate;

import com.google.common.collect.ImmutableList;
import eu.europeana.cloud.cassandra.CassandraConnectionProvider;

import eu.europeana.cloud.service.uis.persistent.util.DatabaseTruncateUtil;

/**
 * Database truncator class
 * 
 * @author Yorgos Mamakis (Yorgos.Mamakis@ europeana.eu)
 * @since Jan 10, 2014
 */
public class Truncator {

	private CassandraConnectionProvider dbService;

	/**
	 * Creates a new instance of this class.
	 * 
	 * @param dbService
	 */
	public Truncator(CassandraConnectionProvider dbService) {
		this.dbService = dbService;
	}

	/**
	 * Truncate the database
	 */
	public void truncate() {
		DatabaseTruncateUtil dbUtil = new DatabaseTruncateUtil(dbService);
		dbUtil.truncateTables(ImmutableList.of("data_providers", "Cloud_Id", "Provider_Record_Id"));
	}

}
