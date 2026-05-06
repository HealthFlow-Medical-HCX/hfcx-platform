package org.healthflow.dp.notification.functions;

import org.apache.commons.lang3.StringUtils;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.healthflow.dp.core.function.DispatcherResult;
import org.healthflow.dp.core.service.AuditService;
import org.healthflow.dp.core.util.Constants;
import org.healthflow.dp.core.util.DispatcherUtil;
import org.healthflow.dp.core.util.JSONUtil;
import org.swasth.dp.core.util.TableNames;
import org.healthflow.dp.notification.task.NotificationConfig;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SubscriptionDispatcherFunction extends BaseNotificationFunction {

    private final Logger logger = LoggerFactory.getLogger(SubscriptionDispatcherFunction.class);

    public SubscriptionDispatcherFunction(NotificationConfig config) {
        super(config);
    }

    @Override
    public void processElement(Map<String, Object> inputEvent, ProcessFunction<Map<String, Object>, Map<String, Object>>.Context context, Collector<Map<String, Object>> collector) throws Exception {
        int successfulDispatches = 0;
        int failedDispatches = 0;
        Map<String, Object> contextData = (Map) inputEvent.get(Constants.CDATA());
        Map<String, Object> recipientContextData = (Map) contextData.get(Constants.RECIPIENT());
        Map<String, Object> payloadMap = (Map) inputEvent.get(Constants.PAYLOAD());
        DispatcherResult result = dispatcherUtil.dispatch(recipientContextData, JSONUtil.serialize(payloadMap));
        if(result.success()) successfulDispatches++; else failedDispatches++;
        int totalDispatches = successfulDispatches+failedDispatches;
        System.out.println("Total number of subscriptions dispatched: " + totalDispatches + " :: successful dispatches: " + successfulDispatches + " :: failed dispatches: " + failedDispatches);
        logger.info("Total number of subscriptions dispatched: " + totalDispatches + " :: successful dispatches: " + successfulDispatches + " :: failed dispatches: " + failedDispatches);
        String action = (String) inputEvent.get(Constants.ACTION());
        // for /notification/on_subscribe
        if (action.equalsIgnoreCase(Constants.NOTIFICATION_ONSUBSCRIBE())) {
            String subscriptionId = (String) payloadMap.get(Constants.SUBSCRIPTION_ID());
            //Check for any subscriptions which are still in Pending state based on subscriptionId
            boolean hasPendingResponses = hasPendingResponses(subscriptionId);
            // set the status to pending and if all subscription responses were received then send complete
            String hcxStatus = hasPendingResponses ? Constants.PARTIAL_RESPONSE() : Constants.COMPLETE_RESPONSE();
            //Create audit event
            auditService.indexAudit(createOnSubscriptionAuditEvent(inputEvent, createErrorMap(result.error() != null ? result.error().get() : null),hcxStatus));
        } else {
            //TODO Create audit event for subscribe and unsubscribe post dispatch with status as request.dispatched along with subscription_status and subscription_request_id
            //auditService.indexAudit(createSubscriptionAuditEvent(action,));
        }
    }

    private boolean hasPendingResponses(String subscriptionId) throws SQLException {
        // Allow-list the table name; bind subscriptionId via the PreparedStatement.
        String table = TableNames.validate(config.subscriptionTableName);
        String query = "SELECT count(" + Constants.SUBSCRIPTION_REQUEST_ID() + ")"
                + " FROM " + table
                + " WHERE " + Constants.SUBSCRIPTION_REQUEST_ID() + " IN ("
                + " SELECT DISTINCT " + Constants.SUBSCRIPTION_REQUEST_ID()
                + " FROM " + table
                + " WHERE " + Constants.SUBSCRIPTION_ID() + " = ?)"
                + " AND " + Constants.SUBSCRIPTION_STATUS() + " = 'Pending'";
        try (PreparedStatement ps = postgresConnect.getConnection().prepareStatement(query)) {
            ps.setString(1, subscriptionId);
            try (ResultSet resultSet = ps.executeQuery()) {
                while (resultSet.next()) {
                    int pendingCount = resultSet.getInt("count");
                    if (pendingCount > 0)
                        return true;
                }
            }
        }
        return false;
    }

}
