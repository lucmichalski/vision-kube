package eu.europeana.cloud.service.mcs.messages;

/**
 * Message about removing all {@link eu.europeana.cloud.common.model.Representation representations} from a certain
 * {@link eu.europeana.cloud.common.model.Record record}.
 */
public class RemoveRecordRepresentationsMessage extends AbstractMessage {

    /**
     * Constructs RemoveRecordRepresentationsMessage with given payload
     * 
     * @param payload
     *            message payload
     */
    public RemoveRecordRepresentationsMessage(String payload) {
        super(payload);
    }

}
