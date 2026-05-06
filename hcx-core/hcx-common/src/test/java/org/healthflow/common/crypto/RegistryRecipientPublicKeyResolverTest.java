package org.healthflow.common.crypto;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests for {@link RegistryRecipientPublicKeyResolver}.
 *
 * <p>Uses a baked-in self-signed RSA-2048 X.509 certificate as the test
 * fixture. The cert is real and valid (parses cleanly with HAPI's
 * CertificateFactory), so the resolver's fetch + parse path runs end-to-end
 * without an external HTTP fetch and without any JDK-internal hackery for
 * cert generation.
 *
 * <p>The fixture cert was generated with OpenSSL once, off-line, and inlined
 * here so the test is hermetic. If you ever need to regenerate, run:
 *
 * <pre>
 *   openssl req -x509 -newkey rsa:2048 \
 *           -keyout /tmp/tk.key -out /tmp/tk.crt \
 *           -sha256 -days 3650 -nodes \
 *           -subj "/CN=hcx-test/O=HealthFlow/C=EG"
 * </pre>
 */
public class RegistryRecipientPublicKeyResolverTest {

    /**
     * Baked-in self-signed RSA-2048 cert. {@code subject = CN=hcx-test, O=HealthFlow, C=EG}.
     * Validity window 10 years from generation. Used purely as test material —
     * no system depends on the modulus or signature.
     */
    private static final String FIXTURE_CERT_PEM =
            "-----BEGIN CERTIFICATE-----\n" +
            "MIIDSzCCAjOgAwIBAgIUfJ+bpdIQuY6Koh9o19rfjQj/tvUwDQYJKoZIhvcNAQEL\n" +
            "BQAwNTERMA8GA1UEAwwIaGN4LXRlc3QxEzARBgNVBAoMCkhlYWx0aEZsb3cxCzAJ\n" +
            "BgNVBAYTAkVHMB4XDTI2MDUwNjAxMTUyMVoXDTM2MDUwMzAxMTUyMVowNTERMA8G\n" +
            "A1UEAwwIaGN4LXRlc3QxEzARBgNVBAoMCkhlYWx0aEZsb3cxCzAJBgNVBAYTAkVH\n" +
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAq0zqAZYgPHRgtvtCxxil\n" +
            "EMIi+UJTQDn4e8nHOnSPUHQhjjiPpiFYcKBya8OgujM/KylS+PwSjIQG8eO5XvsI\n" +
            "x1S4GywWZiQTzgbKeSkZyDEvFSvvqHDPBKB73IzjWVAgMHo6Wrj5OPJp6FimasCt\n" +
            "9M69yL3zYs1zqdvrbGyswgQFNDac481ibrk4X0MbOUWqBFWZ4DnaNABt8os7sSz8\n" +
            "AHpe+EgJJ2pEZwt77O3zWIHZfD9ojm4hgb7HjqhoUIS78vtSYOjqZrOXi7YTCKzY\n" +
            "UQsEw//uK7tZpWcoCBIRMhtuMyJjxEMUfizJEwKyDYeJw9hoyIBLSKI/L1eGJC/l\n" +
            "+QIDAQABo1MwUTAdBgNVHQ4EFgQUZMQASCjP6zcO/DEOj9LCcdlYljAwHwYDVR0j\n" +
            "BBgwFoAUZMQASCjP6zcO/DEOj9LCcdlYljAwDwYDVR0TAQH/BAUwAwEB/zANBgkq\n" +
            "hkiG9w0BAQsFAAOCAQEAUkwcX9I7YzWkwA3Ymna69SUShKlHek88nvMlWjzTbbvP\n" +
            "itJacURlL1jPeFkTFfSaQIhSVRzOi13iWt6P0t2GykWy1p/390ZfGBCUsyMIfNH2\n" +
            "Q4gmDi/m+ailP2gEjXq1eaabbOrd9x5VH18VLsW50bKh4HdgKo4rJiOoyry+9MUd\n" +
            "GBTPxIBg7yfUbxtFA/SwaIpMkwhSxjI6qDm8swVd6qAnkjqyTI0GA9bm5ny8TKNo\n" +
            "BIS13uWqxSky4HQ7dgEJS1AV+gzP73p7oWC2rCUik8pjElRg50OzbZdutJVZ7Wrt\n" +
            "qAteBlAmMmn0pryS30supIVrQlOM/B0qnKvOP5ZRrw==\n" +
            "-----END CERTIFICATE-----\n";

