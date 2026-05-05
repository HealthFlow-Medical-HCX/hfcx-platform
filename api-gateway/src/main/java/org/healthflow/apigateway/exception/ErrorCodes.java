package org.healthflow.apigateway.exception;

/**
 * Internal error codes raised by the gateway. Each value carries the public
 * {@code ERR-X-NNN} code from §24.6 of the HealthFlow Integration Guide so the
 * outward-facing error response payload can include both the internal name
 * (stable across the codebase) and the public code (stable for integrators).
 *
 * <p>Public-code taxonomy:
 * <ul>
 *   <li><b>ERR-P-xxx</b> — Protocol / validation errors (header, signature, payload).</li>
 *   <li><b>ERR-B-xxx</b> — Business-logic errors (claim/coverage/policy). Owned by
 *       downstream services; gateway does not raise these.</li>
 *   <li><b>ERR-T-xxx</b> — Technical / infrastructure errors.</li>
 * </ul>
 *
 * <p>If you add a new enum value, you MUST assign a public code (or pass {@code null}
 * to opt out — but then the integrator cannot distinguish your error). The integration
 * guide owns the public taxonomy; coordinate before allocating a new ERR-X-NNN slot.
 */
public enum ErrorCodes {
    ERR_MANDATORY_HEADERFIELD_MISSING("ERR-P-001"),
    ERR_INVALID_TIMESTAMP            ("ERR-P-002"),
    ERR_INVALID_PAYLOAD              ("ERR-P-003"),
    ERR_INVALID_API_CALL_ID          ("ERR-P-004"),
    ERR_INVALID_CORRELATION_ID       ("ERR-P-005"),
    ERR_INVALID_SENDER               ("ERR-P-006"),
    ERR_INVALID_RECIPIENT            ("ERR-P-007"),
    ERR_INVALID_SENDER_AND_RECIPIENT ("ERR-P-008"),
    ERR_ACCESS_DENIED                ("ERR-P-009"),
    ERR_INVALID_FORWARD_REQ          ("ERR-P-010"),
    ERR_INVALID_REDIRECT_TO          ("ERR-P-011"),
    ERR_INVALID_SIGNATURE            ("ERR-P-012"),
    ERR_INVALID_WORKFLOW_ID          ("ERR-P-013"),
    ERR_INVALID_DEBUG_FLAG           ("ERR-P-014"),
    ERR_INVALID_STATUS               ("ERR-P-015"),
    ERR_INVALID_ERROR_DETAILS        ("ERR-P-016"),
    ERR_INVALID_DEBUG_DETAILS        ("ERR-P-017"),
    ERR_INVALID_ALGORITHM            ("ERR-P-018"),
    ERR_INVALID_NOTIFICATION_TOPIC_CODE   ("ERR-P-019"),
    ERR_INVALID_NOTIFICATION_REQ          ("ERR-P-020"),
    ERR_INVALID_NOTIFICATION_HEADERS      ("ERR-P-021"),
    ERR_INVALID_NOTIFICATION_RECIPIENT_TYPE("ERR-P-022"),
    ERR_INVALID_NOTIFICATION_TIMESTAMP    ("ERR-P-023"),
    ERR_INVALID_NOTIFICATION_EXPIRY       ("ERR-P-024"),
    ERR_INVALID_NOTIFICATION_MESSAGE      ("ERR-P-025"),
    ERR_INVALID_NOTIFICATION_RECIPIENTS   ("ERR-P-026"),

    INTERNAL_SERVER_ERROR ("ERR-T-001"),
    SERVICE_UNAVAILABLE   ("ERR-T-002");

    private final String publicCode;

    ErrorCodes(String publicCode) {
        this.publicCode = publicCode;
    }

    /** The integrator-facing {@code ERR-P/B/T-xxx} code, or {@code null} if unmapped. */
    public String getPublicCode() {
        return publicCode;
    }
}
