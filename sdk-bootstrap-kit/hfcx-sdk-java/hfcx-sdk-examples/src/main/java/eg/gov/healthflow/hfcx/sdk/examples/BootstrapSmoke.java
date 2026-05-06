package eg.gov.healthflow.hfcx.sdk.examples;

import eg.gov.healthflow.hfcx.sdk.crypto.JWEHelper;

import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * Sprint J1 acceptance-criterion smoke test: prove that a downstream
 * Maven project can pull in {@code hfcx-sdk-core} and successfully
 * call {@link JWEHelper#encrypt(String, java.security.interfaces.RSAPublicKey)}.
 *
 * <p>Round-trips a small payload through encrypt + decrypt against a
 * locally-generated RSA key pair. If this main exits 0, the SDK's
 * primitives are wired correctly and consumers can build against the
 * 1.0.0-SNAPSHOT artifact.
 *
 * <p>The full sender flow (submitClaim / submitPreauth / etc.) lands in
 * J4. This is intentionally the smallest possible end-to-end exercise
 * of the J1 deliverable.
 */
public final class BootstrapSmoke {

    public static void main(String[] args) throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        java.security.KeyPair pair = gen.generateKeyPair();
        RSAPrivateKey priv = (RSAPrivateKey) pair.getPrivate();
        RSAPublicKey pub = (RSAPublicKey) pair.getPublic();

        String plaintext = "{\"resourceType\":\"Bundle\",\"type\":\"collection\",\"entry\":[]}";
        String compact = JWEHelper.encrypt(plaintext, pub);
        String roundtrip = JWEHelper.decrypt(compact, priv);

        if (!plaintext.equals(roundtrip)) {
            System.err.println("BootstrapSmoke FAILED — round-trip did not match");
            System.exit(1);
        }
        System.out.println("BootstrapSmoke OK — JWEHelper.encrypt + decrypt round-tripped a "
                + plaintext.length() + "-char payload through a 2048-bit RSA-OAEP-256 / A256GCM JWE.");
    }

    private BootstrapSmoke() {
    }
}
