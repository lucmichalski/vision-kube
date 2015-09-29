package gr.iti.mklab.focused.crawler;

import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.log4j.Logger;
import org.xml.sax.SAXException;

import gr.iti.mklab.focused.crawler.bolts.media.MediaItemDeserializationBolt;
import gr.iti.mklab.focused.crawler.bolts.media.MediaTextIndexerBolt;
import gr.iti.mklab.focused.crawler.bolts.media.MediaUpdaterBolt;
import gr.iti.mklab.focused.crawler.bolts.media.RedisBolt;
import gr.iti.mklab.focused.crawler.bolts.media.StatusCheckBolt;
import gr.iti.mklab.focused.crawler.bolts.media.VisualClustererBolt;
import gr.iti.mklab.focused.crawler.bolts.media.VisualIndexerBolt;
import gr.iti.mklab.focused.crawler.bolts.webpages.ArticleExtractionBolt;
import gr.iti.mklab.focused.crawler.bolts.webpages.MediaExtractionBolt;
import gr.iti.mklab.focused.crawler.bolts.webpages.TextIndexerBolt;
import gr.iti.mklab.focused.crawler.bolts.webpages.URLExpansionBolt;
import gr.iti.mklab.focused.crawler.bolts.webpages.WebPageDeserializationBolt;
import gr.iti.mklab.focused.crawler.config.CrawlerConfiguration;
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

/**
 *	@author Manos Schinas - manosetro@iti.gr
 *
 *	Entry class for MKLAB focused crawling.  
 *  This class defines a storm-based pipeline (topology) for the processing of 
 *  items, MediaItemsa and WebPages.
 *  
 *  For more information on Storm distributed processing check this tutorial:
 *  https://github.com/nathanmarz/storm/wiki/Tutorial
 *  
 */
public class Crawler {

	private static Logger logger = Logger.getLogger(SocialsensorCrawler.class);
	
