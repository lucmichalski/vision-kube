package gr.iti.mklab.bolt;

import backtype.storm.topology.BasicOutputCollector;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseBasicBolt;
import backtype.storm.tuple.Tuple;

/**
 * Logs
 *
 * Created by kandreadou on 4/11/14.
 */
public class LoggerBolt extends BaseBasicBolt {
    @Override
    public void execute(Tuple tuple, BasicOutputCollector basicOutputCollector) {
        System.out.println("# Logger:  "+tuple);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {

    }
}
