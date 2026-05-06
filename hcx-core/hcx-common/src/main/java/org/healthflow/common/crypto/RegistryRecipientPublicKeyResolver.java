package org.healthflow.common.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Default {@link RecipientPublicKeyResolver} that fetches a participant's X.509
 * certificate from a URL (the registry's {@code encryption_cert} field), parses
 * it as RSA, and caches the result in-process with an LRU bound.
 *
 * <p>Two collaborators:
 * <ul>
 *   <li>A {@code participantCode -> certUrl} function that the platform's
 *       existing registry client provides. Pluggable so the resolver doesn't
 *       have a hard dep on a specific registry-client class — pass any function
 *       (typically {@code code -> registryService.fetchDetails(code).get("encryption_cert")}).</li>
 *   <li>A maximum cache size. Old entries evict in LRU order. Default 1000.</li>
 * </ul>
 *
 * <p>Cache invalidation: callers that need to react promptly to participant cert
 * rotation should call {@link #evict(String)} from a registry-event listener.
 * Without that, a rotated cert takes effect after the entry ages out of the LRU
 * (i.e. when 1000 other lookups have happened).
 */
public class RegistryRecipientPublicKeyResolver implements RecipientPublicKeyResolver {

    private static final Logger logger = LoggerFactory.getLogger(RegistryRecipientPublicKeyResolver.class);

    /** participant_code -> registry encryption_cert URL */
    @FunctionalInterface
    public interface CertUrlLookup {
        URL urlFor(String participantCode) throws Exception;
    }

    private final CertUrlLookup urlLookup;
    private final Map<String, RSAPublicKey> cache;

    public RegistryRecipientPublicKeyResolver(CertUrlLookup urlLookup) {
        this(urlLookup, 1000);
    }

    public RegistryRecipientPublicKeyResolver(CertUrlLookup urlLookup, int maxCacheEntries) {
        if (urlLookup == null) throw new IllegalArgumentException("urlLookup must not be null");
        if (maxCacheEntries <= 0) throw new IllegalArgumentException("maxCacheEntries must be > 0");
        this.urlLookup = urlLookup;
        // LinkedHashMap with access-order semantics + size cap = textbook bounded LRU.
        // Wrapped in synchronizedMap so concurrent callers don't trip over the
        // structural modification in removeEldestEntry. Hot path is ~1 lookup per
        // dispatch, so the synchronization cost is negligible vs the network fetch
        // it occasionally avoids.
        this.cache = Collections.synchronizedMap(new LinkedHashMap<String, RSAPublicKey>(
                Math.min(64, maxCacheEntries), 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, RSAPublicKey> eldest) {
                return size() > maxCacheEntries;
            }
        });
    }

    @Override
    public RSAPublicKey resolve(String participantCode) throws Exception {
        if (participantCode == null || participantCode.trim().isEmpty()) {
            throw new IllegalArgumentException("participantCode must not be blank");
        }
        RSAPublicKey hit = cache.get(participantCode);
        if (hit != null) return hit;
        URL certUrl = urlLookup.urlFor(participantCode);
        if (certUrl == null) {
            throw new IllegalStateException(
                    "No encryption_cert registered in the registry for participant " + participantCode);
        }
        RSAPublicKey parsed = fetchAndParse(certUrl);
        cache.put(participantCode, parsed);
        logger.debug("Cached recipient public key for {} (cert url: {})", participantCode, certUrl);
        return parsed;
    }

    /**
     * Drop a cached entry. Call from a registry-event listener when a participant
     * rotates their {@code encryption_cert}.
     */
    public void evict(String participantCode) {
        cache.remove(participantCode);
    }

    /** Test/admin helper. */
    public int cacheSize() {
        return cache.size();
    }

    private static RSAPublicKey fetchAndParse(URL certUrl) throws Exception {
        byte[] pemBytes;
        try (java.io.InputStream in = certUrl.openStream()) {
            pemBytes = in.readAllBytes();
        }
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) cf.generateCertificate(
                new ByteArrayInputStream(pemBytes));
        if (!(cert.getPublicKey() instanceof RSAPublicKey)) {
            throw new IllegalStateException(
                    "encryption_cert at " + certUrl + " has key type "
                    + cert.getPublicKey().getAlgorithm() + ", expected RSA");
        }
        return (RSAPublicKey) cert.getPublicKey();
    }

    /** Quiet down compiler warning about anonymous-LRU-map serialVersionUID. */
    @SuppressWarnings("unused")
    private static final long serialVersionUID = 1L;
}
