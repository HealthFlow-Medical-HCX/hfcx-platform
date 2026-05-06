#!/usr/bin/env bash
#
# Wrapper that builds the Egyptian FHIR IG package via SUSHI.
# Idempotent — safe to run repeatedly. Used by both Make-style local
# workflows and CI (.github/workflows/maven.yml) to ensure
# fhir/egyptian-ig/output/package.tgz exists before mvn package runs.
#
# Sourced from Gap N2 of the v1.3 remediation plan.
#
set -euo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
IG_DIR="$HERE/../fhir/egyptian-ig"

if [[ ! -d "$IG_DIR" ]]; then
  echo "ERROR: FHIR IG directory not found at $IG_DIR" >&2
  exit 2
fi

if ! command -v sushi >/dev/null; then
  if ! command -v npm >/dev/null; then
    echo "ERROR: npm is required to install fsh-sushi. Install Node.js 18+ first." >&2
    exit 2
  fi
  echo "Installing fsh-sushi globally (one-time)..." >&2
  npm install -g fsh-sushi
fi

bash "$IG_DIR/build.sh"

if [[ ! -f "$IG_DIR/output/package.tgz" ]]; then
  echo "ERROR: build.sh ran but $IG_DIR/output/package.tgz was not produced." >&2
  exit 3
fi

echo "FHIR IG package built at $IG_DIR/output/package.tgz"
