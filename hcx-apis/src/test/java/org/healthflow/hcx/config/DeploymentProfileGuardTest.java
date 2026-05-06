package org.healthflow.hcx.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link DeploymentProfileGuard}.
 *
 * <p>Each test sets the four guarded properties directly via reflection rather
 * than spinning up a Spring context — the guard's branching logic is the
 * contract under test, not the wiring.
 */
class DeploymentProfileGuardTest {

    private DeploymentProfileGuard guard(String role, boolean jwe, boolean fhir, boolean egyptian) {
        DeploymentProfileGuard g = new DeploymentProfileGuard();
        ReflectionTestUtils.setField(g, "role", role);
        ReflectionTestUtils.setField(g, "jweEnabled", jwe);
        ReflectionTestUtils.setField(g, "fhirEnabled", fhir);
        ReflectionTestUtils.setField(g, "egyptianEnabled", egyptian);
        return g;
    }

    @Test
    void gatewayWithFlagsOffStartsCleanly() {
        assertDoesNotThrow(() -> guard("gateway", false, false, false).verify(),
                "Gateway profile with all flags off should start cleanly");
    }

    @Test
    void gatewayWithJweOnRefusesToStart() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> guard("gateway", true, false, false).verify(),
                "Gateway with crypto.jwe.enabled=true must violate Decision 14");
        assertTrue(e.getMessage().toLowerCase().contains("gateway"),
                "Error message should mention the gateway role");
    }

    @Test
    void gatewayWithFhirOnRefusesToStart() {
        assertThrows(IllegalStateException.class,
                () -> guard("gateway", false, true, false).verify());
    }

    @Test
    void gatewayWithEgyptianOnRefusesToStart() {
        assertThrows(IllegalStateException.class,
                () -> guard("gateway", false, false, true).verify());
    }

    @Test
    void recipientHcxApiWithAllFlagsOnStartsCleanly() {
        assertDoesNotThrow(() -> guard("recipient-hcx-api", true, true, true).verify(),
                "Recipient HCX-API with all flags on should start cleanly");
    }

    @Test
    void recipientHcxApiWithFlagOffRefusesToStart() {
        assertThrows(IllegalStateException.class,
                () -> guard("recipient-hcx-api", true, true, false).verify(),
                "Recipient HCX-API must have all three flags on");
    }

    @Test
    void unsetRoleRefusesToStart() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> guard("UNSET", false, false, false).verify());
        assertTrue(e.getMessage().toLowerCase().contains("unset"),
                "Error message should explain the role is unset");
    }
}
