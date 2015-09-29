package gr.iti.mklab.focused.crawler;

import java.net.UnknownHostException;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.log4j.Logger;

import gr.iti.mklab.focused.crawler.bolts.media.ClustererBolt;
import gr.iti.mklab.focused.crawler.bolts.media.MediaRankerBolt;
import gr.iti.mklab.focused.crawler.bolts.media.MediaTextIndexerBolt;
import gr.iti.mklab.focused.crawler.bolts.media.MediaUpdaterBolt;
import gr.iti.mklab.focused.crawler.bolts.media.VisualIndexerBolt;
import gr.iti.mklab.focused.crawler.bolts.metrics.MediaCounterBolt;
import gr.iti.mklab.focused.crawler.spouts.RedisSpout;
import backtype.storm.Config;
import backtype.storm.LocalCluster;
import backtype.storm.StormSubmitter;
import backtype.storm.generated.AlreadyAliveException;
import backtype.storm.generated.InvalidTopologyException;
import backtype.storm.generated.StormTopology;
import backtype.storm.topology.IRichBolt;
import backtype.storm.topology.TopologyBuilder;
import backtype.storm.topology.base.BaseRichSpout;

public class VisualIndexer {

	private static Logger logger = Logger.getLogger(VisualIndexer.class);
	
	/**
	 *	@author Manos Schinas - manosetro@iti.gr
	 *
	 *	Entry class for distributed visual indexing. 
	 *  This class defines a storm-based pipeline (topology) for the processing of MediaItems 
	 *  received from a Redis-based pub/sub channel. 
	 *  
	 * 	The main steps in the topology are: FeatureExtraction/VisualIndexing, ConceptDetection, TextIndexing,
	 *  VisualClustering and media-based Statistics (e.g top tags, top users etc.)
	 *  
	 *  For more information on Storm distributed processing check this tutorial:
	 *  https://github.com/nathanmarz/storm/wiki/Tutorial
	 *  
	 */
	public static void main(String[] args) throws UnknownHostException {
		
		XMLConfiguration config;
		try {
			if(args.length == 1)
				config = new XMLConfiguration(args[0]);
			else
				config = new XMLConfiguration("./conf/focused.crawler.xml");
		}
		catch(ConfigurationException ex) {
			logger.error(ex);
			return;
		}
		
		StormTopology topology;
		try {
			topology = createTopology(config);
		}
		catch(Exception e) {
			logger.error("Cannot create topology", e);
			return;
		}
		
		if(topology == null) {
			logger.error("Tpology is null. ");
			return;
		}
		
        // Run topology
        String name = config.getString("topology.visualIndexerName", "VisualIndexer");
        boolean local = config.getBoolean("topology.local", true);
        
        Config conf = new Config();
        conf.setDebug(false);
        
        if(!local) {
        	System.out.println("Submit topology to Storm cluster");
			try {
				int workers = config.getInt("topology.workers", 2);
				conf.setNumWorkers(workers);
				
				StormSubmitter.submitTopology(name, conf, topology);
			}
			catch(NumberFormatException e) {
				logger.error(e);
			} catch (AlreadyAliveException e) {
				logger.error(e);
			} catch (InvalidTopologyException e) {
				logger.error(e);
			}
			
		} else {
			logger.info("Run topology in local mode");
			LocalCluster cluster = new LocalCluster();
			cluster.submitTopology(name, conf, topology);
		}
	}
	
	public static StormTopology createTopology(XMLConfiguration config) {
		
		String redisHost = config.getString("redis.hostname", "xxx.xxx.xxx.xxx");
		String redisMediaChannel = config.getString("redis.mediaItemsChannel", "media");
		
		String mongodbHostname = config.getString("mongodb.hostname", "xxx.xxx.xxx.xxx");
		String mediaItemsDB = config.getString("mongodb.mediaItemsDB", "Prototype");
		String mediaItemsCollection = config.getString("mongodb.mediaItemsCollection", "MediaItems");
		String streamUsersDB = config.getString("mongodb.streamUsersDB", "Prototype");
		String clustersDB = config.getString("mongodb.clustersDB", "Prototype");
		String clustersCollection = config.getString("mongodb.clustersCollection", "MediaClusters");
		
		String mediaTextIndexHostname = config.getString("textindex.hostname", "http://xxx.xxx.xxx.xxx:8080/solr");
		String mediaTextIndexCollection = config.getString("textindex.collections.media", "MediaItems");
		String mediaTextIndexService = mediaTextIndexHostname + "/" + mediaTextIndexCollection;
		
		String visualIndexHostname = config.getString("visualindex.hostname");
		String visualIndexCollection = config.getString("visualindex.collection");
		
		String learningFiles = config.getString("visualindex.learningfiles");
		if(!learningFiles.endsWith("/"))
			learningFiles = learningFiles + "/";
		
		String[] codebookFiles = { 
				learningFiles + "surf_l2_128c_0.csv",
				learningFiles + "surf_l2_128c_1.csv", 
				learningFiles + "surf_l2_128c_2.csv",
				learningFiles + "surf_l2_128c_3.csv" };
		
		String pcaFile = learningFiles + "pca_surf_4x128_32768to1024.txt";
		
		BaseRichSpout miSpout;
		IRichBolt miRanker, mediaCounter, visualIndexer, mediaUpdater;
		IRichBolt mediaTextIndexer, clusterer;
		
		try {
			miSpout = new RedisSpout(redisHost, redisMediaChannel, "id");	
			miRanker = new MediaRankerBolt(redisMediaChannel);
			
			mediaCounter = new MediaCounterBolt(mongodbHostname, "Prototype");
			visualIndexer = new VisualIndexerBolt(visualIndexHostname, visualIndexCollection, codebookFiles, pcaFile);
			clusterer = new ClustererBolt(mongodbHostname, mediaItemsDB, mediaItemsCollection, clustersDB, clustersCollection, visualIndexHostname, visualIndexCollection, mediaTextIndexService);
			
			mediaUpdater = new MediaUpdaterBolt(mongodbHostname, mediaItemsDB, streamUsersDB);
			mediaTextIndexer = new MediaTextIndexerBolt(mediaTextIndexService);
		} catch (Exception e) {
			logger.error(e);
			return null;
		}
		
		// Create topology 
		TopologyBuilder builder = new TopologyBuilder();
		builder.setSpout("miInjector", miSpout, 1);
				
		builder.setBolt("miRanker", miRanker, 4).shuffleGrouping("miInjector");

		builder.setBolt("counter", mediaCounter, 1).shuffleGrouping("miRanker");
		builder.setBolt("indexer", visualIndexer, 16).shuffleGrouping("miRanker");
		builder.setBolt("clusterer", clusterer, 1).shuffleGrouping("indexer");   
		        
		builder.setBolt("mediaupdater", mediaUpdater, 1).shuffleGrouping("indexer");
		builder.setBolt("mediaTextIndexer", mediaTextIndexer, 1).shuffleGrouping("indexer");
		
		return builder.createTopology();
	}
}