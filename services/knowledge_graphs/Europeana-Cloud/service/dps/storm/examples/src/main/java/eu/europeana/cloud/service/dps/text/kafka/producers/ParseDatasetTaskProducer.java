package eu.europeana.cloud.service.dps.text.kafka.producers;

import eu.europeana.cloud.service.dps.DpsTask;
import eu.europeana.cloud.service.dps.PluginParameterKeys;
import eu.europeana.cloud.service.dps.index.structure.IndexerInformations;
import eu.europeana.cloud.service.dps.storm.topologies.text.TextStrippingConstants;
import java.util.Properties;
import kafka.javaapi.producer.Producer;
import kafka.producer.KeyedMessage;
import kafka.producer.ProducerConfig;

/**
 *
 * @author Pavel Kefurt <Pavel.Kefurt@gmail.com>
 */
public class ParseDatasetTaskProducer 
{
    private static final String[] indexers = {"elasticsearch_indexer", "solr_indexer"};
    private static final String[] addresses = {"192.168.47.129:9300", "http://192.168.47.129:8983/solr"};
    
    public static final String datasetId = "ceffa_dataset4";
    public static final String providerId = "ceffa";
    //public static final String datasetId = "Franco_maria_performance_test_0002";
    //public static final String providerId = "CORE_testing_0002";

    /**
     * @param args the command line arguments
     *      1) kafka broker
     */
    public static void main(String[] args) 
    {
        Properties props = new Properties();
        if(args.length >= 1)
        {
            props.put("metadata.broker.list", args[0]);
        }
        else
        {
            props.put("metadata.broker.list", "192.168.47.129:9093");
        } 
        props.put("serializer.class", "eu.europeana.cloud.service.dps.storm.kafka.JsonEncoder");
        props.put("request.required.acks", "1");
                
        ProducerConfig config = new ProducerConfig(props);
        Producer<String, DpsTask> producer = new Producer<>(config);

        DpsTask msg = new DpsTask();

        msg.setTaskName(PluginParameterKeys.NEW_DATASET_MESSAGE);

        msg.addParameter(PluginParameterKeys.PROVIDER_ID, providerId);
        msg.addParameter(PluginParameterKeys.DATASET_ID, datasetId);
        msg.addParameter(PluginParameterKeys.EXTRACT_TEXT, "True");
        msg.addParameter(PluginParameterKeys.INDEX_DATA, "True");
        msg.addParameter(PluginParameterKeys.STORE_EXTRACTED_TEXT, "False");
               
        //if INDEX_DATA == True
        IndexerInformations ii = new IndexerInformations(indexers[1], "index_mlt_4", "mlt4", addresses[1]);
        msg.addParameter(PluginParameterKeys.INDEXER, ii.toTaskString());
        
        KeyedMessage<String, DpsTask> data = new KeyedMessage<>(TextStrippingConstants.KAFKA_INPUT_TOPIC, msg);
        producer.send(data);
        producer.close();
    }
    
}
