package org.healthflow.commonscheduler.schedulers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.healthflow.common.dto.Request;
import org.healthflow.common.helpers.EventGenerator;
import org.healthflow.common.utils.Constants;
import org.healthflow.common.utils.JSONUtils;
import org.healthflow.common.utils.TableNames;
import org.healthflow.kafka.client.KafkaClient;
import org.healthflow.postgresql.PostgreSQLClient;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.healthflow.common.utils.Constants.*;

@Component
public class RetryScheduler extends BaseScheduler {

    private final Logger logger = LoggerFactory.getLogger(RetryScheduler.class);

    @Value("${max.retry}")
    private int maxRetry;

    @Value("${kafka.topic.output}")
    private String kafkaOutputTopic;

    @Value("${postgres.tablename}")
    private String postgresTableName;

    @Scheduled(fixedDelayString = "${fixedDelay.in.milliseconds.retry}")
    public void process() throws Exception {

        System.out.println("Retry batch job is started");
        ResultSet result = null;
        Connection connection = postgreSQLClient.getConnection();
        // Validate the table name once via the allow-list (JDBC cannot bind
        // identifiers); all data values flow through PreparedStatement params.
        String table = TableNames.validate(postgresTableName);
        String selectQuery = "SELECT * FROM " + table
                + " WHERE status = ? AND retryCount <= ?";
        String updateQuery = "UPDATE " + table
                + " SET status = ?, retryCount = ?, lastUpdatedOn = ? WHERE mid = ?";
        PreparedStatement updatePs = connection.prepareStatement(updateQuery);
        try (PreparedStatement selectPs = connection.prepareStatement(selectQuery)) {
            selectPs.setString(1, Constants.RETRY_STATUS);
            selectPs.setInt(2, maxRetry);
            result = selectPs.executeQuery();
            int metrics = 0;
            while (result.next()) {
                String action = result.getString(Constants.ACTION);
                Request request = new Request(JSONUtils.deserialize(result.getString("data"), Map.class), action);
                request.setMid(result.getString(Constants.MID));
                request.setApiAction(action);
                int retryCount = result.getInt(Constants.RETRY_COUNT) + 1 ;
                String event = eventGenerator.generateMetadataEvent(request);
                Map<String,Object> eventMap = JSONUtils.deserialize(event, Map.class);
                eventMap.put(Constants.RETRY_INDEX, retryCount);
                kafkaClient.send(kafkaOutputTopic, request.getHcxSenderCode(), JSONUtils.serialize(eventMap));
                System.out.println("Event is pushed to kafka topic :: mid: " + request.getMid() + " :: retry count: " + retryCount);
                updatePs.setString(1, Constants.RETRY_PROCESSING_STATUS);
                updatePs.setInt(2, retryCount);
                updatePs.setLong(3, System.currentTimeMillis());
                updatePs.setString(4, request.getMid());
                updatePs.addBatch();
                metrics++;
            }
            updatePs.executeBatch();
            System.out.println("Total number of events processed: " + metrics);
            System.out.println("Job is completed");
        } catch (Exception e) {
            System.out.println("Error while processing event: " + e.getMessage());
            throw e;
        } finally {
            if(result != null) result.close();
            updatePs.close();
            connection.close();
        }
    }

}
