#!/usr/bin/env bash
#
# Syncs the Egyptian FHIR IG package from the platform repo's release
# artifact. Run when a new platform release lands; commit the result.
#
# Usage:
#   bash fhir-ig/sync.sh <platform-version-tag>     e.g. v1.0.0
#
# Per Section 8 of the agentic delivery prompt, every SDK ships the
# IG version of its corresponding platform release. SDK 1.0.0 ships
# platform 1.0.0's IG; SDK 1.1.0 ships platform 1.1.x's IG.
set -euo pipefail

PLATFORM_VERSION="${1:?usage: sync.sh <platform-version-tag>}"

# Where the platform's CI uploads the SUSHI-built package.tgz. The
# fhir-ig-build workflow's artifact is named 'healthflow-egyptian-ig-package'
# and contains 'package.tgz' at the root. For the 1.0.0 release the
# artifact is also attached to the GitHub Release as 'egyptian-ig.tgz'.
URL="https://github.com/HealthFlow-Medical-HCX/hfcx-platform/releases/download/${PLATFORM_VERSION}/egyptian-ig.tgz"

HERE="$(cd "$(dirname "$0")" && pwd)"
DEST="$HERE/egyptian-ig.tgz"

echo "Fetching $URL ..."
curl -fLo "$DEST" "$URL"

# Record the sourced platform version next to the artifact so consumers
# can verify which version's profiles their build was compiled against.
echo "$PLATFORM_VERSION" > "$HERE/PLATFORM_VERSION"

# Record the SHA-256 of the artifact too so the next sync can detect
# whether the upstream artifact changed for the same tag (which would
# itself be a signal that something is wrong in the platform's release
# process).
if command -v sha256sum >/dev/null 2>&1; then
  sha256sum "$DEST" | awk '{print $1}' > "$HERE/PLATFORM_VERSION.sha256"
elif command -v shasum >/dev/null 2>&1; then
  shasum -a 256 "$DEST" | awk '{print $1}' > "$HERE/PLATFORM_VERSION.sha256"
fi

echo
echo "Synced Egyptian FHIR IG from platform $PLATFORM_VERSION"
echo "  Artifact:    $DEST"
echo "  Version pin: $HERE/PLATFORM_VERSION"
[[ -f "$HERE/PLATFORM_VERSION.sha256" ]] && echo "  SHA-256:     $(cat "$HERE/PLATFORM_VERSION.sha256")"
echo
echo "Next: commit $HERE/{egyptian-ig.tgz,PLATFORM_VERSION,PLATFORM_VERSION.sha256}"
echo "      and bump the SDK CHANGELOG."
