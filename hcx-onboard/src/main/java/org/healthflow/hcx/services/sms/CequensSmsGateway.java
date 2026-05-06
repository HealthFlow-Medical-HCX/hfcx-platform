package org.healthflow.hcx.services.sms;

import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * SMS gateway backed by the CEQUENS REST API.
 *
 * <p>Activated for the {@code egypt}, {@code prod} and {@code staging} profiles.
 * The {@code egypt} profile is the default in production.
 */
@Service
@Profile({"egypt", "prod", "staging"})
public class CequensSmsGateway implements SmsGateway {

    private static final Logger LOGGER = LoggerFactory.getLogger(CequensSmsGateway.class);
    private static final String NAME = "cequens";

    @Value("${sms.cequens.baseUrl}")
    private String baseUrl;

    @Value("${sms.cequens.senderId}")
    private String senderId;

    @Value("${sms.cequens.bearerToken}")
    private String bearerToken;

    @Override
    public void send(String phoneNumber, String message) throws Exception {
        String url = stripTrailingSlash(baseUrl) + "/messages";
        String body = buildRequestBody(senderId, phoneNumber, message);

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + bearerToken);
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");

        HttpResponse<String> response = Unirest.post(url).headers(headers).body(body).asString();

        int status = response.getStatus();
        if (status < 200 || status >= 300) {
            String responseBody = response.getBody();
            LOGGER.error("CEQUENS SMS send failed: status={} body={}", status, responseBody);
            throw new Exception("CEQUENS SMS send failed (status " + status + "): " + responseBody);
        }
        LOGGER.debug("CEQUENS SMS sent: status={} recipient={}", status, phoneNumber);
    }

    @Override
    public String name() {
        return NAME;
    }

    static String buildRequestBody(String senderId, String phoneNumber, String message) {
        StringBuilder sb = new StringBuilder();
        sb.append('{')
                .append("\"senderName\":\"").append(escape(senderId)).append("\",")
                .append("\"messageType\":\"text\",")
                .append("\"recipients\":\"").append(escape(phoneNumber)).append("\",")
                .append("\"messageText\":\"").append(escape(message)).append('"')
                .append('}');
        return sb.toString();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.toString();
    }

    private static String stripTrailingSlash(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    // Test/visibility helpers
    void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    void setBearerToken(String bearerToken) {
        this.bearerToken = bearerToken;
    }
}
