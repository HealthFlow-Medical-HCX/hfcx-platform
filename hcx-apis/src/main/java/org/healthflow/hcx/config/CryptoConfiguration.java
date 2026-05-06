package org.healthflow.hcx.config;

import org.healthflow.common.crypto.FileKeyCustodyClient;
import org.healthflow.common.crypto.KeyCustodyClient;
import org.healthflow.common.crypto.VaultKeyCustodyClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.nio.file.Paths;
import java.time.Duration;

/**
 * KeyCustodyClient bean wiring.
 *
 * <p>Profiles:
 * <ul>
 *   <li>{@code prod}, {@code staging} → {@link VaultKeyCustodyClient}.
 *       Production startup requires {@code VAULT_TOKEN} and {@code VAULT_ADDR}
 *       to be set; the client constructor fails fast if either is missing.</li>
 *   <li>any other profile (default {@code dev}) → {@link FileKeyCustodyClient}.
 *       The dev key path defaults to {@code ./keys/local-private.pem}; tests
 *       typically inject a generated key directly via the public-key cache
 *       seeding helpers on FileKeyCustodyClient.</li>
 * </ul>
 *
 * <p>Per Decision 14, this bean only ever resolves at the recipient HCX-API
 * instance. The api-gateway module deliberately does not import this
 * configuration.
 */
@Configuration
public class CryptoConfiguration {

    @Value("${crypto.vault.addr:${VAULT_ADDR:}}")
    private String vaultAddr;

    @Value("${crypto.vault.token:${VAULT_TOKEN:}}")
    private String vaultToken;

    @Value("${crypto.vault.localKeyPath:secret/data/hfcx/local/private}")
    private String localKeyPath;

    @Value("${registry.basePath:http://localhost:8081}")
    private String registryBasePath;

    @Value("${registry.apiPath:/api/v1/Organisation/}")
    private String registryApiPath;

    @Value("${crypto.vault.cache.ttl:PT1H}")
    private Duration cacheTtl;

    @Value("${crypto.file.privateKeyPath:./keys/local-private.pem}")
    private String filePrivateKeyPath;

    @Value("${crypto.file.expiryEpochMillis:#{T(java.lang.Long).MAX_VALUE}}")
    private long fileExpiryMillis;

    @Bean
    @Profile({"prod", "production", "staging"})
    public KeyCustodyClient vaultKeyCustodyClient() {
        return new VaultKeyCustodyClient(
                vaultAddr,
                vaultToken,
                localKeyPath,
                registryBasePath,
                registryApiPath,
                cacheTtl);
    }

    @Bean
    @Profile({"!prod & !production & !staging"})
    public KeyCustodyClient fileKeyCustodyClient(@Value("${spring.profiles.active:dev}") String activeProfile)
            throws Exception {
        return new FileKeyCustodyClient(
                activeProfile,
                Paths.get(filePrivateKeyPath),
                fileExpiryMillis);
    }
}
