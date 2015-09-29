package gr.iti.mklab.focused.crawler.bolts.media;

import gr.iti.mklab.framework.client.mongo.DAOFactory;
import gr.iti.mklab.framework.common.domain.Concept;
import gr.iti.mklab.framework.common.domain.MediaItem;
import gr.iti.mklab.framework.common.domain.StreamUser;

import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.mongodb.morphia.dao.BasicDAO;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;

public class MediaUpdaterBolt extends BaseRichBolt {

    /**
	 * 
	 */
	private static final long serialVersionUID = -2548434425109192911L;
	
	private Logger _logger;
	
	private String _mongodbHostname;
	private String _mediaItemsDB;
	private String _streamUsersDB;
	
	private BasicDAO<MediaItem, String> _mediaItemDAO;
	private BasicDAO<StreamUser, String> _streamUsersDAO;
	//private OutputCollector _collector;

	private long received = 0;
	private long newMedia=0, existedMedia = 0;
	
	public MediaUpdaterBolt(String mongodbHostname, String mediaItemsDB, String streamUsersDB) {
		
		_mongodbHostname = mongodbHostname;
		_mediaItemsDB = mediaItemsDB;
		_streamUsersDB = streamUsersDB;
	}
	
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
    	declarer.declare(new Fields("MediaItem"));
    }

	public void prepare(@SuppressWarnings("rawtypes") Map conf, TopologyContext context, 
			OutputCollector collector) {
		
		_logger = Logger.getLogger(MediaUpdaterBolt.class);
		try {
			DAOFactory daoFactory = new DAOFactory();
			
			_mediaItemDAO = daoFactory.getDAO(_mongodbHostname, _mediaItemsDB, MediaItem.class);
			_streamUsersDAO = daoFactory.getDAO(_mongodbHostname, _streamUsersDB, StreamUser.class);
			
			//_collector = collector;
		} catch (Exception e) {
			_logger.error(e);
		}
		
	}

	public void execute(Tuple tuple) {
		if(_mediaItemDAO != null) {
			try {
				
			if(++received%1000==0) {
				_logger.info(received + " media items received. " + newMedia + " are new and " + existedMedia + " already exists!");
			}
				
			MediaItem mediaItem = (MediaItem) tuple.getValueByField("MediaItem");
				if(mediaItem == null)
					return;
			
				String mId = mediaItem.getId();
				
				Query<MediaItem> query = _mediaItemDAO.createQuery();
				query.filter("id", mId);
				
				if(_mediaItemDAO.exists(query)) {
				
					existedMedia++;
					
					UpdateOperations<MediaItem> ops = _mediaItemDAO.createUpdateOperations();
				
					Integer width = mediaItem.getWidth();
					Integer height = mediaItem.getHeight();
					if(width != null && height != null && width != -1 && height != -1) {
						ops.set("height", height);
						ops.set("width", width);
					}
				
					List<Concept> concepts = mediaItem.getConcepts();
					if(concepts != null) {
						ops.set("concepts", concepts);
					}
					
					String clusterId = mediaItem.getClusterId();
					if(clusterId != null) {
						ops.set("clusterId", clusterId);
					}
					
					_mediaItemDAO.update(query, ops);
				}
				else {
					newMedia++;
					_mediaItemDAO.save(mediaItem);
					
					StreamUser user = mediaItem.getUser();
					if(user != null && _streamUsersDAO != null) {
						user.setLastUpdated(System.currentTimeMillis());
						
						Query<StreamUser> q = _streamUsersDAO.createQuery().filter("id", user.getId());
						if(!_streamUsersDAO.exists(q)) {
							_streamUsersDAO.save(user);
						}
					}
				}
				
			}
			catch(Exception e) {
				_logger.error(e);
			}
		}
	}   
	
}