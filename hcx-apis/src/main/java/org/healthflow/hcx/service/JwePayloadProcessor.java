package org.healthflow.hcx.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import org.healthflow.common.crypto.JWEHelper;
import org.healthflow.common.crypto.KeyCustodyClient;
import org.healthflow.common.exception.ClientException;
import org.healthflow.common.exception.ErrorCodes;
import org.healthflow.common.fhir.FhirValidationService;
import org.healthflow.hcx.utils.validators.EgyptianFieldValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Inbound-side JWE handling for the HCX-API recipient.
 *
 * <p>Per Decision 14 (zero-knowledge transport), the gateway must never
 * decrypt JWE payloads — that responsibility lives at the recipient
 * HCX-API instance. This service is the single place where decryption,
 * FHIR validation, and Egyptian field validation are wired into the
 * inbound protocol path; {@link org.healthflow.hcx.controllers.BaseController}
 * delegates to it.
 *
 * <p>Each step is independently feature-flagged so a misconfigured
 * deployment can be rolled back at runtime:
 * <ul>
 *   <li>{@code crypto.jwe.enabled} — gates the JWE decrypt step. With the
 *       flag off, the request is passed through unchanged (legacy behaviour
 *       and the test profile use this).</li>
 *   <li>{@code fhir.validation.enabled} — gates the HAPI-FHIR validate step
 *       (delegated to {@link FhirValidationService}, which has its own
 *       internal flag of the same name).</li>
 *   <li>{@code egyptian.validation.enabled} — gates the Egyptian field
 *       validators on the decrypted Bundle.</li>
 * </ul>
 *
 * <p>Order matters: decrypt → FHIR validation → Egyptian validation.
 * The reasons:
 *   1. Validation needs the plaintext, so it can't run before decrypt.
 *   2. FHIR validation enforces the HealthFlow Egyptian IG profiles, which
 *      include structural constraints (e.g. National-ID slice presence).
 *      Running FHIR before the field-level Egyptian validator is intentional:
 *      if the resource isn't shaped right, the field-level error message
 *      would be a confusing follow-on. FHIR errors take priority.
 */
/*
 * Defense in depth (Gap N1 v1.3): the bean is only instantiated when
 * {@code crypto.jwe.enabled=true}. On the gateway profile (where the flag
 * is pinned to false), the bean does not exist at all — BaseController's
 * {@code @Autowired(required = false)} field stays null and the inbound
 * payload is forwarded opaque. DeploymentProfileGuard is the loud check;
 * this is the silent backstop.
 */
@Service
@ConditionalOnProperty(name = "crypto.jwe.enabled", havingValue = "true")
public class JwePayloadProcessor {

    private static final Logger logger = LoggerFactory.getLogger(JwePayloadProcessor.class);

    private final KeyCustodyClient keyCustodyClient;
    private final FhirValidationService fhirValidationService;
    private final ObjectMapper jackson;

    @Value("${crypto.jwe.enabled:true}")
    private boolean jweEnabled;

    @Value("${egyptian.validation.enabled:true}")
    private boolean egyptianValidationEnabled;

    public JwePayloadProcessor(KeyCustodyClient keyCustodyClient,
                               FhirValidationService fhirValidationService) {
        this.keyCustodyClient = keyCustodyClient;
        this.fhirValidationService = fhirValidationService;
        this.jackson = new ObjectMapper();
    }

    /** Visible for tests. */
    public boolean isJweEnabled() {
        return jweEnabled;
    }

