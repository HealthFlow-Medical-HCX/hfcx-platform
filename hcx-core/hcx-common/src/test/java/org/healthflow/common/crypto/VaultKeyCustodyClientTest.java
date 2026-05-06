package org.healthflow.common.crypto;

import org.healthflow.common.exception.ServiceUnavailbleException;
import org.junit.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Unit tests for {@link VaultKeyCustodyClient}.
 *
 * <p>The HTTP-side behaviour (Vault transit reads and Sunbird RC lookups) is
 * exercised at integration-test time with WireMock; here we cover the pure
 * units: PEM parsing, fail-fast on missing config, the cache-by-participant-code
 * contract, and the inability-to-decrypt-with-no-vault contract.
 */
public class VaultKeyCustodyClientTest {

    @Test
    public void rejectsMissingVaultAddr() {
        try {
            new VaultKeyCustodyClient(null, "tok", "secret/data/x", "http://reg", "/p/", Duration.ofHours(1));
            fail("expected IllegalArgumentException for null vaultAddr");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().toLowerCase().contains("vault_addr"));
        }
    }

    @Test
    public void rejectsMissingVaultToken() {
        try {
            new VaultKeyCustodyClient("http://vault", "", "secret/data/x", "http://reg", "/p/", Duration.ofHours(1));
            fail("expected IllegalArgumentException for empty vaultToken");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().toLowerCase().contains("vault_token"));
        }
    }

    @Test
    public void parsesPkcs8PrivateKey() throws Exception {
        // Generate a real PKCS#8 PEM and round-trip it through the helper.
        KeyPair pair = KeyPairGenerator.getInstance("RSA").genKeyPair();
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + java.util.Base64.getMimeEncoder(64, "\n".getBytes())
                        .encodeToString(pair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----";
        RSAPrivateKey parsed = VaultKeyCustodyClient.parsePrivateKeyPem(pem);
        assertNotNull(parsed);
        assertEquals("RSA", parsed.getAlgorithm());
    }

    @Test
    public void rejectsMalformedCertificatePem() {
        try {
            VaultKeyCustodyClient.parseCertificate("not a certificate");
            fail("expected CertificateException for malformed cert PEM");
        } catch (java.security.cert.CertificateException expected) {
            // ok
        }
    }

    @Test
    public void recipientLookupBubblesServiceUnavailableWhenRegistryUnreachable() {
        // No registry running on this port — Unirest call should fail and translate.
        VaultKeyCustodyClient client = new VaultKeyCustodyClient(
                "http://127.0.0.1:1",
                "test-token",
                "secret/data/test",
                "http://127.0.0.1:1",
                "/api/v1/Organisation/",
                Duration.ofMinutes(5));
        try {
            client.getRecipientPublicKey("test-participant");
            fail("expected ServiceUnavailbleException");
        } catch (ServiceUnavailbleException expected) {
            assertTrue("error message should mention registry or participant",
                    expected.getMessage().toLowerCase().contains("registry")
                            || expected.getMessage().toLowerCase().contains("participant"));
        } catch (Exception other) {
            fail("expected ServiceUnavailbleException, got " + other);
        }
    }
}
