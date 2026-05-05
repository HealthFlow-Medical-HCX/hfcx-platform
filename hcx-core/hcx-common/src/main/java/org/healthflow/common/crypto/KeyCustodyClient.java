package org.healthflow.common.crypto;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;

/**
 * Boundary between the JWE helper and the actual storage of cryptographic key
 * material. Production deployments back this with HashiCorp Vault Transit / KV-v2
 * (recommended for dev + staging) or a PKCS#11 HSM (recommended for production
 * per Integration Guide §28.3); local development backs it with a file-based
 * implementation that is intentionally fail-loud if it activates with
 * {@code spring.profiles.active=production}.
 *
 * <p>Implementations must be safe to call concurrently — the gateway and the
 * dispatcher both invoke {@link #getLocalPrivateKey()} on the request hot path.
 *
 * <p>Public-key lookups should be cached by the implementation against the
 * registry's {@code encryption_cert} URI; the cache TTL must be short (e.g.
 * 5 minutes) to honour participant cert rotations promptly.
 */
public interface KeyCustodyClient {

    /**
     * Returns the recipient participant's RSA public key, fetched from the
     * registry's {@code encryption_cert} field. Implementations cache.
     *
     * @param participantCode the recipient's HCX participant code
     */
    RSAPublicKey getRecipientPublicKey(String participantCode) throws Exception;

    /**
     * Returns this HCX instance's RSA private key for decrypting inbound JWEs.
     * Production implementations source this from Vault / HSM at startup or on
     * demand; the key material must never be persisted to disk in plaintext
     * outside the custody backend.
     */
    RSAPrivateKey getLocalPrivateKey() throws Exception;

    /**
     * Returns the expiry of the local private key's certificate. The
     * cert-expiry scheduler reads this and emits 30/14/7/1-day notifications.
     */
    Instant getLocalKeyExpiry() throws Exception;
}
