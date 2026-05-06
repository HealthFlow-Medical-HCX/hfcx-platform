package org.healthflow.hcx.services.sms;

public interface SmsGateway {
    /**
     * Send an SMS to the given phone number. Implementations decide their
     * own retry / error policy; throwing means the message was not delivered
     * to the carrier (vs. delivered-but-not-yet-read).
     */
    void send(String phoneNumber, String message) throws Exception;

    /** A short identifier for logging, e.g. "cequens" or "aws-sns". */
    String name();
}
