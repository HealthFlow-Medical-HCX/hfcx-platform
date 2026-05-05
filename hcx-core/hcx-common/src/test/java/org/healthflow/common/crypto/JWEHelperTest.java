package org.healthflow.common.crypto;

import com.nimbusds.jose.JOSEException;
import org.junit.BeforeClass;
import org.junit.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Round-trip and negative tests for {@link JWEHelper}. Generates a fresh
 * 2048-bit RSA key pair once at class load so the suite runs offline and
 * never depends on test-only key material checked into the repo.
 */
public class JWEHelperTest {

    private static RSAPublicKey publicKey;
    private static RSAPrivateKey privateKey;
    private static RSAPublicKey wrongPublicKey;
    private static RSAPrivateKey wrongPrivateKey;

    @BeforeClass
    public static void generateTestKeys() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair k1 = g.generateKeyPair();
        publicKey = (RSAPublicKey) k1.getPublic();
        privateKey = (RSAPrivateKey) k1.getPrivate();

        KeyPair k2 = g.generateKeyPair();
        wrongPublicKey = (RSAPublicKey) k2.getPublic();
        wrongPrivateKey = (RSAPrivateKey) k2.getPrivate();
    }

    @Test
    public void encryptDecryptRoundTripPreservesPayload() throws Exception {
        String plaintext = "{\"resourceType\":\"Bundle\",\"type\":\"collection\",\"entry\":[]}";
        String jwe = JWEHelper.encrypt(plaintext, publicKey);

        // The JWE compact serialization has 5 base64url segments separated by dots.
        assertEquals(5, jwe.split("\\.", -1).length);
        assertTrue("JWE header should encode RSA-OAEP-256 + A256GCM",
                jwe.startsWith("eyJ"));

        String decrypted = JWEHelper.decrypt(jwe, privateKey);
        assertEquals(plaintext, decrypted);
    }

    @Test
    public void encryptProducesDistinctCiphertextForSamePlaintext() throws Exception {
        // A256GCM uses a fresh 96-bit IV per call, so two encryptions of the same
        // plaintext under the same key MUST yield distinct ciphertexts. If they don't,
        // the algorithm is misconfigured and replay attacks become trivial.
        String plaintext = "deterministic input";
        String jwe1 = JWEHelper.encrypt(plaintext, publicKey);
        String jwe2 = JWEHelper.encrypt(plaintext, publicKey);
        assertFalse("JWE should be non-deterministic under identical inputs", jwe1.equals(jwe2));
    }

    @Test
    public void wrongPrivateKeyFailsDecryption() throws Exception {
        String jwe = JWEHelper.encrypt("payload", publicKey);
        try {
            JWEHelper.decrypt(jwe, wrongPrivateKey);
            fail("decrypt with wrong private key must fail");
        } catch (JOSEException e) {
            // expected — the integrity check (GCM tag) detects the wrong key
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void tamperedCiphertextFailsAuthentication() throws Exception {
        String jwe = JWEHelper.encrypt("payload", publicKey);
        String[] segments = jwe.split("\\.");
        // Flip a byte in the ciphertext segment (index 3 — header.encrypted_key.iv.ciphertext.tag).
        char[] cipher = segments[3].toCharArray();
        cipher[0] = (cipher[0] == 'A') ? 'B' : 'A';
        segments[3] = new String(cipher);
        String tampered = String.join(".", segments);

        try {
            JWEHelper.decrypt(tampered, privateKey);
            fail("decrypt of tampered ciphertext must fail authentication");
        } catch (JOSEException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void tamperedAuthenticationTagIsRejected() throws Exception {
        String jwe = JWEHelper.encrypt("payload", publicKey);
        String[] segments = jwe.split("\\.");
        char[] tag = segments[4].toCharArray();
        tag[0] = (tag[0] == 'A') ? 'B' : 'A';
        segments[4] = new String(tag);
        String tampered = String.join(".", segments);

        try {
            JWEHelper.decrypt(tampered, privateKey);
            fail("decrypt with tampered tag must fail");
        } catch (JOSEException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void downgradeToWeakerAlgorithmIsRejected() {
        // Caller-supplied JWE compact serialization carrying a header that claims
        // alg=dir + enc=A128CBC-HS256. Even if the message somehow validated, our
        // decrypt() rejects any algorithm other than RSA-OAEP-256 + A256GCM
        // BEFORE attempting decryption.
        // Header: {"alg":"dir","enc":"A128CBC-HS256"} -> base64url
        String maliciousHeader = "eyJhbGciOiJkaXIiLCJlbmMiOiJBMTI4Q0JDLUhTMjU2In0";
        String fake = maliciousHeader + ".." + "AAAA" + "." + "AAAA" + "." + "AAAA";
        try {
            JWEHelper.decrypt(fake, privateKey);
            fail("downgrade attempt must be rejected");
        } catch (JOSEException e) {
            assertTrue("expected algorithm-rejection error, got: " + e.getMessage(),
                    e.getMessage().contains("Unsupported JWE alg"));
        }
    }

    @Test
    public void malformedJweIsRejected() {
        try {
            JWEHelper.decrypt("not-a-jwe", privateKey);
            fail("malformed JWE must throw");
        } catch (JOSEException e) {
            assertTrue(e.getMessage().contains("Malformed JWE"));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void encryptRejectsNullPlaintext() throws Exception {
        JWEHelper.encrypt(null, publicKey);
    }

    @Test(expected = IllegalArgumentException.class)
    public void encryptRejectsNullKey() throws Exception {
        JWEHelper.encrypt("payload", null);
    }

    @Test
    public void fileKeyCustodyRefusesProductionProfile() {
        try {
            new FileKeyCustodyClient("production", java.nio.file.Paths.get("/dev/null"), 0L);
            fail("FileKeyCustodyClient must refuse to start with profile=production");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("must not be used in production"));
        } catch (Exception e) {
            fail("expected IllegalStateException, got: " + e);
        }
    }
}
