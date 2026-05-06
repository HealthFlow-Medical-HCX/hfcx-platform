package org.healthflow.hcx.service;

import org.healthflow.common.dto.NotificationListRequest;
import org.healthflow.common.dto.Response;
import org.healthflow.common.exception.ClientException;
import org.healthflow.common.exception.ErrorCodes;
import org.healthflow.kafka.client.IEventService;
import org.healthflow.postgresql.IDatabaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Integration-style test for the SQL-injection fix in
 * {@link NotificationService#getSubscriptions} (Gap V2 of the v1.4 corrective
 * sprint).
 *
 * <p>The codebase has no embedded Postgres (H2 / Testcontainers /
 * opentable-EmbeddedPostgres are not on the hcx-apis classpath), so this test
 * uses Mockito's {@link ArgumentCaptor} to verify the wire contract directly:
 * the SQL string passed to {@link IDatabaseService#executeQuery(String, Object...)}
 * must contain {@code ?} placeholders, and the user-controlled malicious value
 * must appear in the parameters array — never inlined into the SQL string.
 *
 * <p>This contract is precisely what prevents the injection — a real Postgres
 * driver binding via {@link java.sql.PreparedStatement} would treat the
 * malicious value as data and never execute it as SQL syntax. Capturing the
 * call arguments verifies the same contract without booting a real DB.
 *
 * <p>Three cases:
 * <ol>
 *   <li>Malicious filter value is bound as a parameter, not interpolated.</li>
 *   <li>Legitimate filter value flows through unchanged.</li>
 *   <li>Filter key outside the allow-list is rejected with ClientException
 *       before any SQL is built.</li>
 * </ol>
 */
class NotificationServiceSqlInjectionIT {

    private NotificationService service;
    private IDatabaseService db;
    private final AtomicReference<String> capturedSql = new AtomicReference<>();
    private final List<Object> capturedParams = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        service = new NotificationService();
        db = mock(IDatabaseService.class);
        ResultSet emptyResultSet = mock(ResultSet.class);
        doReturn(false).when(emptyResultSet).next();
        // Capture the SQL string (arg 0) and the varargs params (args 1..N)
        // on every call. Mockito's ArgumentCaptor doesn't compose well with
        // Object... varargs, so we use doAnswer for the capture and return
        // the empty ResultSet for the test path.
        doAnswer(inv -> {
            capturedSql.set(inv.getArgument(0));
            capturedParams.clear();
            Object[] all = inv.getArguments();
            for (int i = 1; i < all.length; i++) capturedParams.add(all[i]);
            return emptyResultSet;
        }).when(db).executeQuery(anyString(), any());

        ReflectionTestUtils.setField(service, "postgreSQLClient", db);
        ReflectionTestUtils.setField(service, "kafkaClient", mock(IEventService.class));
        ReflectionTestUtils.setField(service, "postgresSubscription", "subscription");
        ReflectionTestUtils.setField(service, "allowedSubscriptionFilters",
                List.of("subscription_id", "topic_code", "subscription_status"));
    }

    @Test
    void filterValue_classicSqlInjection_isBoundAsParameter_notInterpolated() throws Exception {
        // The literal injection string the v1.2/Gap-5 grep, the v1.4 review,
        // and the OWASP examples all flag.
        String malicious = "' OR '1'='1";

        NotificationListRequest req = newRequest("R-1", Map.of("topic_code", malicious));
        service.getSubscriptions(req, new Response());

        // 1. The SQL must have a ? placeholder for topic_code, NOT the literal value.
        String sql = capturedSql.get();
        assertTrue(sql.contains("topic_code = ?"),
                "SQL must contain '?' placeholder for topic_code; got: " + sql);
        assertFalse(sql.contains(malicious),
                "Malicious string must NOT appear in the SQL: " + sql);
        assertFalse(sql.contains("' OR '"),
                "No single-quote injection fragment may appear in the SQL: " + sql);

        // 2. The malicious value must be bound as a parameter — recipient_code
        //    first (always added), then topic_code.
        assertEquals(2, capturedParams.size(), "Expected 2 bound params");
        assertEquals("R-1", capturedParams.get(0));
        assertEquals(malicious, capturedParams.get(1));
    }

    @Test
    void filterValue_legitimate_flowsThroughUnchanged() throws Exception {
        NotificationListRequest req = newRequest("R-1", Map.of("topic_code", "TC-001"));
        service.getSubscriptions(req, new Response());

        assertTrue(capturedSql.get().contains("topic_code = ?"));
        assertEquals("R-1", capturedParams.get(0));
        assertEquals("TC-001", capturedParams.get(1));
    }

    @Test
    void filterKey_outsideAllowList_isRejectedBeforeSqlBuild() throws Exception {
        // A malicious caller tries to inject a column name. The allow-list is
        // the first defence; SQL is never built.
        NotificationListRequest req = newRequest("R-1",
                Map.of("DROP_TABLE_subscription_DASH_DASH", "anything"));

        ClientException thrown = assertThrows(ClientException.class,
                () -> service.getSubscriptions(req, new Response()));
        assertEquals(ErrorCodes.ERR_INVALID_NOTIFICATION_REQ, thrown.getErrCode());

        // No DB call must have been made — the method bailed before
        // reaching the SQL-build path.
        verify(db, never()).executeQuery(anyString(), any());
    }

    private static NotificationListRequest newRequest(String recipient, Map<String, Object> filters) {
        Map<String, Object> body = new HashMap<>();
        body.put("filters", filters);
        body.put("recipientCode", recipient);
        NotificationListRequest req = new NotificationListRequest(body);
        // recipientCode setter on the POJO; the constructor uses Constants.FILTERS only
        req.setRecipientCode(recipient);
        return req;
    }
}
