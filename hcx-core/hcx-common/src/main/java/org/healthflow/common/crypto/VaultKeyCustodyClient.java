package org.healthflow.common.crypto;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;

/**
 * Vault-backed {@link KeyCustodyClient}.
 *
 * <p><b>Skeleton — not yet wired.</b> The plan calls for HashiCorp Vault as the
 * dev/staging key-custody backend, with Spring Cloud Vault integration. The
 * scaffolding for that already exists under {@code phase3/security/vault-config.hcl}
 * and {@code application-vault.yml}. Wiring this to a live Vault Transit /
 * KV-v2 backend is its own focused commit (Spring Cloud Vault dependency,
 * VaultTemplate bean, key-path configuration, integration tests against a
 * dev-mode Vault container). All call sites should depend on the
 * {@link KeyCustodyClient} interface so the only change at integration time
 * is wiring this bean instead of {@link FileKeyCustodyClient}.
 *
 * <p>Until the wiring lands, all methods throw {@link UnsupportedOperationException}
 * so misconfiguration in production fails loud rather than silently degrading
 * to in-process keys.
 */
public class VaultKeyCustodyClient implements KeyCustodyClient {

    @Override
    public RSAPublicKey getRecipientPublicKey(String participantCode) {
        throw new UnsupportedOperationException(
                "VaultKeyCustodyClient is a skeleton; the Spring Cloud Vault integration "
                + "is queued as a follow-up commit on PR #7. Use FileKeyCustodyClient for "
                + "dev or wait for the Vault wiring.");
    }

    @Override
    public RSAPrivateKey getLocalPrivateKey() {
        throw new UnsupportedOperationException(
                "VaultKeyCustodyClient is a skeleton; the Spring Cloud Vault integration "
                + "is queued as a follow-up commit on PR #7.");
    }

    @Override
    public Instant getLocalKeyExpiry() {
        throw new UnsupportedOperationException(
                "VaultKeyCustodyClient is a skeleton; the Spring Cloud Vault integration "
                + "is queued as a follow-up commit on PR #7.");
    }
}
