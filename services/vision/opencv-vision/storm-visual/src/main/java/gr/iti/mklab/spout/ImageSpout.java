package gr.iti.mklab.spout;

import backtype.storm.spout.SpoutOutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichSpout;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Values;

import java.io.File;
import java.util.Map;

/**
 * Created by kandreadou on 4/11/14.
 */
public class ImageSpout extends BaseRichSpout {

    private final File[] files;
    private int count = 0;
    private SpoutOutputCollector collector;
    private final static String FOLDER_NAME = "/home/kandreadou/datasets/mixed/jpg/";

    public ImageSpout() {
        File folder = new File(FOLDER_NAME);
        files = folder.listFiles();
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declare(new Fields("folder","name"));
    }

    @Override
    public void open(Map map, TopologyContext topologyContext, SpoutOutputCollector spoutOutputCollector) {
        collector = spoutOutputCollector;
    }

    @Override
    public void nextTuple() {
        if (count < 10) {
            String next = files[count].getName();
            count++;
            collector.emit(new Values(FOLDER_NAME, next));
        }
    }
}
