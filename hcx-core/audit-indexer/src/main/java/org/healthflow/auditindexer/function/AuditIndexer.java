package org.healthflow.auditindexer.function;

import com.fasterxml.jackson.databind.JsonNode;
import org.healthflow.auditindexer.utils.ElasticSearchUtil;
import org.healthflow.common.utils.JSONUtils;

import java.io.InputStream;
import java.util.*;
import java.util.logging.Logger;

public class AuditIndexer {

    private static final Logger logger = Logger.getLogger(AuditIndexer.class.getName());
    private final String auditIndex;
    private final String auditAlias;
    private final ElasticSearchUtil esUtil;
    private final String mappings;
    private final String settings;

    public AuditIndexer(String esHost, int esPort, String auditIndex, String auditAlias) throws Exception {
        mappings = JSONUtils.convertJson(getStream("audit-mappings.json"), JsonNode.class).toString();
        settings = JSONUtils.convertJson(getStream("es-settings.json"), JsonNode.class).toString();
        this.auditIndex = auditIndex;
        this.auditAlias = auditAlias;
        esUtil = new ElasticSearchUtil(esHost, esPort);
    }

    public void createDocument(Map<String, Object> event) throws Exception {
        try {
            String indexName = getIndexName((Long) event.get("ets"));
            String mid = (String) event.get("mid");
            esUtil.addIndex(settings, mappings, indexName, auditAlias);
            esUtil.addDocumentWithIndex(JSONUtils.serialize(event), indexName, mid);
            logger.info("Audit document created for mid: " + mid);
        } catch (Exception e) {
            throw new Exception("Error while processing event :: " + event + " :: " + e.getMessage());
        }
    }

    private String getIndexName(long ets){
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("IST"));
        cal.setTime(new Date(ets));
        return auditIndex + "_" + cal.get(Calendar.YEAR) + "_" + cal.get(Calendar.WEEK_OF_YEAR);
    }

    private InputStream getStream(String filename){
        return getClass().getClassLoader().getResourceAsStream(filename);
    }

    /**
     * Deletes every audit document referencing the given participant — both as
     * sender and as recipient. Used by the right-to-erasure admin endpoint
     * (P0-8) so the audit-index scrub completes immediately rather than relying
     * solely on the ILM policy's eventual age-out.
     *
     * <p>Hits the audit alias (which spans every weekly index since onboarding),
     * not a single index, so a participant whose history reaches back years is
     * fully scrubbed in one call.
     *
     * @param participantCode the HCX participant code being erased
     * @return total documents deleted (sender-side + recipient-side)
     */
    public long deleteByParticipantCode(String participantCode) throws Exception {
        if (participantCode == null || participantCode.trim().isEmpty()) return 0L;
        long sent = esUtil.deleteByQuery(auditAlias, "sender_code", participantCode);
        long received = esUtil.deleteByQuery(auditAlias, "recipient_code", participantCode);
        long total = sent + received;
        logger.info("Audit erasure for participant " + participantCode
                + ": deleted " + sent + " as sender + " + received + " as recipient = "
                + total + " total");
        return total;
    }

}
