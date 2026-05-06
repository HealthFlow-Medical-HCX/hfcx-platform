package org.healthflow.common.utils;

import java.util.Set;

/**
 * Allow-list for SQL table names.
 *
 * <p>JDBC {@link java.sql.PreparedStatement} cannot parameterize identifiers (table or
 * column names), so any code path that string-concatenates a table name into a SQL
 * statement MUST first run that name through {@link #validate(String)} to ensure the
 * value originated from configuration we control rather than from an external input.
 *
 * <p>The set below is the union of every table name actually referenced by call
 * sites that build SQL with a substituted table identifier. New tables must be added
 * here before the corresponding code change merges; otherwise the validator will
 * throw at runtime.
 */
public final class TableNames {

    private TableNames() {
    }

    private static final Set<String> ALLOWED = Set.of(
            "payload",
            "payload_audit",
            "search",
            "subscription",
            "notification",
            "composite_search",
            "onboarding",
            "onboarding_otp"
    );

    /**
     * Returns the supplied name unchanged if it is on the allow-list; otherwise
     * throws {@link IllegalArgumentException}. Callers should pass the returned value
     * straight into the SQL string (it is identical to the input on success, but
     * routing through the validator documents the safety check at the call site).
     *
     * @param name table identifier sourced from a configuration property
     * @return the same {@code name} that was passed in
     * @throws IllegalArgumentException if {@code name} is null or not allow-listed
     */
    public static String validate(String name) {
        if (name == null || !ALLOWED.contains(name)) {
            throw new IllegalArgumentException("Table name not allow-listed: " + name);
        }
        return name;
    }
}
