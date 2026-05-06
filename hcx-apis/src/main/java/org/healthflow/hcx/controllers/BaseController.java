package org.healthflow.hcx.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.healthflow.auditindexer.function.AuditIndexer;
import org.healthflow.common.dto.Request;
import org.healthflow.common.dto.Response;
import org.healthflow.common.dto.ResponseError;
import org.healthflow.common.exception.*;
import org.healthflow.common.helpers.EventGenerator;
import org.healthflow.hcx.handlers.EventHandler;
import org.healthflow.hcx.service.AuditService;
import org.healthflow.hcx.service.JwePayloadProcessor;

import java.util.Map;

import static org.healthflow.common.utils.Constants.ERROR_STATUS;

public class BaseController {

    @Autowired
    protected Environment env;

    @Autowired
    protected AuditIndexer auditIndexer;

    @Autowired
    protected EventGenerator eventGenerator;

    @Autowired
    protected AuditService auditService;

    @Autowired
    protected EventHandler eventHandler;

    /**
     * Inbound JWE / FHIR / Egyptian-validator pipeline. Optional in tests
     * (legacy WebMvcTest contexts do not load the full Spring config); when
     * absent the controller falls back to the legacy pass-through behaviour.
     */
    @Autowired(required = false)
    protected JwePayloadProcessor jwePayloadProcessor;

    protected Response errorResponse(Response response, ErrorCodes code, java.lang.Exception e) {
        response.setError(new ResponseError(code, e.getMessage(), e.getCause()));
        return response;
    }

    public ResponseEntity<Object> validateReqAndPushToKafka(Request request, String kafkaTopic) throws Exception {
        Response response = new Response(request);
        try {
            eventHandler.processAndSendEvent(kafkaTopic, request);
            return new ResponseEntity<>(response, HttpStatus.ACCEPTED);
        } catch (Exception e) {
            return exceptionHandlerWithAudit(request, response, e);
        }
    }

    public ResponseEntity<Object> validateReqAndPushToKafka(Map<String, Object> requestBody, String apiAction, String kafkaTopic) throws Exception {
        // Decision 14 — recipient-side JWE decrypt + FHIR validation + Egyptian
        // field validation. Each step is feature-flagged inside the processor;
        // in test profiles all three flags are off and this is a no-op. Errors
        // raised here surface as HTTP 400 ClientException via exceptionHandler.
        try {
            if (jwePayloadProcessor != null) {
                jwePayloadProcessor.process(requestBody);
            }
        } catch (Exception preErr) {
            return exceptionHandler(new Response(), preErr);
        }
        Request request = new Request(requestBody, apiAction);
        Response response = new Response(request);
        try {
            eventHandler.processAndSendEvent(kafkaTopic, request);
            return new ResponseEntity<>(response, HttpStatus.ACCEPTED);
        } catch (Exception e) {
            return exceptionHandlerWithAudit(request, response, e);
        }
    }

    protected ResponseEntity<Object> exceptionHandlerWithAudit(Request request, Response response, Exception e) throws Exception {
        request.setStatus(ERROR_STATUS);
        auditIndexer.createDocument(eventGenerator.generateAuditEvent(request));
        return exceptionHandler(response, e);
    }

    protected ResponseEntity<Object> exceptionHandler(Response response, Exception e){
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        ErrorCodes errorCode = ErrorCodes.INTERNAL_SERVER_ERROR;
        if (e instanceof ClientException) {
            status = HttpStatus.BAD_REQUEST;
            errorCode = ((ClientException) e).getErrCode();
        } else if (e instanceof ServiceUnavailbleException) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            errorCode = ((ServiceUnavailbleException) e).getErrCode();
        } else if (e instanceof ServerException) {
            errorCode = ((ServerException) e).getErrCode();
        } else if (e instanceof AuthorizationException) {
            status = HttpStatus.UNAUTHORIZED;
        } else if (e instanceof ResourceNotFoundException) {
            status = HttpStatus.NOT_FOUND;
        }
        return new ResponseEntity<>(errorResponse(response, errorCode, e), status);
    }



}
