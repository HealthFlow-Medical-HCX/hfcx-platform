package eg.gov.healthflow.hfcx.sdk.crypto;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.RSADecrypter;
import com.nimbusds.jose.crypto.RSAEncrypter;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * RSA-OAEP-256 + A256GCM JWE helper, per HealthFlow Integration Guide §25–28.
 *
 * <p>Wraps the Nimbus-JOSE-JWT API in the smallest possible surface so callers
 * never have to know which underlying library is in use; if Nimbus is replaced
 * with jose4j or BouncyCastle later, only this class changes.
 *
 * <p>Algorithm choice is fixed by the integration guide: {@code RSA-OAEP-256}
 * for key wrap, {@code A256GCM} for content encryption. {@code alg: none} and
 * {@code RSA1_5} are intentionally not supported — those would be downgrade
 * vectors. Nimbus's {@link RSAEncrypter}/{@link RSADecrypter} only honour the
 * algorithm declared in the JWE header, but we additionally verify the header
 * on decrypt so a tampered header is rejected before key material is used.
 *
 * <p>Resource lifecycle: each call constructs a fresh {@link JWEObject}; no
 * shared state, no caching of decryption results.
 */
public final class JWEHelper {

    /** Key-encryption algorithm. Locked per Integration Guide §25.2. */
    public static final JWEAlgorithm ALG = JWEAlgorithm.RSA_OAEP_256;

    /** Content-encryption method. Locked per Integration Guide §25.2. */
    public static final EncryptionMethod ENC = EncryptionMethod.A256GCM;

    private JWEHelper() {
    }

    /**
     * Encrypts a plaintext payload for the given recipient.
     *
     * @param plaintext    raw payload (typically a serialized FHIR Bundle)
     * @param recipientKey the recipient's RSA public key, fetched from the registry's
     *                     {@code encryption_cert} URI
     * @return JWE compact serialization (5 base64url-encoded segments separated by dots)
     * @throws JOSEException on encryption failure
     */
    public static String encrypt(String plaintext, RSAPublicKey recipientKey) throws JOSEException {
        if (plaintext == null) throw new IllegalArgumentException("plaintext is null");
        if (recipientKey == null) throw new IllegalArgumentException("recipientKey is null");

        JWEHeader header = new JWEHeader.Builder(ALG, ENC)
                .contentType("application/json")
                .build();
        JWEObject jwe = new JWEObject(header, new Payload(plaintext));
        jwe.encrypt(new RSAEncrypter(recipientKey));
        return jwe.serialize();
    }

    /**
     * Decrypts a JWE compact serialization.
     *
     * <p>Rejects any algorithm other than RSA-OAEP-256 + A256GCM before decryption
     * is attempted. This blocks a class of attacks where a malicious sender supplies
     * a downgraded algorithm hoping the recipient will accept it.
     *
     * @param jweCompact JWE compact serialization
     * @param privateKey local RSA private key (held by the recipient — typically
     *                   sourced from a Vault/HSM-backed key provider per
     *                   Integration Guide §28; the {@code LocalKeyProvider}
     *                   abstraction lands in Sprint J5)
     * @return the plaintext payload
     */
    public static String decrypt(String jweCompact, RSAPrivateKey privateKey) throws JOSEException {
        if (jweCompact == null) throw new IllegalArgumentException("jweCompact is null");
        if (privateKey == null) throw new IllegalArgumentException("privateKey is null");

        JWEObject jwe;
        try {
            jwe = JWEObject.parse(jweCompact);
        } catch (java.text.ParseException e) {
            throw new JOSEException("Malformed JWE compact serialization: " + e.getMessage(), e);
        }

        JWEHeader h = jwe.getHeader();
        if (!ALG.equals(h.getAlgorithm())) {
            throw new JOSEException("Unsupported JWE alg: " + h.getAlgorithm()
                    + " (expected " + ALG + ")");
        }
        if (!ENC.equals(h.getEncryptionMethod())) {
            throw new JOSEException("Unsupported JWE enc: " + h.getEncryptionMethod()
                    + " (expected " + ENC + ")");
        }

        jwe.decrypt(new RSADecrypter(privateKey));
        return jwe.getPayload().toString();
    }
}
