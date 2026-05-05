package org.healthflow.common.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

/**
 * File-backed {@link KeyCustodyClient} for development and tests only.
 *
 * <p>Reads a PKCS#8 PEM private key from a configurable directory and fetches
 * recipient X.509 PEM certificates from URLs (the registry's
 * {@code encryption_cert} field). Public keys are cached in-process; the
 * private key is loaded once at construction.
 *
 * <p><b>Production must not use this implementation.</b> The constructor
 * receives the active Spring profile and throws {@link IllegalStateException}
 * if it ever activates under {@code production} — fail-loud before any key
 * material is loaded.
 *
 * <p>The Vault- and HSM-backed implementations are separate classes; selecting
 * between them is wired through the Spring config.
 */
public class FileKeyCustodyClient implements KeyCustodyClient {

    private static final Logger logger = LoggerFactory.getLogger(FileKeyCustodyClient.class);

    private final RSAPrivateKey localPrivateKey;
    private final Instant localKeyExpiry;
    private final ConcurrentHashMap<String, RSAPublicKey> publicKeyCache = new ConcurrentHashMap<>();

    /**
     * @param activeProfile     Spring profile (e.g. {@code dev}, {@code staging});
     *                          must NOT be {@code production}
     * @param privateKeyPemPath path to the PKCS#8 PEM private-key file
     * @param expiryEpochMillis local cert expiry in epoch-millis; supplied by
     *                          configuration since reading the cert from the
     *                          private-key path is out of scope here
     */
    public FileKeyCustodyClient(String activeProfile, Path privateKeyPemPath, long expiryEpochMillis) throws Exception {
        if ("production".equalsIgnoreCase(activeProfile) || "prod".equalsIgnoreCase(activeProfile)) {
            throw new IllegalStateException(
                    "FileKeyCustodyClient must not be used in production. "
                    + "Configure VaultKeyCustodyClient or an HSM-backed implementation.");
        }
        this.localPrivateKey = loadPrivateKey(privateKeyPemPath);
        this.localKeyExpiry = Instant.ofEpochMilli(expiryEpochMillis);
        logger.info("FileKeyCustodyClient initialized (profile={}). Local cert expiry: {}",
                activeProfile, this.localKeyExpiry);
    }

    @Override
    public RSAPublicKey getRecipientPublicKey(String participantCode) throws Exception {
        // The registry caches participant details elsewhere; this cache is for the
        // parsed RSAPublicKey to avoid X.509 parsing on every dispatch.
        RSAPublicKey cached = publicKeyCache.get(participantCode);
        if (cached != null) return cached;
        throw new IllegalStateException(
                "FileKeyCustodyClient does not auto-fetch from the registry. "
                + "Call putRecipientPublicKey(participantCode, certUrl) first or use VaultKeyCustodyClient.");
    }

    /**
     * Test/dev helper to seed the public-key cache from a registry-style URL.
     */
    public void putRecipientPublicKey(String participantCode, URL certUrl) throws Exception {
        String pem = new String(certUrl.openStream().readAllBytes(), StandardCharsets.UTF_8);
        publicKeyCache.put(participantCode, parsePublicKeyFromPem(pem));
    }

    @Override
    public RSAPrivateKey getLocalPrivateKey() {
        return localPrivateKey;
    }

    @Override
    public Instant getLocalKeyExpiry() {
        return localKeyExpiry;
    }

    private static RSAPrivateKey loadPrivateKey(Path pemPath) throws Exception {
        String pem = new String(Files.readAllBytes(pemPath), StandardCharsets.UTF_8);
        String body = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(body);
        return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static RSAPublicKey parsePublicKeyFromPem(String pem) throws Exception {
        // X.509 certificate path
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) cf.generateCertificate(
                new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)));
        return (RSAPublicKey) cert.getPublicKey();
    }
}
