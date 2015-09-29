package eu.europeana.cloud.service.dps.service.cassandra;

/**
 *
 * @author Pavel Kefurt <Pavel.Kefurt@gmail.com>
 */
public class CassandraTablesAndColumnsNames 
{
    //------- TABLES -------
    public static final String BASIC_INFO_TABLE = "basic_info";
    public static final String NOTIFICATIONS_TABLE = "notifications";
    
    //------- BASIC INFO -------
    public static final String BASIC_TASK_ID = "task_id";
    public static final String BASIC_TOPOLOGY_NAME = "topology_name";
    public static final String BASIC_EXPECTED_SIZE = "expected_size";
       
    //------- NOTIFICATION -------
    public static final String NOTIFICATION_TASK_ID = "task_id";
    public static final String NOTIFICATION_TOPOLOGY_NAME = "topology_name";
    public static final String NOTIFICATION_RESOURCE = "resource";
    public static final String NOTIFICATION_STATE = "state";
    public static final String NOTIFICATION_INFO_TEXT = "info_text";
    public static final String NOTIFICATION_ADDITIONAL_INFORMATIONS = "additional_informations";
}
