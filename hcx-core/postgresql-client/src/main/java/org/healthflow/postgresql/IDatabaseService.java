package org.healthflow.postgresql;

import java.sql.ResultSet;

public interface IDatabaseService {

    Object executeQuery(String query) throws Exception;
    boolean execute(String query) throws Exception;
    boolean isHealthy();
    Object getConnection() throws Exception;
    void close() throws Exception;
    void addBatch(String query) throws Exception;
    int[] executeBatch() throws Exception;

    /**
     * Parameter-bound INSERT/UPDATE/DELETE. Use {@code ?} placeholders in the SQL
     * and pass values as varargs. Implementations must use {@link java.sql.PreparedStatement}
     * so values are not subject to SQL injection.
     */
    default boolean execute(String sql, Object... params) throws Exception {
        throw new UnsupportedOperationException("Parameter-bound execute() is not implemented for this IDatabaseService.");
    }

    /**
     * Parameter-bound SELECT. Use {@code ?} placeholders in the SQL and pass values
     * as varargs. Caller is responsible for closing the returned ResultSet AND its
     * underlying Statement (the latter via {@code rs.getStatement().close()}).
     */
    default ResultSet executeQuery(String sql, Object... params) throws Exception {
        throw new UnsupportedOperationException("Parameter-bound executeQuery() is not implemented for this IDatabaseService.");
    }
}
