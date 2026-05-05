package org.healthflow.hcx.handlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.healthflow.auditindexer.function.AuditIndexer;
import org.healthflow.common.dto.Request;
import org.healthflow.common.helpers.EventGenerator;
import org.healthflow.common.utils.Constants;
import org.healthflow.common.utils.JSONUtils;
import org.healthflow.common.utils.PayloadUtils;
import org.healthflow.kafka.client.IEventService;
import org.healthflow.postgresql.IDatabaseService;

import java.util.Map;

import static org.healthflow.common.utils.Constants.KAFKA_TOPIC_PAYLOAD;
import static org.healthflow.common.utils.Constants.QUEUED_STATUS;

@Component
public class EventHandler {

    private static final Logger logger = LoggerFactory.getLogger(EventHandler.class);

    @Autowired
    protected Environment env;

    @Autowired
    private IEventService kafkaClient;

    @Autowired
    protected IDatabaseService postgreSQLClient;

    @Autowired
    protected AuditIndexer auditIndexer;

    @Autowired
    protected EventGenerator eventGenerator;

    @Value("${postgres.tablename}")
    private String postgresTableName;

    @Value("${kafka.topic.audit}")
    private String auditTopic;

    public void processAndSendEvent(String metadataTopic, Request request) throws Exception {
        String payloadTopic = env.getProperty(KAFKA_TOPIC_PAYLOAD);
        String key = request.getHcxSenderCode();
        // TODO: check and remove writing payload event to kafka
        String payloadEvent = eventGenerator.generatePayloadEvent(request);
        String metadataEvent = eventGenerator.generateMetadataEvent(request);
        String query = "INSERT INTO " + postgresTableName
                + " (mid,data,action,status,retrycount,lastupdatedon) VALUES (?,?,?,?,?,?)";
        logger.debug("Mid: " + request.getMid() + " :: Event: " + metadataEvent);
        postgreSQLClient.execute(query,
                request.getMid(),
                JSONUtils.serialize(PayloadUtils.removeParticipantDetails(request.getPayload())),
                request.getApiAction(),
                QUEUED_STATUS,
                0,
                System.currentTimeMillis());
        kafkaClient.send(payloadTopic, key, payloadEvent);
        kafkaClient.send(metadataTopic, key, metadataEvent);
        auditIndexer.createDocument(eventGenerator.generateAuditEvent(request));
    }

    public void createAudit(Map<String,Object> event) throws Exception {
        kafkaClient.send(auditTopic , (String) ((Map<String,Object>) event.get(Constants.OBJECT)).get(Constants.TYPE) , JSONUtils.serialize(event));
    }

}
