package gr.iti.mklab.sfc.input;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.mongodb.morphia.dao.BasicDAO;
import org.mongodb.morphia.query.QueryResults;

import gr.iti.mklab.framework.client.mongo.DAOFactory;
import gr.iti.mklab.framework.common.domain.config.Configuration;
import gr.iti.mklab.framework.common.domain.feeds.Feed;
/**
 * @brief The class responsible for the creation of input feeds from mongodb storage
 * 
 * @author manosetro
 * @email  manosetro@iti.gr
 */
public class FeedsCreator {
	
	public final Logger logger = Logger.getLogger(FeedsCreator.class);
	
	protected static final String SINCE = "since";
	
	protected static final String HOST = "host";
	protected static final String DB = "database";
	
	private String host = null;
	private String db = null;
	
	private BasicDAO<Feed, String> feedsDao;
	
	public FeedsCreator(Configuration config) throws Exception {
		this.host = config.getParameter(HOST);
		this.db = config.getParameter(DB);
		
		DAOFactory daoFactory = new DAOFactory();
		feedsDao = daoFactory.getDAO(host, db, Feed.class);
	}
	
	public Map<String, Set<Feed>> createFeedsPerSource() {
	
		Map<String, Set<Feed>> feedsPerSource = new HashMap<String, Set<Feed>>();
		
		Set<Feed> allFeeds = createFeeds();
		for(Feed feed : allFeeds) {
			String source = feed.getSource();
			Set<Feed> feeds = feedsPerSource.get(source);
			if(feeds == null) {
				feeds = new HashSet<Feed>();
				feedsPerSource.put(source, feeds);
			}	
			feeds.add(feed);
		}
		
		return feedsPerSource;
	}

	public Set<Feed> createFeeds() {
		HashSet<Feed> feedsSet = new HashSet<Feed>();
		
		try {
			QueryResults<Feed> result = feedsDao.find();
			List<Feed> feeds = result.asList();
			
			feedsSet.addAll(feeds);
		}
		catch(Exception e) {
			logger.error(e);
		}

		return feedsSet;
	}
	
}