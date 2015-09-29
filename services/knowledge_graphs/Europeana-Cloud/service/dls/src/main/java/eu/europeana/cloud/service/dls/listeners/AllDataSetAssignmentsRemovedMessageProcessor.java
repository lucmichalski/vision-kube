package eu.europeana.cloud.service.dls.listeners;

import com.google.gson.Gson;
import eu.europeana.cloud.common.model.CompoundDataSetId;
import eu.europeana.cloud.service.dls.solr.SolrDAO;
import eu.europeana.cloud.service.mcs.messages.RemoveAssignmentsFromDataSetMessage;
import java.io.IOException;
import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.SolrServerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Processor that processes messages about removing all assignments from a
 * certain {@link eu.europeana.cloud.common.model.DataSet data set}.
 * 
 * It process messages with <code>datasets.assignments.deleteAll</code> routing
 * key. Message text should be a {@link CompoundDataSetId} object, serialised to
 * Json.
 * 
 * After processing properly formed message, processor calls
 * {@link eu.europeana.cloud.service.dls.solr.SolrDAO#removeAssignmentFromDataSet(CompoundDataSetId)}
 * so that Solr index is updated (the
 * {@link eu.europeana.cloud.common.model.DataSet data set} will have no
 * {@link eu.europeana.cloud.common.model.Representation representations}
 * assigned in updated index).
 * 
 * If message is malformed, information about error is logged and no call to
 * {@link eu.europeana.cloud.service.dls.solr.SolrDAO} is performed. If call to
 * {@link eu.europeana.cloud.service.dls.solr.SolrDAO} fails, an information is
 * also logged.
 * 
 * Messages for this processor are produced by
 * <code>eu.europeana.cloud.service.mcs.persistent.SolrRepresentationIndexer.removeAssignmentsFromDataSet(CompoundDataSetId)</code>
 * method in MCS.
 */
@Component
public class AllDataSetAssignmentsRemovedMessageProcessor implements
	MessageProcessor<RemoveAssignmentsFromDataSetMessage> {

    private static final Logger LOGGER = LoggerFactory
	    .getLogger(AllDataSetAssignmentsRemovedMessageProcessor.class);

    @Autowired
    SolrDAO solrDAO;

    private final Gson gson = new Gson();

    @Override
    public void processMessage(RemoveAssignmentsFromDataSetMessage message) {
        String messageText = message.getPayload();

        if (messageText == null || messageText.isEmpty()) {
            LOGGER.error("Message has empty body.");
            return;
        }

        CompoundDataSetId compoundDataSetId = gson.fromJson(messageText, CompoundDataSetId.class);
        if (compoundDataSetId == null) {
            LOGGER.error("Required parameter CompoundDataSetId is null.");
            return;
        }

        if (StringUtils.isBlank(compoundDataSetId.getDataSetId())) {
            LOGGER.error("Data set id is empty.");
            return;
        }

        if (StringUtils.isBlank(compoundDataSetId.getDataSetProviderId())) {
            LOGGER.error("Provider id is empty.");
            return;
        }

        try {
            solrDAO.removeAssignmentFromDataSet(compoundDataSetId);
        } catch (IOException | SolrServerException ex) {
            LOGGER.error("Cannot remove assignments from data set in solr.", ex);
        }
    }
}
