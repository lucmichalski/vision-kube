package eu.europeana.cloud.service.uis.persistent.dao;

import java.util.ArrayList;
import java.util.List;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.exceptions.NoHostAvailableException;
import eu.europeana.cloud.cassandra.CassandraConnectionProvider;

import eu.europeana.cloud.common.model.CloudId;
import eu.europeana.cloud.common.model.IdentifierErrorInfo;
import eu.europeana.cloud.common.model.LocalId;
import eu.europeana.cloud.service.uis.exception.CloudIdAlreadyExistException;
import eu.europeana.cloud.service.uis.exception.CloudIdDoesNotExistException;
import eu.europeana.cloud.service.uis.exception.DatabaseConnectionException;
import eu.europeana.cloud.service.uis.status.IdentifierErrorTemplate;

/**
 * Dao providing access to database operations on Cloud id database
 * 
 * @author Yorgos.Mamakis@ kb.nl
 * 
 */
public class CassandraCloudIdDAO {

    private String hostList;
    private String keyspaceName;
    private String port;
    private CassandraConnectionProvider dbService;
    private PreparedStatement insertStatement;
    private PreparedStatement insertIfNoExistsStatement;
    private PreparedStatement searchStatementNonActive;
    private PreparedStatement deleteStatement;

    /**
     * The Cloud Id Dao
     * 
     * @param dbService
     *            The service exposing the connection and session
     */
    public CassandraCloudIdDAO(CassandraConnectionProvider dbService) {
	this.dbService = dbService;
	this.hostList = dbService.getHosts();
	this.port = dbService.getPort();
	this.keyspaceName = dbService.getKeyspaceName();
	prepareStatements();
    }

    private void prepareStatements() {
	insertIfNoExistsStatement = dbService
		.getSession()
		.prepare(
			"INSERT INTO Cloud_Id(cloud_id,provider_id,record_id,deleted) VALUES(?,?,?,false) IF NOT EXISTS");
	insertStatement = dbService
		.getSession()
		.prepare(
			"INSERT INTO Cloud_Id(cloud_id,provider_id,record_id,deleted) VALUES(?,?,?,false)");
	insertStatement.setConsistencyLevel(dbService.getConsistencyLevel());
	searchStatementNonActive = dbService.getSession().prepare(
		"SELECT * FROM Cloud_Id WHERE cloud_id=?");
	searchStatementNonActive.setConsistencyLevel(dbService
		.getConsistencyLevel());
	deleteStatement = dbService
		.getSession()
		.prepare(
			"UPDATE Cloud_Id SET deleted=true WHERE cloud_Id=? AND provider_id=? AND record_id=?");
	deleteStatement.setConsistencyLevel(dbService.getConsistencyLevel());
    }

    public List<CloudId> searchById(boolean deleted, String... args)
            throws DatabaseConnectionException, CloudIdDoesNotExistException {
        try {
            ResultSet rs = dbService.getSession().execute(searchStatementNonActive.bind(args[0]));
            if (!rs.iterator().hasNext()) {
                throw new CloudIdDoesNotExistException(new IdentifierErrorInfo(
                        IdentifierErrorTemplate.CLOUDID_DOES_NOT_EXIST.getHttpCode(),
                        IdentifierErrorTemplate.CLOUDID_DOES_NOT_EXIST.getErrorInfo(args[0])));
            }
            List<CloudId> cloudIds = new ArrayList<>();
            for (Row row : rs.all()) {
                if (row.getBool("deleted") == deleted) {
                    CloudId cId = new CloudId();
                    cId.setId(args[0]);
                    LocalId lId = new LocalId();
                    lId.setProviderId(row.getString("provider_Id"));
                    lId.setRecordId(row.getString("record_Id"));
                    cId.setLocalId(lId);
                    cloudIds.add(cId);
                }
            }

            return cloudIds;
        } catch (NoHostAvailableException e) {
            throw new DatabaseConnectionException(new IdentifierErrorInfo(
                    IdentifierErrorTemplate.DATABASE_CONNECTION_ERROR.getHttpCode(),
                    IdentifierErrorTemplate.DATABASE_CONNECTION_ERROR.getErrorInfo(hostList, port, e.getMessage())));
        }
    }

    public List<CloudId> searchActive(String... args)
	    throws DatabaseConnectionException, CloudIdDoesNotExistException {
	return searchById(false, args[0]);
    }

    /**
     * Search for all the Cloud Identifiers regardless if they are deleted or
     * not
     * 
     * @param args
     *            The cloudId to search on
     * @return A list of cloudIds
     * @throws DatabaseConnectionException
     */
    public List<CloudId> searchAll(String args)
            throws DatabaseConnectionException {
        ResultSet rs = dbService.getSession().execute(searchStatementNonActive.bind(args));
        List<Row> results = rs.all();
        List<CloudId> cloudIds = new ArrayList<>();
        for (Row row : results) {
            CloudId cId = new CloudId();
            cId.setId(args);
            LocalId lId = new LocalId();
            lId.setProviderId(row.getString("provider_Id"));
            lId.setRecordId(row.getString("record_Id"));
            cId.setLocalId(lId);
            cloudIds.add(cId);
        }
        return cloudIds;
    }

    public List<CloudId> insert(boolean insertOnlyIfNoExist, String... args)
            throws DatabaseConnectionException, CloudIdDoesNotExistException, CloudIdAlreadyExistException {
        ResultSet rs = null;
        try {
            if (insertOnlyIfNoExist) {
                rs = dbService.getSession().execute(insertIfNoExistsStatement.bind(args[0], args[1], args[2]));
                Row row = rs.one();
                if (row.getBool("[applied]") == false) {
                    throw new CloudIdAlreadyExistException(new IdentifierErrorInfo(
                            IdentifierErrorTemplate.CLOUDID_ALREADY_EXIST.getHttpCode(),
                            IdentifierErrorTemplate.CLOUDID_ALREADY_EXIST.getErrorInfo(args[0])));
                }
            } else {
                dbService.getSession().execute(insertStatement.bind(args[0], args[1], args[2]));
            }
        } catch (NoHostAvailableException e) {
            throw new DatabaseConnectionException(new IdentifierErrorInfo(
                    IdentifierErrorTemplate.DATABASE_CONNECTION_ERROR.getHttpCode(),
                    IdentifierErrorTemplate.DATABASE_CONNECTION_ERROR.getErrorInfo(hostList, port, e.getMessage())));
        }

        CloudId cId = new CloudId();
        LocalId lId = new LocalId();
        lId.setProviderId(args[1]);
        lId.setRecordId(args[2]);
        cId.setLocalId(lId);
        cId.setId(args[0]);
        List<CloudId> cIds = new ArrayList<>();
        cIds.add(cId);
        return cIds;
    }

    public void delete(String... args) throws DatabaseConnectionException {
	try {
	    dbService.getSession().execute(
		    deleteStatement.bind(args[0], args[1], args[2]));
	} catch (NoHostAvailableException e) {
	    throw new DatabaseConnectionException(
		    new IdentifierErrorInfo(
			    IdentifierErrorTemplate.DATABASE_CONNECTION_ERROR
				    .getHttpCode(),
			    IdentifierErrorTemplate.DATABASE_CONNECTION_ERROR
				    .getErrorInfo(hostList, port,
					    e.getMessage())));
	}
    }

    public void update(String... obj) throws DatabaseConnectionException {
	throw new UnsupportedOperationException(
		"This method is not implemented for the Cloud Id");
    }

    public String getHostList() {
	return hostList;
    }

    public String getKeyspace() {
	return keyspaceName;
    }

    public String getPort() {
	return this.port;
    }

}
