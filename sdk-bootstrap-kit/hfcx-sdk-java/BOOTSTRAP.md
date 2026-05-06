# Bootstrapping the hfcx-sdk-java repository

This kit is committed to the `hfcx-platform` repo at
`sdk-bootstrap-kit/hfcx-sdk-java/` because the agentic delivery
session that produced it has GitHub MCP scope restricted to the
platform repo and cannot create a new repository.

The procedure below converts this kit into a real, standalone GitHub
repository at `HealthFlow-Medical-HCX/hfcx-sdk-java`.

## Step 1 — Create the empty repository

In the GitHub UI, create `HealthFlow-Medical-HCX/hfcx-sdk-java` as
an empty public repository. Apache 2.0 license. No initial README,
no .gitignore, no README — we provide all of those.

## Step 2 — Copy the kit contents into the new repo

```bash
# From a fresh clone of hfcx-platform:
git clone git@github.com:HealthFlow-Medical-HCX/hfcx-platform.git
cd hfcx-platform

# Clone the new empty repo as a sibling:
git clone git@github.com:HealthFlow-Medical-HCX/hfcx-sdk-java.git ../hfcx-sdk-java

# Copy the kit. The dot at the end of -t copies hidden files
# (.github/, .gitignore) too.
cp -r sdk-bootstrap-kit/hfcx-sdk-java/. ../hfcx-sdk-java/

cd ../hfcx-sdk-java
git add .
git commit -m "chore: bootstrap hfcx-sdk-java from platform-side kit (Sprint J1)"
git push origin main
```

## Step 3 — Verify the build locally

```bash
cd ../hfcx-sdk-java
mvn -B clean verify
```

Expected output: BUILD SUCCESS. The `hfcx-sdk-core` module compiles, its
tests pass, and `hfcx-sdk-examples/BootstrapSmoke` round-trips a JWE.

## Step 4 — Sync the FHIR IG package

```bash
# Choose the platform release tag whose IG you want to bundle.
# The 1.0.0 SDK release ships the platform's 1.0.0 IG package.
bash fhir-ig/sync.sh v1.0.0
git add fhir-ig/
git commit -m "build: sync Egyptian FHIR IG from platform v1.0.0"
git push
```

## Step 5 — Sonatype OSSRH setup (TODO(human))

This is the credential plumbing the agent could not perform from inside
its sandbox. Do this once per repository:

1. **Create a Sonatype OSSRH account** at <https://central.sonatype.org/register/central-portal/>.
2. **Open a "New Project" ticket** to claim the `eg.gov.healthflow` group ID. Sonatype's review usually takes 1–2 business days; they verify domain ownership of `healthflow.gov.eg`.
3. **Generate a GPG key pair** offline:
   ```bash
   gpg --full-generate-key   # RSA 4096, your engineering email
   gpg --list-secret-keys --keyid-format=long
   gpg --export-secret-keys --armor <KEY-ID> > /tmp/gpg-private.asc
   gpg --keyserver keyserver.ubuntu.com --send-keys <KEY-ID>
   gpg --keyserver keys.openpgp.org --send-keys <KEY-ID>
   ```
4. **Wire the four secrets** into the repo at GitHub Settings → Secrets and variables → Actions:
   - `OSSRH_USERNAME` — your Sonatype OSSRH user name
   - `OSSRH_TOKEN` — Sonatype user token (NOT your password). Generate at <https://oss.sonatype.org/#profile;User%20Token>.
   - `GPG_PRIVATE_KEY` — the `/tmp/gpg-private.asc` contents.
   - `GPG_PASSPHRASE` — the GPG key's passphrase.
5. **Smoke-test the SNAPSHOT publish.** Run the publish workflow against a `v0.0.1-test` tag and confirm the artifact appears at <https://s01.oss.sonatype.org/content/repositories/snapshots/eg/gov/healthflow/hfcx-sdk-core/>.
6. After a successful SNAPSHOT publish, **delete the test tag** and proceed to J2.

## Step 6 — Branch protection on `main`

Match the platform repo's CONTRIBUTING.md:

- Require pull request reviews before merging.
- Require status checks: the `Test` workflow.
- Allow merge commits.
- Allow rebase merging.
- **Disallow squash merging** while the SDK is in active sprint development. Re-enable post-1.0.0 if you want.

## Step 7 — Open issue tracker

Issues are likely disabled by default. Enable them under Settings →
Features → Issues. Add labels: `sprint-J1` (closed), `sprint-J2`,
`sprint-J3`, ..., `sprint-J7`, `parity`.

## Step 8 — Delete the kit from the platform repo (post-merge)

After this kit has been moved into the new repo and the new repo's CI
is green, open a follow-up PR against `hfcx-platform` that deletes
`sdk-bootstrap-kit/hfcx-sdk-java/` (the kit has done its job) and
updates `docs/strategy/sdk-delivery-plan.md` Java-section `<LINK>` slots
with the new repo URL and tracking issue URLs.

## What this kit deliberately does NOT do

- It does not push to the new repo. That's a deliberate human-in-the-loop
  step.
- It does not create the GPG key. Keys must be generated on a secure
  workstation and the passphrase must never appear in agent transcripts.
- It does not register with Sonatype. Sonatype's review is a domain-
  ownership verification that requires a HealthFlow corporate email.
- It does not file the SDK 1.0.0 release on Maven Central. That's J7.

## Cross-reference

- Platform plan: `docs/strategy/sdk-delivery-plan.md`
- Agentic prompt: `docs/agentic-delivery-prompt.md` (this kit, committed)
- Decision 14: `docs/reviews/DECISION_14_ZERO_KNOWLEDGE_TRANSPORT.md` in the platform
