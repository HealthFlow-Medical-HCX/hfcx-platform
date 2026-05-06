package org.healthflow.dp.notification.functions;


import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.healthflow.dp.core.util.Constants;
import org.swasth.dp.core.util.TableNames;
import org.healthflow.dp.notification.task.NotificationConfig;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class NotificationFilterFunction extends BaseNotificationFunction {

    private final Logger logger = LoggerFactory.getLogger(NotificationFilterFunction.class);
    private Map<String,Object> consolidatedEvent;

    public NotificationFilterFunction(NotificationConfig config) {
        super(config);
    }

    @Override
    public void processElement(Map<String,Object> inputEvent, ProcessFunction<Map<String,Object>, Map<String,Object>>.Context context, Collector<Map<String,Object>> collector) throws Exception {
        consolidatedEvent = new HashMap<>();
        System.out.println("Event: " + inputEvent);
        logger.debug("Event: " + inputEvent);
        String topicCode = (String) inputEvent.get(Constants.TOPIC_CODE());
        Map<String,Object> notificationHeaders = getProtocolMapValue(Constants.NOTIFICATION_HEADERS(), inputEvent);
        String senderCode = (String) notificationHeaders.get(Constants.SENDER_CODE());
        Map<String, Object> notification = notificationUtil.getNotification(topicCode);
        System.out.println("Notification Master data template: " + notification);
        logger.debug("Notification Master data template: " + notification);
        consolidatedEvent.put(Constants.MASTER_DATA(), notification);
        consolidatedEvent.put(Constants.INPUT_EVENT(), inputEvent);
        List<String> participantCodes;
        List<String> recipients = (List<String>) notificationHeaders.get(Constants.RECIPIENTS());
        String recipientType = (String) notificationHeaders.get(Constants.RECIPIENT_TYPE());
        if (recipientType.equalsIgnoreCase(Constants.SUBSCRIPTION())) {
            participantCodes = getParticipantCodes(topicCode, senderCode, Constants.SUBSCRIPTION_ID(), recipients);
        } else {
            if (recipientType.equalsIgnoreCase(Constants.PARTICIPANT_ROLE())) {
                // fetching participants based on the master data allowed recipient roles
                List<Map<String, Object>> fetchParticipants = registryService.getParticipantDetails("{\"roles\":{\"or\":[" + addQuotes(recipients) + "]}}");
                recipients = fetchParticipants.stream().map(obj -> (String) obj.get(Constants.PARTICIPANT_CODE())).collect(Collectors.toList());
            }
            if(notification.get(Constants.CATEGORY()).equals(Constants.NETWORK())) {
                participantCodes = recipients;
            } else {
                // check if recipients have any active subscription
                participantCodes = getParticipantCodes(topicCode, senderCode, Constants.RECIPIENT_CODE(), recipients);;
            }
        }
        List<Map<String, Object>> participantDetails = registryService.getParticipantDetails("{\"participant_code\":{\"or\":[" + addQuotes(participantCodes) + "]}}");
        System.out.println("Total number of participants: " + participantDetails.size());
        logger.debug("Total number of participants: " + participantDetails.size());
        consolidatedEvent.put(Constants.PARTICIPANT_DETAILS(), participantDetails);
        context.output(config.dispatcherOutputTag(), consolidatedEvent);
    }

    private List<String> getParticipantCodes(String topicCode, String senderCode, String id, List<String> recipients) throws SQLException {
        List<String> participantCodes = new ArrayList<>();
        // Bind every data value via PreparedStatement; only the table name is
        // string-substituted (which is unavoidable in JDBC) and is run through
        // the allow-list validator first. The IN-list builds N "?" placeholders
        // sized to the recipient list and binds each value positionally.
        String inPlaceholders = recipients.stream().map(r -> "?").collect(Collectors.joining(","));
        String query = "SELECT " + Constants.RECIPIENT_CODE() + "," + Constants.EXPIRY()
                + " FROM " + TableNames.validate(config.subscriptionTableName)
                + " WHERE " + Constants.SENDER_CODE() + " = ?"
                + " AND " + Constants.TOPIC_CODE() + " = ?"
                + " AND " + Constants.SUBSCRIPTION_STATUS() + " = 'Active'"
                + " AND " + id + " IN (" + inPlaceholders + ")";
        try (PreparedStatement ps = postgresConnect.getConnection().prepareStatement(query)) {
            ps.setString(1, senderCode);
            ps.setString(2, topicCode);
            for (int i = 0; i < recipients.size(); i++) {
                ps.setString(3 + i, recipients.get(i));
            }
            try (ResultSet resultSet = ps.executeQuery()) {
                while (resultSet.next()) {
                    if (!isExpired(resultSet.getLong(Constants.EXPIRY())))
                        participantCodes.add(resultSet.getString(Constants.RECIPIENT_CODE()));
                }
            }
        }
        return  participantCodes;
    }
}
