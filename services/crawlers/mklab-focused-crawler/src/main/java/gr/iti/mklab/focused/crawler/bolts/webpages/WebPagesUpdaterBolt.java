package gr.iti.mklab.focused.crawler.bolts.webpages;

import gr.iti.mklab.framework.client.mongo.DAOFactory;
import gr.iti.mklab.framework.common.domain.WebPage;

import java.util.Map;

import org.apache.log4j.Logger;
import org.mongodb.morphia.dao.BasicDAO;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.tuple.Tuple;

public class WebPagesUpdaterBolt extends BaseRichBolt {

    /**
	 * 
	 */
	private static final long serialVersionUID = -2548434425109192911L;
	
	private Logger logger;
	
	private String mongodbHostname;
	private String webPagesDB;
	
	private BasicDAO<WebPage, String> _webPageDAO = null;
	
	private long received = 0, newWP = 0, existedWP = 0;
	
	public WebPagesUpdaterBolt(String mongodbHostname, String webPagesDB) {
		this.mongodbHostname = mongodbHostname;
		
		this.webPagesDB = webPagesDB;
	}
	
    public void declareOutputFields(OutputFieldsDeclarer declarer) {

    }

	public void prepare(@SuppressWarnings("rawtypes") Map conf, TopologyContext context, 
			OutputCollector collector) {
		
		logger = Logger.getLogger(WebPagesUpdaterBolt.class);
		try {
			_webPageDAO = new DAOFactory().getDAO(mongodbHostname, webPagesDB, WebPage.class);
		} catch (Exception e) {
			logger.error(e);
		}
		
	}

	public void execute(Tuple tuple) {
		try {
			if(++received%1000==0) {
				logger.info(received + " web pages received. New: " + newWP + 
						" Exists: " + existedWP);
				
			}
			
			WebPage webPage = (WebPage) tuple.getValueByField("WebPage");
		
			if(webPage == null || _webPageDAO == null)
				return;
				
			Query<WebPage> query = _webPageDAO.createQuery();
			query.filter("url", webPage.getUrl());
			
			if(_webPageDAO.exists(query)) {
				existedWP++;
				
				// Update existing web page
				
				UpdateOperations<WebPage> ops = _webPageDAO.createUpdateOperations();
				ops.set("isArticle", webPage.isArticle());
				ops.set("text", webPage.getText());
				ops.set("domain", webPage.getDomain());
				ops.set("expandedUrl", webPage.getExpandedUrl());
				ops.set("mediaThumbnai.", webPage.getMediaThumbnail());
				
				_webPageDAO.update(query, ops);

			}
			else {
				newWP++;
				// Insert new web page (this should never happen in production)
				_webPageDAO.save(webPage);
			}
		
		}
		catch(Exception ex) {
			logger.error(ex);
		}
		
	}
 
}