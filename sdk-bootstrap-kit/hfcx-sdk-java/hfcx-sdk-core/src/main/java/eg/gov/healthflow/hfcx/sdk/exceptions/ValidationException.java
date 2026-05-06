package eg.gov.healthflow.hfcx.sdk.exceptions;

/**
 * Raised by the Egyptian field validators and the FHIR validation layer when a
 * payload fails validation rules.
 *
 * <p>The full SDK exception hierarchy ({@code ProtocolException},
 * {@code BusinessException}, {@code TechnicalException}) lands in Sprint J6.
 * Until then, this single {@code RuntimeException} subtype is used so the
 * extracted validator code in J1 can compile cleanly without dragging in the
 * platform's {@code org.healthflow.common.exception.*} types. Callers should
 * not depend on this class's identity surviving the J6 redesign.
 */
public class ValidationException extends RuntimeException {

    private final String code;

    public ValidationException(String message) {
        super(message);
        this.code = "ERR-B-006";
    }

    public ValidationException(String code, String message) {
        super(message);
        this.code = code;
    }

    /** The wire-format error code per Integration Guide §28. */
    public String getCode() {
        return code;
    }
}
