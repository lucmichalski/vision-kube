package gr.iti.mklab;

import backtype.storm.Config;
import backtype.storm.LocalCluster;
import backtype.storm.topology.TopologyBuilder;
import backtype.storm.utils.Utils;
import gr.iti.mklab.bolt.FeatureExtractionBolt;
import gr.iti.mklab.bolt.IndexingBolt;
import gr.iti.mklab.bolt.LoggerBolt;
import gr.iti.mklab.core.ProcessorSingleton;
import gr.iti.mklab.spout.ImageSpout;

/**
 * Created by kandreadou on 4/11/14.
 */
public class IndexingTopology {
    public static void main(String[] args) throws Exception {
        ProcessorSingleton.get(); // initialize stuff first
        TopologyBuilder builder = new TopologyBuilder();

        builder.setSpout("image", new ImageSpout(), 5);
        builder.setBolt("extraction", new FeatureExtractionBolt(), 3).shuffleGrouping("image");
        builder.setBolt("index", new IndexingBolt(), 2).shuffleGrouping("extraction");
        builder.setBolt("log", new LoggerBolt(), 1).shuffleGrouping("index");

        Config conf = new Config();
        conf.setDebug(true);

        LocalCluster cluster = new LocalCluster();
        cluster.submitTopology("test", conf, builder.createTopology());
        Utils.sleep(10000);
        cluster.killTopology("test");
        cluster.shutdown();

    }
}
