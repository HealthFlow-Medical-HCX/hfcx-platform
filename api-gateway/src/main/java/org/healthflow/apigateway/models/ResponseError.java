package org.healthflow.apigateway.models;

import org.healthflow.apigateway.exception.ErrorCodes;
import lombok.Data;

@Data
public class ResponseError {

    private final ErrorCodes code;
    private final String message;
    private final Throwable trace;

}
