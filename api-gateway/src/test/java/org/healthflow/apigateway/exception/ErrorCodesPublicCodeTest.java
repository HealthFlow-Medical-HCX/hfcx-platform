package org.healthflow.apigateway.exception;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Pinned tests for the public ERR-P/B/T-xxx taxonomy mapping. The mapping is the
 * gateway's outward-facing contract; an unintentional rename would silently break
 * every integrator's error-handling. These tests fail loudly on drift.
 */
public class ErrorCodesPublicCodeTest {

    @Test
    public void everyEnumValueHasAPublicCode() {
        for (ErrorCodes c : ErrorCodes.values()) {
            assertNotNull("Missing public code for " + c.name(), c.getPublicCode());
            assertTrue("Public code for " + c.name() + " has wrong shape: " + c.getPublicCode(),
                    c.getPublicCode().matches("^ERR-[PBT]-\\d{3}$"));
        }
    }

    @Test
    public void publicCodesAreUnique() {
        Set<String> seen = new HashSet<>();
        for (ErrorCodes c : ErrorCodes.values()) {
            assertTrue("Duplicate public code: " + c.getPublicCode() + " on " + c.name(),
                    seen.add(c.getPublicCode()));
        }
    }

    @Test
    public void anchorMappingsAreStable() {
        // Spot-check a few that integrators depend on most. If you change any of
        // these, you are breaking integrators — coordinate with the integration guide.
        assertEquals("ERR-P-001", ErrorCodes.ERR_MANDATORY_HEADERFIELD_MISSING.getPublicCode());
        assertEquals("ERR-P-002", ErrorCodes.ERR_INVALID_TIMESTAMP.getPublicCode());
        assertEquals("ERR-P-003", ErrorCodes.ERR_INVALID_PAYLOAD.getPublicCode());
        assertEquals("ERR-P-009", ErrorCodes.ERR_ACCESS_DENIED.getPublicCode());
        assertEquals("ERR-P-012", ErrorCodes.ERR_INVALID_SIGNATURE.getPublicCode());
        assertEquals("ERR-T-001", ErrorCodes.INTERNAL_SERVER_ERROR.getPublicCode());
        assertEquals("ERR-T-002", ErrorCodes.SERVICE_UNAVAILABLE.getPublicCode());
    }

    @Test
    public void protocolAndTechnicalCodesAreSeparate() {
        for (ErrorCodes c : ErrorCodes.values()) {
            String pc = c.getPublicCode();
            if (c == ErrorCodes.INTERNAL_SERVER_ERROR || c == ErrorCodes.SERVICE_UNAVAILABLE) {
                assertTrue(c.name() + " should be ERR-T-*", pc.startsWith("ERR-T-"));
            } else {
                assertTrue(c.name() + " should be ERR-P-*", pc.startsWith("ERR-P-"));
            }
        }
    }
}
