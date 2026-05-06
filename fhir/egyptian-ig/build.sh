#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if ! command -v sushi >/dev/null; then
  echo "ERROR: sushi (fsh-sushi) not installed. Install: npm install -g fsh-sushi" >&2; exit 2
fi
if ! command -v java >/dev/null; then
  echo "ERROR: java required for HL7 IG Publisher" >&2; exit 2
fi
sushi .
PUBLISHER_JAR="${IG_PUBLISHER_JAR:-input-cache/publisher.jar}"
mkdir -p input-cache
if [[ ! -f "$PUBLISHER_JAR" ]]; then
  echo "Downloading HL7 IG Publisher..."
  curl -fsSL -o "$PUBLISHER_JAR" https://github.com/HL7/fhir-ig-publisher/releases/latest/download/publisher.jar
fi
java -jar "$PUBLISHER_JAR" -ig ig.ini
echo "Build complete. Output at output/package.tgz"
