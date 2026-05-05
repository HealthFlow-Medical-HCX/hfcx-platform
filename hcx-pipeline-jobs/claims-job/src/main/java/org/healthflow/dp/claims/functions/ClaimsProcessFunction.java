package org.healthflow.dp.claims.functions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.healthflow.dp.claims.task.ClaimsConfig;
import org.healthflow.dp.core.function.BaseDispatcherFunction;
import org.healthflow.dp.core.function.ErrorResponse;
import org.healthflow.dp.core.function.ValidationResult;
import scala.Option;

import java.util.Map;

public class ClaimsProcessFunction extends BaseDispatcherFunction {

    private Logger logger = LoggerFactory.getLogger(ClaimsProcessFunction.class);
    private ClaimsConfig config;

    /** JWE algorithms locked by Integration Guide §25.2. */
    private static final String EXPECTED_ALG_OAEP_256 = "RSA-OAEP-256";
    private static final String EXPECTED_ALG_OAEP_LEGACY = "RSA-OAEP";
    private static final String EXPECTED_ENC = "A256GCM";

    public ClaimsProcessFunction(ClaimsConfig config) {
        super(config);
        this.config = config;
    }

    /**
     * Structural validation of the inbound claims event.
     *
     * <p>Per Decision 14 (zero-knowledge transport) the platform cannot decrypt
     * the JWE payload, so this method does not validate FHIR profile content —
     * that runs at the recipient HCX-API instance via
     * {@code FhirValidationService.validate()} after JWE decryption (P0-5,
     * gated on the IG package being published — see Decision 15).
     *
     * <p>What this method DOES validate, on the encrypted-but-routed event:
     * <ul>
     *   <li>{@code headers.jose.alg} is RSA-OAEP-256 (or the legacy RSA-OAEP
     *       marker some senders still emit).</li>
     *   <li>{@code headers.jose.enc} is A256GCM.</li>
     * </ul>
     * Anything else is a downgrade attempt and is rejected before the
     * dispatcher forwards the event.
     */
    @Override
    public ValidationResult validate(Map<String, Object> event) {
        if (event == null) {
            return invalid("ERR-P-003", "Event is null");
        }
        Object headersObj = event.get("headers");
        if (!(headersObj instanceof Map)) {
            return invalid("ERR-P-001", "Event missing 'headers' map");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> headers = (Map<String, Object>) headersObj;

        Object joseObj = headers.get("jose");
        if (!(joseObj instanceof Map)) {
            return invalid("ERR-P-001", "Event missing 'headers.jose' map");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> jose = (Map<String, Object>) joseObj;

        String alg = stringOrNull(jose.get("alg"));
        if (!EXPECTED_ALG_OAEP_256.equals(alg) && !EXPECTED_ALG_OAEP_LEGACY.equals(alg)) {
            return invalid("ERR-P-018",
                    "Unsupported JWE alg: " + alg + " (expected " + EXPECTED_ALG_OAEP_256 + ")");
        }

        String enc = stringOrNull(jose.get("enc"));
        if (!EXPECTED_ENC.equals(enc)) {
            return invalid("ERR-P-018",
                    "Unsupported JWE enc: " + enc + " (expected " + EXPECTED_ENC + ")");
        }
        return new ValidationResult(true, Option.<ErrorResponse>empty());
    }

    private static ValidationResult invalid(String code, String message) {
        ErrorResponse err = new ErrorResponse(
                Option.apply(code),
                Option.apply(message),
                Option.<String>empty());
        return new ValidationResult(false, Option.apply(err));
    }

    private static String stringOrNull(Object o) {
        return o == null ? null : o.toString();
    }
}
