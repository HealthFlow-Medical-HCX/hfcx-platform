package org.healthflow.common.crypto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import org.healthflow.common.exception.ErrorCodes;
import org.healthflow.common.exception.ServiceUnavailbleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * HashiCorp Vault-backed {@link KeyCustodyClient}.
 *
 * <p>Used in {@code prod} and {@code staging} profiles. The local participant's
 * RSA private key is read from a Vault KV-v2 path; recipient public keys are
 * fetched from the Sunbird RC participant registry (the registry exposes an
 * {@code encryption_cert} URI per participant which resolves to a PEM-encoded
 * X.509 certificate).
 *
 * <p>Caching: parsed RSA keys are cached in a Caffeine cache with a configurable
 * TTL (default 1 hour). Cache misses round-trip to Vault / the registry and
 * the cert URL respectively. The cache is bounded so a misbehaving caller cannot
 * exhaust JVM heap.
 *
 * <p>This implementation issues HTTP calls directly via Unirest rather than
 * pulling in {@code spring-cloud-starter-vault-config}. Reasons:
 *   1. Spring Cloud 2020.0.x is the line that pairs with Spring Boot 2.5.x
 *      (the platform's baseline) and dragging in the full Spring Cloud BOM
 *      surfaces transitive bumps unrelated to key custody.
 *   2. Vault's HTTP API is small (one read + one wrap call); a thin client is
 *      easier to reason about for an audit.
 *   3. The interface boundary remains {@link KeyCustodyClient}; swapping to
 *      Spring Cloud Vault later is a one-class change.
 *
 * <p>Failure modes (all map to {@link ServiceUnavailbleException}):
 *   - Vault HTTP error → registry/transit unreachable → service unavailable
 *   - Registry HTTP error → recipient unknown → service unavailable
 *   - Certificate fetch error → service unavailable
 *   - Malformed cert / key data → service unavailable (logged at WARN)
 *
 * <p>Note: the {@link ServiceUnavailbleException} class name typo is preserved;
 * fixing the spelling is tracked separately and is out of scope here.
 */
public class VaultKeyCustodyClient implements KeyCustodyClient {

    private static final Logger logger = LoggerFactory.getLogger(VaultKeyCustodyClient.class);

    /** Vault API endpoint, e.g. https://vault.healthflow.eg:8200 */
    private final String vaultAddr;

    /** Vault token, sourced from VAULT_TOKEN. Must be non-empty in production. */
    private final String vaultToken;

    /** Vault KV-v2 mount + key path for the local private key, e.g. secret/data/hfcx/{code}/private. */
    private final String localPrivateKeyPath;

    /** Sunbird RC base URL, e.g. http://hcx-registry.healthflow.local:8081 */
    private final String registryBaseUrl;

    /** Sunbird RC API path, e.g. /api/v1/Organisation/ */
    private final String registryApiPath;

    /** Cert expiry for the local key. Lazily populated on first read. */
    private volatile Instant localKeyExpiry;

    /** Local private key cache (single entry — itself, with TTL). */
    private final Cache<String, RSAPrivateKey> localPrivateKeyCache;

    /** Recipient public key cache, keyed by participantCode. */
    private final Cache<String, RSAPublicKey> recipientPublicKeyCache;

    private final ObjectMapper jackson = new ObjectMapper();

    /**
     * Builds a Vault custody client.
     *
     * @param vaultAddr           Vault HTTP base URL (no trailing slash)
     * @param vaultToken          Vault auth token (the deployment-time service token)
     * @param localPrivateKeyPath Vault KV-v2 data path containing the local key as
     *                            {@code data.private_key_pem} and optionally
     *                            {@code data.cert_pem}
     * @param registryBaseUrl     Sunbird RC base URL
     * @param registryApiPath     Sunbird RC API prefix (e.g. {@code /api/v1/Organisation/})
     * @param cacheTtl            Caffeine cache TTL (recommended: PT1H)
     */
    public VaultKeyCustodyClient(String vaultAddr,
                                 String vaultToken,
                                 String localPrivateKeyPath,
                                 String registryBaseUrl,
                                 String registryApiPath,
                                 Duration cacheTtl) {
        if (vaultAddr == null || vaultAddr.isEmpty()) {
            throw new IllegalArgumentException("VAULT_ADDR is required for VaultKeyCustodyClient");
        }
        if (vaultToken == null || vaultToken.isEmpty()) {
            throw new IllegalArgumentException("VAULT_TOKEN is required for VaultKeyCustodyClient");
        }
        this.vaultAddr = stripTrailingSlash(vaultAddr);
        this.vaultToken = vaultToken;
        this.localPrivateKeyPath = localPrivateKeyPath;
        this.registryBaseUrl = stripTrailingSlash(registryBaseUrl);
        this.registryApiPath = registryApiPath == null ? "/api/v1/Organisation/" : registryApiPath;
        Duration ttl = cacheTtl == null ? Duration.ofHours(1) : cacheTtl;
        this.localPrivateKeyCache = Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(2)
                .build();
        this.recipientPublicKeyCache = Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(10_000)
                .build();
        logger.info("VaultKeyCustodyClient initialized (addr={}, registry={}, ttl={})",
                this.vaultAddr, this.registryBaseUrl, ttl);
    }

    @Override
    public RSAPublicKey getRecipientPublicKey(String participantCode) throws Exception {
        if (participantCode == null || participantCode.isEmpty()) {
            throw new IllegalArgumentException("participantCode is null or empty");
        }
        RSAPublicKey cached = recipientPublicKeyCache.getIfPresent(participantCode);
        if (cached != null) {
            return cached;
        }
        String certUri = lookupEncryptionCertUri(participantCode);
        RSAPublicKey key = fetchAndParsePublicKey(certUri, participantCode);
        recipientPublicKeyCache.put(participantCode, key);
        return key;
    }

    @Override
    public RSAPrivateKey getLocalPrivateKey() throws Exception {
        RSAPrivateKey cached = localPrivateKeyCache.getIfPresent("local");
        if (cached != null) {
            return cached;
        }
        VaultKeyMaterial material = readLocalKeyFromVault();
        RSAPrivateKey key = parsePrivateKeyPem(material.privateKeyPem);
        if (material.certPem != null) {
            try {
                X509Certificate cert = parseCertificate(material.certPem);
                this.localKeyExpiry = cert.getNotAfter().toInstant();
            } catch (CertificateException e) {
                logger.warn("VaultKeyCustodyClient: cert in vault path {} did not parse: {}",
                        localPrivateKeyPath, e.getMessage());
            }
        }
        localPrivateKeyCache.put("local", key);
        return key;
    }

    @Override
    public Instant getLocalKeyExpiry() throws Exception {
        if (localKeyExpiry == null) {
            // force a read so that the expiry is populated from the cert in vault
            getLocalPrivateKey();
        }
        return localKeyExpiry == null ? Instant.MAX : localKeyExpiry;
    }

    // ---- Vault interactions ---------------------------------------------------

    private VaultKeyMaterial readLocalKeyFromVault() throws ServiceUnavailbleException {
        String url = vaultAddr + "/v1/" + stripLeadingSlash(localPrivateKeyPath);
        try {
            HttpResponse<String> resp = Unirest.get(url)
                    .header("X-Vault-Token", vaultToken)
                    .asString();
            if (resp.getStatus() < 200 || resp.getStatus() >= 300) {
                throw new ServiceUnavailbleException(ErrorCodes.ERR_SERVICE_UNAVAILABLE,
                        "Vault read failed for path " + localPrivateKeyPath
                                + " (status=" + resp.getStatus() + ")");
            }
            JsonNode root = jackson.readTree(resp.getBody());
            // KV-v2 layout: data.data.{private_key_pem, cert_pem}
            JsonNode data = root.path("data").path("data");
            if (data.isMissingNode() || !data.hasNonNull("private_key_pem")) {
                throw new ServiceUnavailbleException(ErrorCodes.ERR_SERVICE_UNAVAILABLE,
                        "Vault response at " + localPrivateKeyPath
                                + " is missing data.data.private_key_pem");
            }
            VaultKeyMaterial m = new VaultKeyMaterial();
            m.privateKeyPem = data.get("private_key_pem").asText();
            m.certPem = data.hasNonNull("cert_pem") ? data.get("cert_pem").asText() : null;
            return m;
        } catch (ServiceUnavailbleException e) {
            throw e;
        } catch (Exception e) {
            logger.warn("Vault read error for path {}: {}", localPrivateKeyPath, e.getMessage());
            throw new ServiceUnavailbleException(ErrorCodes.ERR_SERVICE_UNAVAILABLE,
                    "Vault read error: " + e.getMessage());
        }
    }

    // ---- Sunbird RC registry interactions ------------------------------------

    private String lookupEncryptionCertUri(String participantCode) throws ServiceUnavailbleException {
        String url = registryBaseUrl + registryApiPath + participantCode;
        try {
            HttpResponse<String> resp = Unirest.get(url).asString();
            if (resp.getStatus() < 200 || resp.getStatus() >= 300) {
                throw new ServiceUnavailbleException(ErrorCodes.ERR_SERVICE_UNAVAILABLE,
                        "Registry lookup failed for participantCode=" + participantCode
                                + " (status=" + resp.getStatus() + ")");
            }
            JsonNode root = jackson.readTree(resp.getBody());
            JsonNode certNode = root.path("encryption_cert");
            if (certNode.isMissingNode() || certNode.asText().isEmpty()) {
                throw new ServiceUnavailbleException(ErrorCodes.ERR_SERVICE_UNAVAILABLE,
                        "Registry record for " + participantCode + " has no encryption_cert");
            }
            return certNode.asText();
        } catch (ServiceUnavailbleException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceUnavailbleException(ErrorCodes.ERR_SERVICE_UNAVAILABLE,
                    "Registry lookup error: " + e.getMessage());
        }
    }

    private RSAPublicKey fetchAndParsePublicKey(String certUri, String participantCode) throws ServiceUnavailbleException {
        try {
            HttpResponse<String> resp = Unirest.get(certUri).asString();
            if (resp.getStatus() < 200 || resp.getStatus() >= 300) {
                throw new ServiceUnavailbleException(ErrorCodes.ERR_SERVICE_UNAVAILABLE,
                        "encryption_cert fetch failed for " + participantCode
                                + " (status=" + resp.getStatus() + ")");
            }
            X509Certificate cert = parseCertificate(resp.getBody());
            cert.checkValidity(new Date());
            return (RSAPublicKey) cert.getPublicKey();
        } catch (ServiceUnavailbleException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceUnavailbleException(ErrorCodes.ERR_SERVICE_UNAVAILABLE,
                    "encryption_cert parse/validate error for " + participantCode + ": " + e.getMessage());
        }
    }

    // ---- Parsing helpers ------------------------------------------------------

    static RSAPrivateKey parsePrivateKeyPem(String pem) throws Exception {
        String body = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(body);
        return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    static X509Certificate parseCertificate(String pem) throws CertificateException {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return (X509Certificate) cf.generateCertificate(
                new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)));
    }

    private static String stripTrailingSlash(String s) {
        if (s == null) return null;
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static String stripLeadingSlash(String s) {
        if (s == null) return null;
        return s.startsWith("/") ? s.substring(1) : s;
    }

    /** Holder for the private key + cert PEMs returned from Vault. */
    private static final class VaultKeyMaterial {
        String privateKeyPem;
        String certPem;
    }
}
