package eg.gov.healthflow.hfcx.sdk.smoketest;

import eg.gov.healthflow.hfcx.sdk.crypto.JWEHelper;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Sprint J1 acceptance criterion: a downstream Maven project, NOT in
 * the parent reactor, can resolve eg.gov.healthflow:hfcx-sdk-core from
 * Sonatype OSSRH snapshots and call its public API successfully.
 */
class SnapshotConsumerTest {

    @Test
    void canEncryptAndDecryptThroughTheSnapshotArtifact() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();
        RSAPublicKey pub = (RSAPublicKey) pair.getPublic();
        RSAPrivateKey priv = (RSAPrivateKey) pair.getPrivate();

        String plaintext = "{\"resourceType\":\"Bundle\"}";
        String compact = JWEHelper.encrypt(plaintext, pub);
        String roundtrip = JWEHelper.decrypt(compact, priv);

        assertEquals(plaintext, roundtrip,
                "Round-trip through the snapshot-published JWEHelper must be lossless");
    }
}
