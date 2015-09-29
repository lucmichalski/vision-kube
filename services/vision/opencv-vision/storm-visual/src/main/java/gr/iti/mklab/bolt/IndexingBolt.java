package gr.iti.mklab.bolt;

import backtype.storm.topology.BasicOutputCollector;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseBasicBolt;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;
import gr.iti.mklab.core.ProcessorSingleton;

/**
 * Indexes
 *
 * Created by kandreadou on 4/11/14.
 */
public class IndexingBolt extends BaseBasicBolt {
    @Override
    public void execute(Tuple tuple, BasicOutputCollector basicOutputCollector) {
        String id = (String) tuple.getValueByField("name");
        double[] vector = (double[]) tuple.getValueByField("vector");
        System.out.println("# Indexer received: id "+id+" vector length "+vector.length);
        boolean indexed = ProcessorSingleton.get().index(id, vector);
        basicOutputCollector.emit(new Values(indexed));
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declare(new Fields("indexed"));
    }
}