	public static void main(String[] args) throws UnknownHostException {
		
		CrawlerConfiguration config;
		try {
			if(args.length == 1) {
				config = CrawlerConfiguration.readFromFile(new File(args[0]));
			}
			else {
				config = CrawlerConfiguration.readFromFile(new File("./conf/socialsensor.crawler.xml"));
			}
		}
		catch (ParserConfigurationException e) {
			logger.error(e);
			return;
		} catch (SAXException e) {
			logger.error(e);
			return;
		} catch (IOException e) {
			logger.error(e);
			return;
		}
		
		StormTopology topology;
		try {
			topology = createTopology(config);
		} catch (Exception e) {
			logger.error("Topology Creation failed: ", e);
			return;
		}
		
        // Run topology
        String name = "Crawler";
        boolean local = Boolean.parseBoolean(config.getParameter("topology.local"));
        
        Config conf = new Config();
        conf.setDebug(false);
        
        if(!local) {
        	logger.info("Submit topology to Storm cluster");
			try {
				int workers = Integer.parseInt(config.getParameter("topology.workers"));
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
			try {
				LocalCluster cluster = new LocalCluster();
				cluster.submitTopology(name, conf, topology);
			}
			catch(Exception e) {
				logger.error(e);
			}
		}
	}
	
	public static StormTopology createTopology(CrawlerConfiguration config) throws Exception {
		
		// Get Params from config file
		
		// Redis
		String redisHost = config.getParameter("redis.hostname");
		String webPagesChannel = config.getParameter("redis.webPagesChannel");
		String mediaItemsChannel = config.getParameter("redis.mediaItemsChannel");
		
		// MongoDB
		String mongodbHostname = config.getParameter("mongodb.hostname");
		String mediaItemsDB = config.getParameter("mongodb.mediaItemsDB");
		String streamUsersDB = config.getParameter("mongodb.streamUsersDB");
		
		// Visual Index
		String visualIndexHostname = config.getParameter("visualindex.hostname");
		String visualIndexCollection = config.getParameter("visualindex.collection");
		
		String learningFiles = config.getParameter("visualindex.learningfiles");
		if(!learningFiles.endsWith("/"))
			learningFiles = learningFiles + "/";
		
		String[] codebookFiles = { 
				learningFiles + "surf_l2_128c_0.csv",
				learningFiles + "surf_l2_128c_1.csv", 
				learningFiles + "surf_l2_128c_2.csv",
				learningFiles + "surf_l2_128c_3.csv" };
		
		String pcaFile = learningFiles + "pca_surf_4x128_32768to1024.txt";
		
		// Solr Index
		String textIndexHostname = config.getParameter("textindex.hostname");
		String textIndexCollection = config.getParameter("textindex.collections.webpages");
		String textIndexService = textIndexHostname + "/" + textIndexCollection;
		
		String mediaTextIndexCollection = config.getParameter("textindex.collections.media");
		String mediaTextIndexService = textIndexHostname + "/" + mediaTextIndexCollection;
		
		// Initialize spouts and bolts
		BaseRichSpout wpSpout; 
		BaseRichSpout miSpout;
		IRichBolt wpDeserializer;
		IRichBolt miDeserializer;
		IRichBolt urlExpander, articleExtraction, mediaExtraction, textIndexer;
		IRichBolt miStatusChecker, visualIndexer, mediaTextIndexer, clusterer, mediaUpdater;
		IRichBolt redisBolt;
		
		wpSpout = new RedisSpout(redisHost, webPagesChannel, "url");
		miSpout = new RedisSpout(redisHost, mediaItemsChannel, "id");
			
		wpDeserializer = new WebPageDeserializationBolt(webPagesChannel);
		miDeserializer = new MediaItemDeserializationBolt(mediaItemsChannel);
		
		// Web Pages Bolts
		urlExpander = new URLExpansionBolt(webPagesChannel);
		articleExtraction = new ArticleExtractionBolt(24);
		mediaExtraction = new MediaExtractionBolt();
		textIndexer = new TextIndexerBolt(textIndexService);
			
		// Media Items Bolts
		miStatusChecker = new StatusCheckBolt(redisHost);
		visualIndexer = new VisualIndexerBolt(visualIndexHostname, visualIndexCollection, codebookFiles, pcaFile);
		mediaUpdater = new MediaUpdaterBolt(mongodbHostname, mediaItemsDB, streamUsersDB);
		mediaTextIndexer = new MediaTextIndexerBolt(mediaTextIndexService);	
		clusterer = new VisualClustererBolt(redisHost, mediaTextIndexService);
		redisBolt = new RedisBolt(redisHost, "mediaIds");
		
		// Create topology 
		TopologyBuilder builder = new TopologyBuilder();
		
		// Input in topology
		builder.setSpout("wpSpout", wpSpout, 1);
		builder.setSpout("miSpout", miSpout, 1);
		
		// Web Pages Bolts
		builder.setBolt("wpDeserializer", wpDeserializer, 2).shuffleGrouping("wpSpout");
		builder.setBolt("expander", urlExpander, 8).shuffleGrouping("wpDeserializer");
		builder.setBolt("articleExtraction", articleExtraction, 1).shuffleGrouping("expander", "webpage");
		builder.setBolt("mediaExtraction", mediaExtraction, 1).shuffleGrouping("expander", "media");
		builder.setBolt("textIndexer", textIndexer, 1).shuffleGrouping("articleExtraction", "webpage");

		// Media Items Bolts
		builder.setBolt("miDeserializer", miDeserializer, 2).shuffleGrouping("miSpout");
		builder.setBolt("miStatusChecker", miStatusChecker, 1)
			.shuffleGrouping("miDeserializer")
			.shuffleGrouping("articleExtraction", "media")
			.shuffleGrouping("mediaExtraction", "media");
	
        builder.setBolt("vIndexer", visualIndexer, 16).shuffleGrouping("miStatusChecker");
		builder.setBolt("mediaTextIndexer", mediaTextIndexer, 1).shuffleGrouping("vIndexer");
        builder.setBolt("mediaupdater", mediaUpdater, 1).shuffleGrouping("vIndexer");
        builder.setBolt("clusterer", clusterer, 1).shuffleGrouping("vIndexer");
        builder.setBolt("redisBolt", redisBolt, 1).shuffleGrouping("vIndexer");
        
		StormTopology topology = builder.createTopology();
		return topology;
		
	}
	
}