package org.healthflow.common.utils;

import org.junit.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class JWTUtilsTest {

    private final JWTUtils jwtUtils = new JWTUtils();

    // note: swasth URLs in the next two tests kept intentionally — they fetch a
    // real self-signed PEM from the upstream jwe-helper repo. TODO(v1.4):
    // HealthFlow should mirror this fixture under healthflow.gov.eg and
    // switch the URLs.
    @Test
    public void testGetCertificateExpiry() throws Exception {
        assertNotNull(jwtUtils.getCertificateExpiry("https://raw.githubusercontent.com/Swasth-Digital-Health-Foundation/jwe-helper/main/src/test/resources/x509-self-signed-certificate.pem"));
    }

    @Test
    public void testIsValidSignature() throws Exception {
        assertTrue(jwtUtils.isValidSignature(getNotificationPayload(), "https://raw.githubusercontent.com/Swasth-Digital-Health-Foundation/jwe-helper/main/src/test/resources/x509-self-signed-certificate.pem"));
    }

    private String getNotificationPayload(){
        return "eyJhbGciOiJSUzI1NiJ9.eyJ1c2VyIjoidGVzdCJ9.D9LaKmvZfjmNHc3UtrWmILkmGJUdjjKaJtus6H9vRcQZqrd6gTXZ-NQl_oayPc8poFq3vljUAKXPO7PzDNI_N1pd2eqWJ5O-UM1NG_m-v1pKi-kV9HaXucZ0VhAjFS9DEQwZ_CMUMOtgnhh5hKFZZn7ljEbDHaC-2JGDshPNUm_FMWMK447A5B-BJYkEztV47Ony-k4lu9BmfmwsKtqSOO2Y3_qXzuZalShMNt9risNoguY_CQWMFTjV4P_cgsKYDtaRnpgKX96DCO2L2j47BbGx2zHCsMia_LIkxBvEnuEAbDn40zZ17IPOo3BCve8JjmPgCLOYnI3W8HBfHfGegg";
    }

    /**
     * Generates a fresh 2048-bit RSA keypair at test time, signs a token with
     * the private key, and verifies the signature with the matching public key.
     *
     * <p>The previous version of this test used a hardcoded 1024-bit private key
     * and verified against a remote PEM cert in the upstream Swasth repo. After
     * the jjwt 0.9.1 -> 0.12.6 bump (PR #3), jjwt enforces the RFC 7518
     * requirement that RS256 keys be at least 2048 bits, so the hardcoded key
     * fails. Generating fresh keys at test time also removes the network
     * dependency and the dead-pin on a Swasth-managed URL.
     */
    @Test
    public void generateAuthTokenTest() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        String privateKeyB64 = Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded());

        String sub = "test-verifier-code";
        String iss = "1-d2d56996-1b77-4abb-b9e9-0e6e7343c72e";
        Long expiryTime = Long.valueOf(86400000);
        String token = jwtUtils.generateAuthToken(privateKeyB64, sub, iss, expiryTime);

        // Verify the JWS signature directly against our matching public key,
        // mirroring what JWTUtils.isValidSignature does internally minus the URL fetch.
        String[] parts = token.split("\\.");
        assertTrue(parts.length == 3);
        String signingInput = parts[0] + "." + parts[1];
        byte[] decodedSig = Base64.getUrlDecoder().decode(parts[2]);
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(kp.getPublic());
        verifier.update(signingInput.getBytes());
        assertTrue("Generated JWS signature must verify against the matching public key",
                verifier.verify(decodedSig));
    }
}
