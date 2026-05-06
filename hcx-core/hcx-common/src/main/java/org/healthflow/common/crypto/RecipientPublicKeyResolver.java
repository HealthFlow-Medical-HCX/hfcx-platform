package org.healthflow.common.crypto;

import java.security.interfaces.RSAPublicKey;

/**
 * Resolves a participant's RSA public key by participant code.
 *
 * <p>Per Decision 14 (zero-knowledge transport, see
 * {@code docs/reviews/DECISION_14_ZERO_KNOWLEDGE_TRANSPORT.md}), recipient
 * public keys live in the registry's {@code encryption_cert} field — NOT
 * in Vault, NOT in an HSM. The platform is a routing fabric and does not
 * decrypt; only senders need recipient public keys, and they get them from
 * the registry. This interface formalises that boundary so the
 * {@link KeyCustodyClient} hierarchy doesn't conflate two concerns.
 *
 * <p>Implementations should cache aggressively — a participant cert change
 * is rare and the registry round-trip per dispatch would crush throughput.
 * The {@link RegistryRecipientPublicKeyResolver} ships with a small
 * in-process LRU; production deployments may layer Redis on top.
 */
public interface RecipientPublicKeyResolver {

    /**
     * @param participantCode the recipient's HCX participant code
     * @return the recipient's RSA public key, parsed from the X.509 cert
     *         pointed at by their registry {@code encryption_cert} field
     * @throws Exception if the participant is unknown, the cert is unreachable,
     *                   or the cert cannot be parsed as an RSA public key
     */
    RSAPublicKey resolve(String participantCode) throws Exception;
}
