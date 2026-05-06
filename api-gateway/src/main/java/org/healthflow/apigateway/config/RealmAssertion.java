package org.healthflow.apigateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Verifies at startup that the configured Keycloak realm name matches the
 * realm the JWK URL actually points at.
 *
 * <p>Catches the v1.4 Gap V3 mismatch (configured {@code healthflow-hcx-egypt},
 * actual {@code hcx-egypt}) at boot rather than at the first authenticated
 * request.
 *
 * <p>The assertion is best-effort: this listener does not call out to
 * Keycloak; it only compares the static {@code keycloak.realm} property to
 * the realm slug extracted from {@code jwt.jwkUrl}. Both are local
 * configuration values, so the check runs offline and is fast.
 *
 * <p>Severity is gated by {@code keycloak.realm-assert-strict}:
 * <ul>
 *   <li>{@code true} (recommended for prod-like profiles): mismatch throws
 *       {@link IllegalStateException} and aborts startup.</li>
 *   <li>{@code false} (default): mismatch logs WARN and continues — useful
 *       for local development where the JWK URL may point at a stub.</li>
 * </ul>
 *
 * <p>Sourced from Gap V3 of the v1.4 corrective sprint.
 */
@Component
public class RealmAssertion {

    private static final Logger logger = LoggerFactory.getLogger(RealmAssertion.class);

    @Value("${keycloak.realm:}")
    private String configuredRealm;

    @Value("${jwt.jwkUrl:}")
    private String jwkUrl;

    @Value("${keycloak.realm-assert-strict:false}")
    private boolean strict;

    @EventListener(ApplicationReadyEvent.class)
    public void verify() {
        if (configuredRealm == null || configuredRealm.isEmpty()
                || jwkUrl == null || jwkUrl.isEmpty()) {
            logger.warn("Skipping realm assertion: keycloak.realm or jwt.jwkUrl is empty");
            return;
        }
        // The JWK URL is of the form .../auth/realms/{realm}/protocol/openid-connect/certs
        // Extract the {realm} segment and compare.
        String marker = "/realms/";
        int start = jwkUrl.indexOf(marker);
        if (start < 0) {
            logger.warn("Skipping realm assertion: jwt.jwkUrl does not contain '/realms/'");
            return;
        }
        int from = start + marker.length();
        int to = jwkUrl.indexOf("/", from);
        if (to < 0) {
            logger.warn("Skipping realm assertion: jwt.jwkUrl is malformed");
            return;
        }
        String realmInUrl = jwkUrl.substring(from, to);
        if (!configuredRealm.equals(realmInUrl)) {
            String msg = String.format(
                    "Keycloak realm mismatch: keycloak.realm=%s but jwt.jwkUrl points at realm %s. "
                            + "Authenticated requests will fail. (Gap V3 v1.4 prevents this from "
                            + "shipping silently.)",
                    configuredRealm, realmInUrl);
            if (strict) {
                logger.error(msg);
                throw new IllegalStateException(msg);
            } else {
                logger.warn(msg);
            }
        } else {
            logger.info("RealmAssertion OK — configured realm '{}' matches JWK URL", configuredRealm);
        }
    }
}
