package gr.iti.mklab.bolt;

import backtype.storm.topology.BasicOutputCollector;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseBasicBolt;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;
import gr.iti.mklab.core.ProcessorSingleton;

/**
 * Performs visual feature extraction
 *
 * Created by kandreadou on 4/11/14.
 */
public class FeatureExtractionBolt extends BaseBasicBolt {
    @Override
    public void execute(Tuple tuple, BasicOutputCollector basicOutputCollector) {
        String imageFolder = (String) tuple.getValueByField("folder");
        String imageName = (String) tuple.getValueByField("name");
        System.out.println("# Extractor received: folder "+imageFolder+" name "+imageName);
        double[] vector = ProcessorSingleton.get().getVector(imageFolder, imageName);
        basicOutputCollector.emit(new Values(imageName, vector));
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declare(new Fields("name", "vector"));
    }
}
