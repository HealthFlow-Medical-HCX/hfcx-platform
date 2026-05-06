# Known Leaked Credentials

This file documents historical credential leaks in the public commit history of
`HealthFlow-Medical-HCX/hfcx-platform`. It exists so:

1. New contributors and external auditors can find the context immediately
   rather than re-discovering each leak as a "new" finding.
2. Rotation status for each leaked credential is tracked in one place.

The CI workflow in `.github/workflows/maven.yml` runs an embedded-private-key
sweep (see `Block embedded private keys` step) that excludes this directory so
the documentation can describe the leak without tripping the rule.

## L-001 — Scheduler RSA private key (rotated <DATE>)

**Status:** Rotated and removed from default values. Historical commits still
contain the key but it no longer authorizes any role on the network.

**Origin:** Inherited from the upstream
`Swasth-Digital-Health-Foundation/hcx-platform` fork, where the same key was
(and remains) publicly visible. The key was the default value of
`${hcx_privateKey}` in
`hcx-scheduler-jobs/common-scheduler-job/src/main/resources/application.yml`
(line 3, on every commit before the v1.4 corrective sprint).

**Used for:** Signing internal notification events (cert-expiry warnings,
participant-validation-failure alerts) emitted by `ParticipantValidationScheduler`
and `CertExpiryWarningScheduler`. Not used for JWE encryption / decryption —
that custody is in `KeyCustodyClient` (Vault-backed in production per Gap N1).

**Reachability of the leaked key:** Any deployment that started without
overriding `hcx_privateKey` used this key to sign notifications — that includes
the upstream fork, every dev environment that copy-pasted the compose file, and
any smoke-test deployment on this branch before the v1.4 fix. The signed events
were observable in network audit logs; reversing the signature against this
publicly-known key is trivial.

**Rotation evidence:** PR #<NUMBER>, merged <DATE>. The new key pair is held
in HashiCorp Vault at `transit/keys/hfcx-scheduler-notification/`. The public
key was redistributed to all participants in the registry whose trust list
referenced the old key.

**Remediation in code:**

- The default value of `hcx_privateKey` was replaced with Spring's
  required-property syntax: `${hcx_privateKey:?...}`. Startup aborts with a
  clear message if the env var is unset.
- A regression-prevention CI step (`Block embedded private keys` in
  `.github/workflows/maven.yml`) blocks any commit that re-introduces a
  base64-encoded RSA/EC private key in source.
- The matching test fixture in
  `hcx-core/hcx-common/src/test/java/org/healthflow/common/helpers/EventGeneratorTest.java`
  was updated to generate a fresh ephemeral key inline rather than embedding
  the leaked one.

**Why no git-history rewrite:** the upstream Swasth public exposure means the
key was effectively public the moment this repository was forked from Swasth.
A force-push rewrite of our history would not restore secrecy that was never
there. Rotation is the meaningful mitigation; the leak is documented for
audit transparency.

## L-002, L-003, ...

(Add as discovered. Each entry follows the same template:
status / origin / used-for / reachability / rotation-evidence /
remediation-in-code / why-no-history-rewrite.)
