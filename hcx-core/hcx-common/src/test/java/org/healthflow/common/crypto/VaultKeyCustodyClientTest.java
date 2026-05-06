package org.healthflow.common.crypto;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.support.VaultResponse;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link VaultKeyCustodyClient}. Mocks {@link VaultOperations} so the
 * test runs with no Vault server. Focus is on the contract: which Vault paths
 * get read, how KV v1 vs v2 response shapes are handled, and that error paths
 * fail loud.
 */
public class VaultKeyCustodyClientTest {

    private static String pemEncodedPrivateKey;
    private static RSAPrivateKey expectedPrivateKey;
    private static RSAPublicKey freshRecipientPublicKey;

    private VaultOperations vault;
    private RecipientPublicKeyResolver resolver;
    private static final String KEY_PATH = "secret/data/hcx/local-key";

    @BeforeClass
    public static void generateTestKeys() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair kp = g.generateKeyPair();
        expectedPrivateKey = (RSAPrivateKey) kp.getPrivate();
        byte[] der = kp.getPrivate().getEncoded();
        pemEncodedPrivateKey = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der)
                + "\n-----END PRIVATE KEY-----";

        KeyPair recipientKp = g.generateKeyPair();
        freshRecipientPublicKey = (RSAPublicKey) recipientKp.getPublic();
    }

    @Before
    public void setUp() {
        vault = mock(VaultOperations.class);
        resolver = mock(RecipientPublicKeyResolver.class);
    }

    private static VaultResponse responseWithData(Map<String, Object> data) {
        VaultResponse r = new VaultResponse();
        r.setData(data);
        return r;
    }

    @Test
    public void getLocalPrivateKey_readsAndParsesKvV2Response() throws Exception {
        Map<String, Object> inner = new HashMap<>();
        inner.put("private_key_pem", pemEncodedPrivateKey);
        inner.put("expires_at_epoch_millis", 1735689600000L);
        Map<String, Object> outer = new HashMap<>();
        outer.put("data", inner); // KV v2 wraps under "data"
        when(vault.read(eq(KEY_PATH))).thenReturn(responseWithData(outer));

        VaultKeyCustodyClient client = new VaultKeyCustodyClient(vault, KEY_PATH, resolver);
        RSAPrivateKey actual = client.getLocalPrivateKey();

        assertNotNull(actual);
        assertEquals(expectedPrivateKey.getModulus(), actual.getModulus());
        verify(vault).read(KEY_PATH);
    }

    @Test
    public void getLocalPrivateKey_readsAndParsesKvV1FlatResponse() throws Exception {
        Map<String, Object> flat = new HashMap<>();
        flat.put("private_key_pem", pemEncodedPrivateKey);
        flat.put("expires_at_epoch_millis", 1735689600000L);
        when(vault.read(eq(KEY_PATH))).thenReturn(responseWithData(flat));

        VaultKeyCustodyClient client = new VaultKeyCustodyClient(vault, KEY_PATH, resolver);
        RSAPrivateKey actual = client.getLocalPrivateKey();

        assertNotNull(actual);
        assertEquals(expectedPrivateKey.getModulus(), actual.getModulus());
    }

    @Test
    public void getLocalKeyExpiry_returnsInstantFromVaultData() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("private_key_pem", pemEncodedPrivateKey);
        data.put("expires_at_epoch_millis", 1735689600000L);
        when(vault.read(eq(KEY_PATH))).thenReturn(responseWithData(data));

        VaultKeyCustodyClient client = new VaultKeyCustodyClient(vault, KEY_PATH, resolver);
        Instant expiry = client.getLocalKeyExpiry();

        assertEquals(Instant.ofEpochMilli(1735689600000L), expiry);
    }

    @Test
    public void getLocalKeyExpiry_acceptsStringEpoch() throws Exception {
        // Some Vault setups serialise numerics as strings depending on KV backend version.
        Map<String, Object> data = new HashMap<>();
        data.put("private_key_pem", pemEncodedPrivateKey);
        data.put("expires_at_epoch_millis", "1735689600000");
        when(vault.read(eq(KEY_PATH))).thenReturn(responseWithData(data));

        VaultKeyCustodyClient client = new VaultKeyCustodyClient(vault, KEY_PATH, resolver);
        assertEquals(Instant.ofEpochMilli(1735689600000L), client.getLocalKeyExpiry());
    }

    @Test
    public void getRecipientPublicKey_delegatesToResolver() throws Exception {
        VaultKeyCustodyClient client = new VaultKeyCustodyClient(vault, KEY_PATH, resolver);
        when(resolver.resolve("payer-001")).thenReturn(freshRecipientPublicKey);

        RSAPublicKey actual = client.getRecipientPublicKey("payer-001");

        assertEquals(freshRecipientPublicKey, actual);
        verify(resolver).resolve("payer-001");
        // Critically: NO Vault read for recipient keys. Per Decision 14, recipient
        // public keys come from the registry, not Vault.
        org.mockito.Mockito.verifyNoInteractions(vault);
    }

    @Test
    public void missingPemFieldFailsLoud() {
        Map<String, Object> data = new HashMap<>();
        data.put("expires_at_epoch_millis", 1L);
        // private_key_pem missing
        when(vault.read(eq(KEY_PATH))).thenReturn(responseWithData(data));

        VaultKeyCustodyClient client = new VaultKeyCustodyClient(vault, KEY_PATH, resolver);
        try {
            client.getLocalPrivateKey();
            fail("expected IllegalStateException for missing private_key_pem");
        } catch (Exception e) {
            assertTrue("got: " + e.getMessage(), e.getMessage().contains("private_key_pem"));
        }
    }

    @Test
    public void missingExpiryFieldFailsLoud() {
        Map<String, Object> data = new HashMap<>();
        data.put("private_key_pem", pemEncodedPrivateKey);
        // expires_at_epoch_millis missing
        when(vault.read(eq(KEY_PATH))).thenReturn(responseWithData(data));

        VaultKeyCustodyClient client = new VaultKeyCustodyClient(vault, KEY_PATH, resolver);
        try {
            client.getLocalKeyExpiry();
            fail("expected IllegalStateException for missing expires_at_epoch_millis");
        } catch (Exception e) {
            assertTrue("got: " + e.getMessage(), e.getMessage().contains("expires_at_epoch_millis"));
        }
    }

    @Test
    public void vaultPathNotFoundFailsLoud() {
        when(vault.read(eq(KEY_PATH))).thenReturn(null);
        VaultKeyCustodyClient client = new VaultKeyCustodyClient(vault, KEY_PATH, resolver);
        try {
            client.getLocalPrivateKey();
            fail("expected IllegalStateException for missing secret");
        } catch (Exception e) {
            assertTrue("got: " + e.getMessage(), e.getMessage().contains(KEY_PATH));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructorRejectsNullVault() {
        new VaultKeyCustodyClient(null, KEY_PATH, resolver);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructorRejectsBlankPath() {
        new VaultKeyCustodyClient(vault, "  ", resolver);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructorRejectsNullResolver() {
        new VaultKeyCustodyClient(vault, KEY_PATH, null);
    }
}
