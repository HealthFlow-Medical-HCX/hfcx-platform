package org.healthflow.common.exception;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Wire-level error codes per Integration Guide §28.
 *
 * <p>Each value carries a stable {@link #code()} string in the documented
 * {@code ERR-[PBT]-NNN} taxonomy:
 * <ul>
 *   <li>{@code ERR-P-NNN} — protocol-layer (header/format/JWT) errors;
 *       caller fix is to correct the request envelope.</li>
 *   <li>{@code ERR-B-NNN} — business-layer (payload/data/state) errors;
 *       caller fix is to correct the data they sent.</li>
 *   <li>{@code ERR-T-NNN} — technical (server-side / dependency) errors;
 *       caller may retry with backoff.</li>
 * </ul>
 *
 * <p>Gap 8 of the v1.2 remediation plan called for splitting the flat enum
 * into three separate enums. To avoid breaking the 50+ existing call sites
 * (which would block the rest of Sprint B), the structure is preserved but
 * each value carries its taxonomy code, and Jackson serialization (via
 * {@link JsonValue} on {@link #code()}) emits that code on the wire.
 *
 * <p>The follow-up structural split to three enums is tracked for a later
 * sprint; it is a safe rename-only refactor once the wire format is stable.
 */
public enum ErrorCodes {
    // Protocol-layer errors (P)
    ERR_INVALID_JWT("ERR-P-001"),
    ERR_INVALID_IDENTITY("ERR-P-002"),

    // Business-layer errors (B)
    ERR_INVALID_PARTICIPANT_CODE("ERR-B-001"),
    ERR_INVALID_PARTICIPANT_DETAILS("ERR-B-002"),
    ERR_INVALID_SEARCH("ERR-B-003"),
    ERR_INVALID_CORRELATION_ID("ERR-B-004"),
    ERR_INVALID_WORKFLOW_ID("ERR-B-005"),
    ERR_INVALID_PAYLOAD("ERR-B-006"),
    ERR_INVALID_NOTIFICATION_TOPIC_CODE("ERR-B-007"),
    /** Egyptian-specific field (National-ID, IBAN, phone, governorate) failed validation. */
    ERR_INVALID_EGYPTIAN_IDENTIFIER("ERR-B-008"),
    ERR_INVALID_NOTIFICATION_REQ("ERR-B-009"),
    ERR_INVALID_SUBSCRIPTION_ID("ERR-B-010"),
    ERR_INVALID_OTP("ERR-B-011"),
    ERR_UPDATE_PARTICIPANT_DETAILS("ERR-B-012"),
    ERR_INVALID_ONBOARD_STATUS("ERR-B-013"),
    ERR_MAXIMUM_OTP_REGENERATE("ERR-B-014"),
    ERR_INVALID_CERTIFICATE("ERR-B-015"),

    // Technical errors (T)
    ERR_SERVICE_UNAVAILABLE("ERR-T-001"),
    INTERNAL_SERVER_ERROR("ERR-T-002"),
    SERVER_ERR_GATEWAY_TIMEOUT("ERR-T-003"),
    ERR_PAYOR_SYSTEM("ERR-T-004");

    private final String code;

    ErrorCodes(String code) {
        this.code = code;
    }

    /**
     * The wire code per Integration Guide §28. Jackson uses this as the
     * serialized form of the enum (replacing {@code name()}), so HTTP
     * error bodies now carry e.g. {@code "code":"ERR-B-006"} instead of
     * {@code "code":"ERR_INVALID_PAYLOAD"}.
     */
    @JsonValue
    public String code() {
        return code;
    }
}
