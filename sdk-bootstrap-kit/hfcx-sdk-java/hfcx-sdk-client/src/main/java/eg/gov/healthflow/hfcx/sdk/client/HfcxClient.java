package eg.gov.healthflow.hfcx.sdk.client;

/**
 * High-level entry point for HFCX integrators.
 *
 * <p><b>Sprint J1 — skeleton only.</b> The full builder, all five sender
 * methods (submitClaim, submitPreauth, checkEligibility, sendCommunication,
 * notifyPayment), correlation-ID handling, and Keycloak auth land in the
 * subsequent sprints:
 *
 * <ul>
 *   <li>J3 — builder + protocol-header construction + correlation IDs</li>
 *   <li>J4 — outbound encryption path (registry + JWE encrypt + POST)</li>
 *   <li>J5 — recipient handler + LocalKeyProvider</li>
 *   <li>J6 — exception hierarchy mapped to platform error codes</li>
 * </ul>
 *
 * <p>Until those sprints land, this class exists only to anchor the
 * {@code hfcx-sdk-client} module's package layout and signal to the IDE
 * that the SDK has a top-level entry point named {@code HfcxClient}.
 *
 * <p>Cross-SDK invariant: this class name is identical across all four
 * language SDKs (Java {@code HfcxClient}, Python {@code HfcxClient},
 * .NET {@code HfcxClient}, JavaScript {@code HfcxClient}). See
 * docs/CROSS_SDK_PARITY.md.
 */
public final class HfcxClient {

    private HfcxClient() {
        // No public construction yet — the builder lands in J3.
    }
}
