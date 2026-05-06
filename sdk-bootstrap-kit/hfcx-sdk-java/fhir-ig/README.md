# Egyptian FHIR Implementation Guide (bundled)

This directory ships the platform's Egyptian FHIR R4 IG package alongside
the SDK so consumers don't need to fetch it separately.

## Files

- `egyptian-ig.tgz` — the SUSHI-built NPM package, sourced from a
  platform release. Updated by `sync.sh`.
- `PLATFORM_VERSION` — the platform release tag whose IG is currently
  bundled (e.g. `v1.0.0`).
- `PLATFORM_VERSION.sha256` — SHA-256 of the bundled `egyptian-ig.tgz`.
  A mismatch between this and the artifact's actual SHA on a re-sync
  signals that the upstream release artifact changed — investigate
  before committing.

## Sync procedure

```bash
bash fhir-ig/sync.sh v1.0.0
git add fhir-ig/{egyptian-ig.tgz,PLATFORM_VERSION,PLATFORM_VERSION.sha256}
git commit -m "build: sync Egyptian FHIR IG from platform v1.0.0"
```

## Compatibility rule

SDK 1.x ships platform 1.x's IG. The SDK refuses to load an IG version
whose major version differs from the SDK's; cross-major use logs a WARN
and falls back to base R4 (Egyptian profile constraints inert).

## J1 status

J1 ships an empty placeholder. The first real IG sync happens in J5
(Recipient handler) when the SDK first needs to validate inbound
Bundles against the IG.
