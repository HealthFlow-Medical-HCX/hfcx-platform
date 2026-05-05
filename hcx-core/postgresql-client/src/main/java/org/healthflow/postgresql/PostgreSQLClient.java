package org.healthflow.postgresql;

import org.healthflow.common.exception.ClientException;

import java.sql.*;

public class PostgreSQLClient implements IDatabaseService {

    private final String url;
    private final String user;
    private final String password;
    private final Connection connection;
    private final Statement statement;

    public PostgreSQLClient(String url, String user, String password) throws ClientException, SQLException {
        this.url = url;
        this.user = user;
        this.password = password;
        this.connection = getConnection();
        this.statement = this.connection.createStatement();
    }

    public Connection getConnection() throws ClientException {
        Connection conn;
        try {
            conn = DriverManager.getConnection(url, user, password);
        } catch (Exception e) {
            throw new ClientException("Error connecting to the PostgreSQL server: " + e.getMessage());
        }
        return conn;
    }

    public void close() throws SQLException {
        statement.close();
        connection.close();
    }

    /**
     * @deprecated SQL injection risk: prefer {@link #execute(String, Object...)} which
     * uses {@link PreparedStatement} parameter binding. Retained for callers that still
     * build queries via String concatenation. Slated for removal once all call sites
     * are migrated. See P0-3 in the production-readiness remediation plan.
     */
    @Deprecated
    public boolean execute(String query) throws ClientException {
        try {
            return statement.execute(query);
        } catch (Exception e) {
            throw new ClientException("Error while performing database operation: " + e.getMessage());
        }
    }

    /**
     * @deprecated SQL injection risk: prefer {@link #executeQuery(String, Object...)}.
     * See P0-3 in the production-readiness remediation plan.
     */
    @Deprecated
    public ResultSet executeQuery(String query) throws ClientException {
        try {
            return statement.executeQuery(query);
        } catch (Exception e) {
            throw new ClientException("Error while performing database operation: " + e.getMessage());
        }
    }

    /**
     * Execute a parameterized DML statement (INSERT / UPDATE / DELETE) via
     * {@link PreparedStatement}. Use this whenever any value flows from external input.
     * Table names cannot be parameterized in JDBC — they must be string-concatenated
     * from a hardcoded config value, never from request input.
     *
     * @param sql    SQL with {@code ?} positional placeholders
     * @param params values to bind, in placeholder order; may be empty
     * @return result of {@link PreparedStatement#execute()}
     */
    public boolean execute(String sql, Object... params) throws ClientException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bindParams(ps, params);
            return ps.execute();
        } catch (SQLException e) {
            throw new ClientException("Error while performing parameterized database operation: " + e.getMessage());
        }
    }

    /**
     * Execute a parameterized SELECT via {@link PreparedStatement} and return its
     * {@link ResultSet}.
     *
     * <p>Resource management note: the {@code PreparedStatement} backing the returned
     * {@code ResultSet} is intentionally not closed by this method (the caller still
     * needs to read from it). Callers should close <em>both</em> via
     * {@code try (Statement st = rs.getStatement(); ResultSet ignored = rs)} or by
     * calling {@code rs.getStatement().close()} explicitly after consuming the rows.
     * This matches the existing single-Statement pattern's lifecycle expectations and
     * is a candidate for a future close-handling refactor.
     *
     * @param sql    SQL with {@code ?} positional placeholders
     * @param params values to bind, in placeholder order; may be empty
     */
    public ResultSet executeQuery(String sql, Object... params) throws ClientException {
        try {
            PreparedStatement ps = connection.prepareStatement(sql);
            bindParams(ps, params);
            return ps.executeQuery();
        } catch (SQLException e) {
            throw new ClientException("Error while performing parameterized database operation: " + e.getMessage());
        }
    }

    private static void bindParams(PreparedStatement ps, Object[] params) throws SQLException {
        if (params == null) return;
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }

    public void addBatch(String query) throws ClientException {
        try {
             statement.addBatch(query);
        } catch (Exception e) {
            throw new ClientException("Error while performing database operation: " + e.getMessage());
        }
    }

    public int[] executeBatch() throws ClientException {
        try {
            return statement.executeBatch();
        } catch (Exception e) {
            throw new ClientException("Error while performing database operation: " + e.getMessage());
        }
    }

    public boolean isHealthy() {
        try {
            Connection conn = getConnection();
            conn.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

}
