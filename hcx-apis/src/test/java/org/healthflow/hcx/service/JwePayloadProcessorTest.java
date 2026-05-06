package org.healthflow.hcx.service;

import org.healthflow.common.crypto.JWEHelper;
import org.healthflow.common.crypto.KeyCustodyClient;
import org.healthflow.common.exception.ClientException;
import org.healthflow.common.fhir.FhirValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit tests for {@link JwePayloadProcessor}.
 *
 * <p>Each test sets the three feature flags directly via reflection rather than
 * spinning up a Spring context — the processor's behaviour under each flag
 * combination is the contract under test, not the wiring.
 */
public class JwePayloadProcessorTest {

    private RSAPrivateKey privateKey;
    private RSAPublicKey publicKey;

    @BeforeEach
    public void setUp() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("RSA").genKeyPair();
        this.privateKey = (RSAPrivateKey) pair.getPrivate();
        this.publicKey = (RSAPublicKey) pair.getPublic();
    }

    private JwePayloadProcessor build(boolean jwe, boolean fhir, boolean egyptian) {
        FhirValidationService fhirSvc = new FhirValidationService(fhir, null);
        JwePayloadProcessor proc = new JwePayloadProcessor(new StaticKeyClient(privateKey), fhirSvc);
        ReflectionTestUtils.setField(proc, "jweEnabled", jwe);
        ReflectionTestUtils.setField(proc, "egyptianValidationEnabled", egyptian);
        return proc;
    }

    @Test
    public void allFlagsOff_isPassthrough() throws Exception {
        JwePayloadProcessor p = build(false, false, false);
        Map<String, Object> body = new HashMap<>();
        body.put("payload", "anything");
        p.process(body);
        assertEquals("anything", body.get("payload"));
    }

    @Test
    public void jweOff_keepsRequestBodyUntouched() throws Exception {
        JwePayloadProcessor p = build(false, false, false);
        String original = JWEHelper.encrypt("{\"resourceType\":\"Bundle\"}", publicKey);
        Map<String, Object> body = new HashMap<>();
        body.put("payload", original);
        p.process(body);
        assertEquals(original, body.get("payload"), "payload preserved when crypto.jwe.enabled=false");
    }

    @Test
    public void jweOn_decryptsAndReplacesPayload() throws Exception {
        JwePayloadProcessor p = build(true, false, false);
        String plaintext = "{\"resourceType\":\"Bundle\",\"type\":\"collection\"}";
        String compact = JWEHelper.encrypt(plaintext, publicKey);
        Map<String, Object> body = new HashMap<>();
        body.put("payload", compact);
        p.process(body);
        assertEquals(plaintext, body.get("payload"));
    }

    @Test
    public void jweOn_nonJwePayloadIsIgnored() throws Exception {
        // A plain string with no dots is not JWE-shaped; processor passes through.
        JwePayloadProcessor p = build(true, false, false);
        Map<String, Object> body = new HashMap<>();
        body.put("payload", "plain-string");
        p.process(body);
        assertEquals("plain-string", body.get("payload"));
    }

    @Test
    public void jweOn_garbledJweRaisesClientException() throws Exception {
        JwePayloadProcessor p = build(true, false, false);
        Map<String, Object> body = new HashMap<>();
        // Five segments → looks like JWE → fails decrypt → ClientException
        body.put("payload", "a.b.c.d.e");
        try {
            p.process(body);
            fail("expected ClientException");
        } catch (ClientException expected) {
            assertTrue(expected.getMessage().toLowerCase().contains("jwe")
                    || expected.getMessage().toLowerCase().contains("payload"));
        }
    }

    @Test
    public void egyptianOn_rejectsBadNationalId() throws Exception {
        JwePayloadProcessor p = build(true, false, true);
        String plaintext = "{"
                + "\"resourceType\":\"Bundle\",\"type\":\"collection\","
                + "\"entry\":[{\"resource\":{"
                + "\"resourceType\":\"Patient\","
                + "\"identifier\":[{"
                + "\"system\":\"http://healthflow.gov.eg/identifier/national-id\","
                + "\"value\":\"123\"}]}}]}";
        String compact = JWEHelper.encrypt(plaintext, publicKey);
        Map<String, Object> body = new HashMap<>();
        body.put("payload", compact);
        try {
            p.process(body);
            fail("expected ClientException for bad National-ID");
        } catch (ClientException expected) {
            assertTrue(expected.getMessage().contains("Egyptian field"));
        }
    }

    @Test
    public void looksLikeJweCompact_only4DotsCounts() {
        assertTrue(JwePayloadProcessor.looksLikeJweCompact("a.b.c.d.e"));
        assertFalse(JwePayloadProcessor.looksLikeJweCompact("a.b.c.d"));
        assertFalse(JwePayloadProcessor.looksLikeJweCompact("a.b.c.d.e.f"));
        assertFalse(JwePayloadProcessor.looksLikeJweCompact(""));
        assertFalse(JwePayloadProcessor.looksLikeJweCompact(null));
    }

    /** Minimal KeyCustodyClient implementation that returns a fixed local private key. */
    private static final class StaticKeyClient implements KeyCustodyClient {
        private final RSAPrivateKey priv;
        StaticKeyClient(RSAPrivateKey priv) { this.priv = priv; }
        @Override public RSAPublicKey getRecipientPublicKey(String code) {
            throw new UnsupportedOperationException("not needed in inbound processor tests");
        }
        @Override public RSAPrivateKey getLocalPrivateKey() { return priv; }
        @Override public Instant getLocalKeyExpiry() { return Instant.MAX; }
    }
}
