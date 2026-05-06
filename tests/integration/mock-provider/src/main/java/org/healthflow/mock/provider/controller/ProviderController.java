package org.healthflow.mock.provider.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Mock provider endpoints for the four §31 protocol cycles.
 *
 * <p>The provider is the <em>initiator</em> for coverage-eligibility / preauth /
 * claim / payment-notice forward calls (`/check`, `/submit`, `/request`) and is
 * also the recipient of the corresponding `on_*` callbacks from the payer.
 *
 * <p>Both directions are implemented here so that scenario scripts can drive
 * the provider as either origin or destination.
 *
 * <p>TODO(gap-17-followup): real JWE decrypt + signature verification. Today we
 * accept any JSON body to keep the harness shell-only and fast.
 */
@RestController
@RequestMapping("/v1")
public class ProviderController {

    private static final Logger LOG = Logger.getLogger(ProviderController.class.getName());

    @Value("${mock.participant-code:hospital-1.example.com}")
    private String participantCode;

    @PostMapping("/coverageeligibility/check")
    public ResponseEntity<Map<String, Object>> coverageCheck(@RequestBody(required = false) String body) {
        return accepted("coverageeligibility/check", body);
    }

    @PostMapping("/coverageeligibility/on_check")
    public ResponseEntity<Map<String, Object>> coverageOnCheck(@RequestBody(required = false) String body) {
        return accepted("coverageeligibility/on_check", body);
    }

    @PostMapping("/preauth/submit")
    public ResponseEntity<Map<String, Object>> preauthSubmit(@RequestBody(required = false) String body) {
        return accepted("preauth/submit", body);
    }

    @PostMapping("/preauth/on_submit")
    public ResponseEntity<Map<String, Object>> preauthOnSubmit(@RequestBody(required = false) String body) {
        return accepted("preauth/on_submit", body);
    }

    @PostMapping("/claim/submit")
    public ResponseEntity<Map<String, Object>> claimSubmit(@RequestBody(required = false) String body) {
        return accepted("claim/submit", body);
    }

    @PostMapping("/claim/on_submit")
    public ResponseEntity<Map<String, Object>> claimOnSubmit(@RequestBody(required = false) String body) {
        return accepted("claim/on_submit", body);
    }

    @PostMapping("/paymentnotice/request")
    public ResponseEntity<Map<String, Object>> paymentRequest(@RequestBody(required = false) String body) {
        return accepted("paymentnotice/request", body);
    }

    @PostMapping("/paymentnotice/on_request")
    public ResponseEntity<Map<String, Object>> paymentOnRequest(@RequestBody(required = false) String body) {
        return accepted("paymentnotice/on_request", body);
    }

    private ResponseEntity<Map<String, Object>> accepted(String operation, String body) {
        int len = body == null ? 0 : body.length();
        LOG.info(() -> String.format(
                "mock-provider participant=%s op=%s bodyLen=%d", participantCode, operation, len));
        Map<String, Object> resp = new HashMap<>();
        resp.put("timestamp", Instant.now().toEpochMilli());
        resp.put("correlation_id", UUID.randomUUID().toString());
        resp.put("api_call_id", UUID.randomUUID().toString());
        resp.put("result", Map.of("status", "request.queued"));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(resp);
    }
}
