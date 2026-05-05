package org.healthflow.apigateway.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.healthflow.apigateway.exception.ErrorCodes;

/**
 * Error envelope on the response wire. Carries both the internal
 * {@link ErrorCodes} enum value and the integrator-facing {@code ERR-P/B/T-xxx}
 * public code from §24.6 of the HealthFlow Integration Guide. The public code
 * is populated automatically from {@link ErrorCodes#getPublicCode()}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class ResponseError {

    private final ErrorCodes code;
    private final String message;
    private final Throwable trace;

    @JsonProperty("public_code")
    private final String publicCode;

    public ResponseError(ErrorCodes code, String message, Throwable trace) {
        this.code = code;
        this.message = message;
        this.trace = trace;
        this.publicCode = (code != null) ? code.getPublicCode() : null;
    }
}
