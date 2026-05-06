package org.healthflow.common.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.support.VaultResponse;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/**
 * HashiCorp Vault-backed {@link KeyCustodyClient}.
 *
 * <p>Per Decision 14 (zero-knowledge transport) Vault holds the LOCAL private
 * key only. Recipient public keys come from the registry's {@code encryption_cert}
 * URL via the constructor-injected {@link RecipientPublicKeyResolver} (typically
 * {@link RegistryRecipientPublicKeyResolver}).
 *
 * <h3>Vault data shape</h3>
 *
 * The local-key secret at {@code keyPath} must be a Map with two keys:
 * <pre>
 * {
 *   "private_key_pem":         "-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----",
 *   "expires_at_epoch_millis": 1735689600000
 * }
 * </pre>
 *
 * For Vault KV v2 ({@code secret/data/hcx/local-key}) the {@link VaultOperations}
 * read returns the response with a nested {@code data.data} structure; this
 * client transparently flattens by checking both shapes.
 *
 * <h3>Production wiring</h3>
 *
 * Construct via Spring config in the consuming service. Example:
 * <pre>
 * &#064;Bean
 * KeyCustodyClient keyCustodyClient(VaultOperations vault, RegistryService rs) {
 *     RecipientPublicKeyResolver resolver = new RegistryRecipientPublicKeyResolver(
 *         code -> new URL((String) rs.fetchDetails(code).get("encryption_cert")));
 *     return new VaultKeyCustodyClient(vault, "secret/data/hcx/local-key", resolver);
 * }
 * </pre>
 */
public class VaultKeyCustodyClient implements KeyCustodyClient {

    private static final Logger logger = LoggerFactory.getLogger(VaultKeyCustodyClient.class);

    private static final String FIELD_PRIVATE_KEY_PEM = "private_key_pem";
    private static final String FIELD_EXPIRES_EPOCH_MILLIS = "expires_at_epoch_millis";

    private final VaultOperations vault;
    private final String keyPath;
    private final RecipientPublicKeyResolver recipientResolver;

    public VaultKeyCustodyClient(VaultOperations vault,
                                 String keyPath,
                                 RecipientPublicKeyResolver recipientResolver) {
        if (vault == null) throw new IllegalArgumentException("vault must not be null");
        if (keyPath == null || keyPath.trim().isEmpty()) {
            throw new IllegalArgumentException("keyPath must not be blank");
        }
        if (recipientResolver == null) {
            throw new IllegalArgumentException("recipientResolver must not be null — "
                    + "see RegistryRecipientPublicKeyResolver");
        }
        this.vault = vault;
        this.keyPath = keyPath;
        this.recipientResolver = recipientResolver;
        logger.info("VaultKeyCustodyClient initialized at path {}", keyPath);
    }

    @Override
    public RSAPublicKey getRecipientPublicKey(String participantCode) throws Exception {
        return recipientResolver.resolve(participantCode);
    }

    @Override
    public RSAPrivateKey getLocalPrivateKey() throws Exception {
        Map<String, Object> data = readKeyData();
        Object pem = data.get(FIELD_PRIVATE_KEY_PEM);
        if (!(pem instanceof String) || ((String) pem).isEmpty()) {
            throw new IllegalStateException(
                    "Vault secret at " + keyPath + " missing required field '" + FIELD_PRIVATE_KEY_PEM + "'");
        }
        return parsePrivateKey((String) pem);
    }

    @Override
    public Instant getLocalKeyExpiry() throws Exception {
        Map<String, Object> data = readKeyData();
        Object epoch = data.get(FIELD_EXPIRES_EPOCH_MILLIS);
        if (epoch == null) {
            throw new IllegalStateException(
                    "Vault secret at " + keyPath + " missing required field '" + FIELD_EXPIRES_EPOCH_MILLIS + "'");
        }
        long millis;
        if (epoch instanceof Number) {
            millis = ((Number) epoch).longValue();
        } else {
            // Vault sometimes serialises numerics as strings depending on KV version.
            millis = Long.parseLong(epoch.toString());
        }
        return Instant.ofEpochMilli(millis);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readKeyData() {
        VaultResponse resp = vault.read(keyPath);
        if (resp == null || resp.getData() == null) {
            throw new IllegalStateException(
                    "Vault secret at " + keyPath + " not found or has no data");
        }
        Map<String, Object> outer = resp.getData();
        // KV v2 wraps the user data under "data". Detect both shapes so the
        // caller doesn't have to remember which mount style they configured.
        Object inner = outer.get("data");
        if (inner instanceof Map) {
            return (Map<String, Object>) inner;
        }
        return outer;
    }

    private static RSAPrivateKey parsePrivateKey(String pem) throws Exception {
        String body = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(body);
        return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(der));
    }
}