    /**
     * Process an inbound request body in place.
     *
     * <p>If {@code crypto.jwe.enabled=true} and the body contains a string
     * {@code payload} with the shape of a JWE compact serialization
     * (5 base64url segments separated by dots), the segment is decrypted
     * and the plaintext FHIR Bundle JSON replaces {@code payload} in
     * {@code requestBody}.
     *
     * <p>The FHIR Bundle is then handed to the FHIR validator (if enabled)
     * and the Egyptian field validator (if enabled). Validation failures
     * raise {@link ClientException} with {@link ErrorCodes#ERR_INVALID_PAYLOAD}.
     *
     * <p>Bodies that don't carry a JWE-shaped payload (e.g. plain protocol
     * headers, status requests) are passed through unchanged. The decision
     * is shape-based, not action-based, so adding new endpoints does not
     * require updating this class.
     */
    public void process(Map<String, Object> requestBody) throws ClientException {
        if (requestBody == null) {
            return;
        }
        String decryptedJson = maybeDecryptInPlace(requestBody);
        if (decryptedJson == null) {
            // Either JWE flag is off, or the request shape didn't carry a JWE.
            // Nothing to validate at the FHIR/Egyptian layer.
            return;
        }
        runFhirValidation(decryptedJson);
        runEgyptianValidation(decryptedJson);
    }

    private String maybeDecryptInPlace(Map<String, Object> requestBody) throws ClientException {
        if (!jweEnabled) {
            return null;
        }
        Object rawPayload = requestBody.get("payload");
        if (!(rawPayload instanceof String)) {
            return null;
        }
        String compact = (String) rawPayload;
        if (!looksLikeJweCompact(compact)) {
            // Notification, status, plaintext-protocol requests do not carry a JWE.
            return null;
        }
        try {
            String plaintext = JWEHelper.decrypt(compact, keyCustodyClient.getLocalPrivateKey());
            // Replace the payload field with the decrypted FHIR Bundle JSON so
            // downstream handlers see the cleartext. This matches §27.5 of the
            // Integration Guide: the recipient layer presents plaintext to its
            // own business logic.
            requestBody.put("payload", plaintext);
            return plaintext;
        } catch (JOSEException e) {
            logger.warn("JWE decrypt failed: {}", e.getMessage());
            throw new ClientException(ErrorCodes.ERR_INVALID_PAYLOAD,
                    "Invalid JWE payload: " + e.getMessage());
        } catch (ClientException e) {
            throw e;
        } catch (Exception e) {
            logger.warn("Unexpected error fetching local private key for JWE decrypt: {}", e.getMessage());
            throw new ClientException(ErrorCodes.ERR_INVALID_PAYLOAD,
                    "Unable to decrypt payload: " + e.getMessage());
        }
    }

    private void runFhirValidation(String decryptedJson) throws ClientException {
        if (fhirValidationService == null || !fhirValidationService.isEnabled()) {
            return;
        }
        FhirValidationService.FhirValidationOutcome outcome = fhirValidationService.validate(decryptedJson);
        if (outcome.isFailure()) {
            String detail = String.join("; ", outcome.getIssues());
            throw new ClientException(ErrorCodes.ERR_INVALID_PAYLOAD,
                    "FHIR validation failed: " + detail);
        }
    }

    private void runEgyptianValidation(String decryptedJson) throws ClientException {
        if (!egyptianValidationEnabled) {
            return;
        }
        try {
            JsonNode bundle = jackson.readTree(decryptedJson);
            List<String> errors = EgyptianFieldValidator.validateFhirBundle(bundle);
            if (!errors.isEmpty()) {
                throw new ClientException(ErrorCodes.ERR_INVALID_PAYLOAD,
                        "Egyptian field validation failed: " + String.join("; ", errors));
            }
        } catch (ClientException e) {
            throw e;
        } catch (Exception e) {
            logger.warn("Egyptian validator could not parse decrypted bundle as JSON: {}",
                    e.getMessage());
            // Don't fail the request because the Egyptian validator hit a parse error;
            // FHIR validation already ran above and would have caught structural problems.
        }
    }

    /**
     * A JWE compact serialization is exactly five base64url-encoded segments
     * separated by dots. Distinguishes a JWE blob from a plain JSON string.
     */
    static boolean looksLikeJweCompact(String s) {
        if (s == null || s.isEmpty()) return false;
        int dots = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '.') dots++;
            if (dots > 4) return false;
        }
        return dots == 4;
    }
}
