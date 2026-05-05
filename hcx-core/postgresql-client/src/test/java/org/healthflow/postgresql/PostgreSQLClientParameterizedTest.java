package org.healthflow.postgresql;

import org.healthflow.common.exception.ClientException;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for the parameterized {@code execute(String, Object...)} and
 * {@code executeQuery(String, Object...)} overloads added in P0-3.
 *
 * <p>Uses Mockito rather than an embedded Postgres so the test runs without a JDBC
 * server. The contract being verified is the parameter-binding behaviour, not the
 * database round-trip — that is exercised by the existing call-site tests.
 */
public class PostgreSQLClientParameterizedTest {

    private Connection mockConnection;
    private PreparedStatement mockPreparedStatement;
    private PostgreSQLClient client;

    @Before
    public void setUp() throws Exception {
        // Construct a real PostgreSQLClient by hijacking the connection field via a subclass.
        // The constructor opens a connection so we use a no-arg subclass that injects mocks.
        mockConnection = mock(Connection.class);
        mockPreparedStatement = mock(PreparedStatement.class);
        Statement mockStatement = mock(Statement.class);
        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockConnection.prepareStatement(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(mockPreparedStatement);

        client = new PostgreSQLClient("jdbc:postgresql://stub", "stub", "stub") {
            @Override
            public Connection getConnection() {
                return mockConnection;
            }
        };
        // Manual override since the parent constructor already ran with a real getConnection() call.
        // For the tests below we re-route by reflection.
        java.lang.reflect.Field connField = PostgreSQLClient.class.getDeclaredField("connection");
        connField.setAccessible(true);
        connField.set(client, mockConnection);
    }

    @Test
    public void executeBindsAllPositionalParameters() throws Exception {
        when(mockPreparedStatement.execute()).thenReturn(true);

        boolean ok = client.execute(
                "INSERT INTO subscription (subscription_id, sender_code) VALUES (?, ?)",
                "sub-123", "sender-abc");

        assertTrue(ok);
        verify(mockConnection).prepareStatement(
                "INSERT INTO subscription (subscription_id, sender_code) VALUES (?, ?)");
        verify(mockPreparedStatement).setObject(1, "sub-123");
        verify(mockPreparedStatement).setObject(2, "sender-abc");
        verify(mockPreparedStatement).execute();
        verify(mockPreparedStatement, atLeastOnce()).close();
    }

    @Test
    public void executeQueryReturnsResultSetAndDoesNotCloseTheStatement() throws Exception {
        ResultSet mockResultSet = mock(ResultSet.class);
        when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);

        ResultSet rs = client.executeQuery(
                "SELECT status FROM onboarding WHERE applicant_email = ?", "test@example.com");

        assertNotNull(rs);
        assertEquals(mockResultSet, rs);
        verify(mockConnection).prepareStatement(
                "SELECT status FROM onboarding WHERE applicant_email = ?");
        verify(mockPreparedStatement).setObject(1, "test@example.com");
        verify(mockPreparedStatement).executeQuery();
        // Critical: do NOT close the statement here — the caller still needs the ResultSet.
        verify(mockPreparedStatement, never()).close();
    }

    @Test
    public void executeWithNoParametersStillBindsCorrectlyAndDoesNotInjectAny() throws Exception {
        when(mockPreparedStatement.execute()).thenReturn(true);

        boolean ok = client.execute("SELECT 1");

        assertTrue(ok);
        verify(mockConnection).prepareStatement("SELECT 1");
        verify(mockPreparedStatement, never()).setObject(anyInt(), org.mockito.ArgumentMatchers.any());
        verify(mockPreparedStatement).execute();
    }

    @Test
    public void executeBindsMixedTypeParameters() throws Exception {
        when(mockPreparedStatement.execute()).thenReturn(true);

        long ts = 1700000000000L;
        boolean active = true;
        client.execute(
                "UPDATE payload SET retrycount = ?, lastupdatedon = ?, active = ? WHERE mid = ?",
                3, ts, active, "mid-xyz");

        verify(mockPreparedStatement).setObject(1, 3);
        verify(mockPreparedStatement).setObject(2, ts);
        verify(mockPreparedStatement).setObject(3, active);
        verify(mockPreparedStatement).setObject(4, "mid-xyz");
    }

    @Test
    public void executeWrapsSqlExceptionAsClientException() throws Exception {
        when(mockPreparedStatement.execute()).thenThrow(new SQLException("syntax error at or near \"FRO\""));

        try {
            client.execute("SELCT * FRO bad", "x");
            fail("expected ClientException");
        } catch (ClientException e) {
            assertTrue(e.getMessage().contains("syntax error"));
        }
    }

    @Test
    public void executeQueryWrapsSqlExceptionAsClientException() throws Exception {
        when(mockPreparedStatement.executeQuery()).thenThrow(new SQLException("relation does not exist"));

        try {
            client.executeQuery("SELECT * FROM no_such_table WHERE id = ?", 42);
            fail("expected ClientException");
        } catch (ClientException e) {
            assertTrue(e.getMessage().contains("relation does not exist"));
        }
    }

    @Test
    public void parameterValuesContainingSqlMetacharactersAreNotInjected() throws Exception {
        when(mockPreparedStatement.execute()).thenReturn(true);

        // The classic injection probe — should be bound as a single literal string,
        // never reinterpreted as additional SQL.
        String evil = "'; DROP TABLE payload; --";
        client.execute(
                "DELETE FROM onboarding WHERE applicant_email = ?", evil);

        verify(mockPreparedStatement).setObject(1, evil);
        // The SQL handed to prepareStatement contains only the parameterized template,
        // never the malicious value:
        verify(mockConnection).prepareStatement(
                eq("DELETE FROM onboarding WHERE applicant_email = ?"));
        verify(mockConnection, times(1)).prepareStatement(org.mockito.ArgumentMatchers.anyString());
    }
}
