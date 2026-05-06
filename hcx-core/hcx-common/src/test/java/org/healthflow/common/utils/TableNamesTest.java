package org.healthflow.common.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TableNamesTest {

    @Test
    public void allowsKnownTableNames() {
        assertEquals("payload", TableNames.validate("payload"));
        assertEquals("payload_audit", TableNames.validate("payload_audit"));
        assertEquals("search", TableNames.validate("search"));
        assertEquals("subscription", TableNames.validate("subscription"));
        assertEquals("notification", TableNames.validate("notification"));
        assertEquals("composite_search", TableNames.validate("composite_search"));
        assertEquals("onboarding", TableNames.validate("onboarding"));
        assertEquals("onboarding_otp", TableNames.validate("onboarding_otp"));
    }

    @Test
    public void rejectsUnknownTableName() {
        try {
            TableNames.validate("evil_table; DROP TABLE payload;--");
            fail("Expected IllegalArgumentException for unknown table name");
        } catch (IllegalArgumentException expected) {
            // pass
        }
    }

    @Test
    public void rejectsNullTableName() {
        try {
            TableNames.validate(null);
            fail("Expected IllegalArgumentException for null table name");
        } catch (IllegalArgumentException expected) {
            // pass
        }
    }

    @Test
    public void rejectsEmptyTableName() {
        try {
            TableNames.validate("");
            fail("Expected IllegalArgumentException for empty table name");
        } catch (IllegalArgumentException expected) {
            // pass
        }
    }
}
