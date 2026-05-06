package org.healthflow.hcx.services;


import org.healthflow.hcx.services.sms.SmsGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Thin facade over the active {@link SmsGateway} implementation.
 *
 * <p>Spring resolves the right gateway per active profile:
 * <ul>
 *   <li>{@code egypt}, {@code prod}, {@code staging} -&gt; CEQUENS</li>
 *   <li>{@code aws-fallback}, {@code dev}, {@code test} -&gt; AWS SNS</li>
 * </ul>
 *
 * <p>Public method signatures are preserved so that callers
 * (e.g. {@code ParticipantService}) remain unchanged.
 */
@Service
public class SMSService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SMSService.class);

    private final SmsGateway gateway;

    @Autowired
    public SMSService(SmsGateway gateway) {
        this.gateway = gateway;
    }

    /**
     * Send an arbitrary SMS via the configured gateway.
     *
     * @param phoneNumber the recipient phone number, including country code
     * @param message     the SMS body
     * @throws Exception if the carrier rejects the message
     */
    public void send(String phoneNumber, String message) throws Exception {
        gateway.send(phoneNumber, message);
    }

    /**
     * Send an OTP SMS to the given mobile number. Preserved signature for
     * existing callers ({@code ParticipantService}, etc.).
     *
     * <p>Phone numbers are prefixed with {@code +91} to match the legacy
     * AWS SNS implementation.
     */
    @Async
    public CompletableFuture<String> sendOTP(String phone, String phoneOtp) {
        String message = "HCX mobile verification code is:" + phoneOtp;
        String phoneNumber = "+91" + phone; // Ex: +91XXX4374XX
        try {
            gateway.send(phoneNumber, message);
            LOGGER.debug("OTP SMS dispatched via gateway={}", gateway.name());
            return CompletableFuture.completedFuture("sent");
        } catch (Exception e) {
            LOGGER.error("OTP SMS dispatch failed via gateway={}: {}", gateway.name(), e.getMessage());
            CompletableFuture<String> failed = new CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        }
    }
}