    private static RSAPublicKey expectedPublicKey;

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File pemFile;

    @BeforeClass
    public static void parseFixtureCertOnce() throws Exception {
        X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(
                        FIXTURE_CERT_PEM.getBytes(StandardCharsets.UTF_8)));
        expectedPublicKey = (RSAPublicKey) cert.getPublicKey();
        // Sanity: the resolver compares moduli; if the fixture ever stops producing
        // a 2048-bit RSA key we want this assertion to fail at @BeforeClass rather
        // than mid-test with a confusing message.
        assertEquals(2048, expectedPublicKey.getModulus().bitLength());
        assertEquals(BigInteger.valueOf(65537), expectedPublicKey.getPublicExponent());
    }

    @Before
    public void writeCertToTempFile() throws Exception {
        pemFile = tmp.newFile("recipient-001.pem");
        Files.write(pemFile.toPath(), FIXTURE_CERT_PEM.getBytes(StandardCharsets.UTF_8));
    }

    private RegistryRecipientPublicKeyResolver newResolver(AtomicInteger fetchCounter) {
        return new RegistryRecipientPublicKeyResolver(participantCode -> {
            fetchCounter.incrementAndGet();
            return pemFile.toURI().toURL();
        });
    }

    @Test
    public void resolve_fetchesParsesAndReturnsPublicKey() throws Exception {
        AtomicInteger fetches = new AtomicInteger();
        RegistryRecipientPublicKeyResolver resolver = newResolver(fetches);

        RSAPublicKey actual = resolver.resolve("payer-001");

        assertNotNull(actual);
        assertEquals(expectedPublicKey.getModulus(), actual.getModulus());
        assertEquals(BigInteger.valueOf(65537), actual.getPublicExponent());
        assertEquals(1, fetches.get());
        assertEquals(1, resolver.cacheSize());
    }

    @Test
    public void resolve_cachesSubsequentLookups() throws Exception {
        AtomicInteger fetches = new AtomicInteger();
        RegistryRecipientPublicKeyResolver resolver = newResolver(fetches);

        resolver.resolve("payer-001");
        resolver.resolve("payer-001");
        resolver.resolve("payer-001");

        assertEquals("expected exactly one fetch despite three lookups", 1, fetches.get());
    }

    @Test
    public void evict_removesEntryFromCache() throws Exception {
        AtomicInteger fetches = new AtomicInteger();
        RegistryRecipientPublicKeyResolver resolver = newResolver(fetches);

        resolver.resolve("payer-001");
        assertEquals(1, fetches.get());
        resolver.evict("payer-001");
        resolver.resolve("payer-001");
        assertEquals("eviction should force a re-fetch", 2, fetches.get());
    }

    @Test
    public void lruBound_evictsOldestEntries() throws Exception {
        AtomicInteger fetches = new AtomicInteger();
        RegistryRecipientPublicKeyResolver resolver = new RegistryRecipientPublicKeyResolver(
                code -> {
                    fetches.incrementAndGet();
                    return pemFile.toURI().toURL();
                }, 2);

        resolver.resolve("p1");
        resolver.resolve("p2");
        assertEquals(2, resolver.cacheSize());
        resolver.resolve("p3"); // should evict p1 (LRU access-order)
        assertEquals(2, resolver.cacheSize());
        assertEquals(3, fetches.get());

        resolver.resolve("p1"); // evicted -> re-fetch
        assertEquals(4, fetches.get());
    }

    @Test
    public void resolve_rejectsBlankParticipantCode() {
        RegistryRecipientPublicKeyResolver resolver = newResolver(new AtomicInteger());
        try {
            resolver.resolve("  ");
            fail("expected IllegalArgumentException for blank participant code");
        } catch (Exception e) {
            // expected
        }
    }

    @Test
    public void resolve_failsLoudWhenLookupReturnsNull() {
        RegistryRecipientPublicKeyResolver resolver = new RegistryRecipientPublicKeyResolver(
                code -> null);
        try {
            resolver.resolve("unknown");
            fail("expected IllegalStateException for missing encryption_cert");
        } catch (Exception e) {
            assertTrue("got: " + e.getMessage(), e.getMessage().contains("unknown"));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructorRejectsNullLookup() {
        new RegistryRecipientPublicKeyResolver(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructorRejectsZeroCacheSize() {
        new RegistryRecipientPublicKeyResolver(c -> null, 0);
    }
}
