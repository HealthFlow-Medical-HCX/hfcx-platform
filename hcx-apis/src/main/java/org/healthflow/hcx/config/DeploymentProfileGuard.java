package org.healthflow.hcx.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Fails fast if the active Spring profile and the JWE feature flag disagree.
 *
 * <p>Per Decision 14 (zero-knowledge transport), the gateway must NEVER decrypt
 * JWE payloads. This guard is the loud-failure mechanism: if
 * {@code deployment.role=gateway} and any of the decryption-side feature flags
 * are on, the application logs ERROR and aborts at
 * {@link ApplicationReadyEvent}. If {@code deployment.role=recipient-hcx-api}
 * and the flags are off, the same applies — there's no point in deploying a
 * recipient that doesn't decrypt.
 *
 * <p>The guard runs once on startup, after the Spring context is fully wired,
 * so all property resolution and profile selection has occurred. Throwing an
 * {@link IllegalStateException} from the listener bubbles up and prevents the
 * application from completing startup; Spring Boot exits with a non-zero code.
 *
 * <p>Sourced from Gap N1 of the v1.3 remediation plan.
 */
@Component
public class DeploymentProfileGuard {

    private static final Logger logger = LoggerFactory.getLogger(DeploymentProfileGuard.class);

    @Value("${deployment.role:UNSET}")
    private String role;

    @Value("${crypto.jwe.enabled:false}")
    private boolean jweEnabled;

    @Value("${fhir.validation.enabled:false}")
    private boolean fhirEnabled;

    @Value("${egyptian.validation.enabled:false}")
    private boolean egyptianEnabled;

    @EventListener(ApplicationReadyEvent.class)
    public void verify() {
        if ("UNSET".equals(role)) {
            logger.error("deployment.role is not set. Activate either the 'gateway' or "
                    + "'recipient-hcx-api' Spring profile (SPRING_PROFILES_ACTIVE).");
            throw new IllegalStateException("deployment.role unset; refusing to start");
        }
        if ("gateway".equals(role) && (jweEnabled || fhirEnabled || egyptianEnabled)) {
            logger.error("Gateway deployment must not decrypt or validate FHIR. "
                    + "crypto.jwe.enabled={}, fhir.validation.enabled={}, egyptian.validation.enabled={}. "
                    + "This violates Decision 14 (zero-knowledge transport).",
                    jweEnabled, fhirEnabled, egyptianEnabled);
            throw new IllegalStateException(
                    "Gateway profile cannot run with JWE/FHIR/Egyptian validation enabled");
        }
        if ("recipient-hcx-api".equals(role) && !(jweEnabled && fhirEnabled && egyptianEnabled)) {
            logger.error("Recipient HCX-API must decrypt and validate. "
                    + "crypto.jwe.enabled={}, fhir.validation.enabled={}, egyptian.validation.enabled={}.",
                    jweEnabled, fhirEnabled, egyptianEnabled);
            throw new IllegalStateException(
                    "Recipient HCX-API profile cannot run with JWE/FHIR/Egyptian validation disabled");
        }
        logger.info("DeploymentProfileGuard OK — role={}, JWE={}, FHIR={}, Egyptian={}",
                role, jweEnabled, fhirEnabled, egyptianEnabled);
    }
}
