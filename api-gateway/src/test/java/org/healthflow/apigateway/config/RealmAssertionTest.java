package org.healthflow.apigateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link RealmAssertion}.
 *
 * <p>The four cases mirror the production decision matrix: realm/JWK match,
 * mismatch under strict mode (throw), mismatch under non-strict mode (warn
 * and continue), and the empty-config skip path.
 */
class RealmAssertionTest {

    private RealmAssertion guard(String configuredRealm, String jwkUrl, boolean strict) {
        RealmAssertion g = new RealmAssertion();
        ReflectionTestUtils.setField(g, "configuredRealm", configuredRealm);
        ReflectionTestUtils.setField(g, "jwkUrl", jwkUrl);
        ReflectionTestUtils.setField(g, "strict", strict);
        return g;
    }

    @Test
    void realmMatchesJwkUrl_startsCleanly() {
        assertDoesNotThrow(() -> guard("hcx-egypt",
                "http://kc.example/auth/realms/hcx-egypt/protocol/openid-connect/certs",
                true).verify(),
                "Configured realm matching the JWK URL should start cleanly");
    }

    @Test
    void realmMismatch_strict_throws() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> guard("healthflow-hcx-egypt",
                        "http://kc.example/auth/realms/hcx-egypt/protocol/openid-connect/certs",
                        true).verify(),
                "Strict mode must throw on realm mismatch");
        assertTrue(e.getMessage().toLowerCase().contains("realm mismatch"),
                "Error message should describe the mismatch; got: " + e.getMessage());
        assertTrue(e.getMessage().contains("healthflow-hcx-egypt")
                        && e.getMessage().contains("hcx-egypt"),
                "Error message should name both realms; got: " + e.getMessage());
    }

    @Test
    void realmMismatch_notStrict_warnsAndContinues() {
        assertDoesNotThrow(() -> guard("healthflow-hcx-egypt",
                "http://kc.example/auth/realms/hcx-egypt/protocol/openid-connect/certs",
                false).verify(),
                "Non-strict mode must log WARN and continue; no exception");
    }

    @Test
    void emptyJwkUrl_skipsAssertion() {
        // Local dev where the JWK URL has not been wired to a real Keycloak
        // yet — the guard should not block startup.
        assertDoesNotThrow(() -> guard("hcx-egypt", "", true).verify());
    }

    @Test
    void malformedJwkUrl_skipsAssertion() {
        // No /realms/ segment; the guard cannot extract the realm to compare
        // against. Skip rather than throw — the user has clearly broken
        // their JWK URL and other code will surface that.
        assertDoesNotThrow(() -> guard("hcx-egypt",
                "http://not-a-keycloak-url.example/jwks.json", true).verify());
    }
}
