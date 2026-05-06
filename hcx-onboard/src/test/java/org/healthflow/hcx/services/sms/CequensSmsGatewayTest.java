package org.healthflow.hcx.services.sms;

import kong.unirest.Unirest;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies request shaping (URL, headers, JSON body) and error handling for
 * {@link CequensSmsGateway}. Uses {@link MockWebServer} as a stand-in for the
 * CEQUENS REST API.
 */
public class CequensSmsGatewayTest {

    private MockWebServer server;
    private CequensSmsGateway gateway;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        gateway = new CequensSmsGateway();
        gateway.setBaseUrl(server.url("/sms/v1").toString());
        gateway.setSenderId("HEALTHFLOW");
        gateway.setBearerToken("test-token-123");
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
        // Unirest holds a singleton config; reset between tests so connections close.
        Unirest.shutDown();
    }

    @Test
    void happyPath_doesNotThrow_andSendsExpectedRequest() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"status\":\"queued\"}"));

        gateway.send("+201001234567", "Your code is 4242");

        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/sms/v1/messages", request.getPath());
        assertEquals("Bearer test-token-123", request.getHeader("Authorization"));
        String contentType = request.getHeader("Content-Type");
        assertNotNull(contentType);
        assertTrue(contentType.startsWith("application/json"),
                "Content-Type should be application/json, got: " + contentType);

        String body = request.getBody().readUtf8();
        assertTrue(body.contains("\"senderName\":\"HEALTHFLOW\""), "body=" + body);
        assertTrue(body.contains("\"messageType\":\"text\""), "body=" + body);
        assertTrue(body.contains("\"recipients\":\"+201001234567\""), "body=" + body);
        assertTrue(body.contains("\"messageText\":\"Your code is 4242\""), "body=" + body);
    }

    @Test
    void non2xx_throwsExceptionWithResponseBody() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(401)
                .setBody("{\"error\":\"invalid token\"}"));

        Exception thrown = assertThrows(Exception.class,
                () -> gateway.send("+201000000000", "hello"));
        assertTrue(thrown.getMessage().contains("401"),
                "exception message should reference status code: " + thrown.getMessage());
        assertTrue(thrown.getMessage().contains("invalid token"),
                "exception message should include response body: " + thrown.getMessage());
    }

    @Test
    void name_isCequens() {
        assertEquals("cequens", gateway.name());
    }

    @Test
    void buildRequestBody_escapesSpecialCharacters() {
        String body = CequensSmsGateway.buildRequestBody(
                "HF", "+201", "He said \"hi\"\nand bye");
        assertTrue(body.contains("\\\"hi\\\""), "expected escaped quotes, got: " + body);
        assertTrue(body.contains("\\n"), "expected escaped newline, got: " + body);
    }
}
