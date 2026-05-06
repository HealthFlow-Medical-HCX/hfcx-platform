#!/usr/bin/env bash
#
# Build the HealthFlow Egyptian FHIR R4 Implementation Guide.
#
# Two output modes:
#   default  - run SUSHI and package the resources as an NPM-shaped tgz
#              (fast; ~5 seconds; what the platform runtime consumes via
#              FhirValidationService -> NpmPackageValidationSupport).
#   --html   - additionally run the HL7 IG Publisher to render browsable
#              HTML. Slow (downloads ~100 MB jar) and not used in CI; for
#              local previewing.
#
# Outputs:
#   output/package.tgz  - NPM package consumed by hcx-apis at runtime
#   output/             - resources, package.json (always)
#   output/site/        - browsable HTML (with --html only)
#
set -euo pipefail
cd "$(dirname "$0")"

if ! command -v sushi >/dev/null; then
  echo "ERROR: sushi (fsh-sushi) not installed. Install: npm install -g fsh-sushi" >&2
  exit 2
fi

echo "Running SUSHI..."
sushi .

# Package the SUSHI-generated FHIR resources as an NPM-shaped tgz.
# Per https://confluence.hl7.org/display/FHIR/NPM+Package+Specification, the tgz
# must have a top-level "package/" directory containing package.json plus the
# resource JSONs. HAPI-FHIR's NpmPackageValidationSupport reads this format.
echo "Packaging resources into output/package.tgz..."
rm -rf output
mkdir -p output/package

# Read identity from sushi-config.yaml. Avoid yq/jq hard deps; tiny grep is fine
# because the values are well-formed strings on their own lines.
IG_ID=$(grep -E '^id:' sushi-config.yaml | head -1 | awk '{print $2}')
IG_VERSION=$(grep -E '^version:' sushi-config.yaml | head -1 | awk '{print $2}')
IG_NAME=$(grep -E '^name:' sushi-config.yaml | head -1 | awk '{print $2}')
IG_CANONICAL=$(grep -E '^canonical:' sushi-config.yaml | head -1 | awk '{print $2}')
IG_FHIRVER=$(grep -E '^fhirVersion:' sushi-config.yaml | head -1 | awk '{print $2}')

cat > output/package/package.json <<EOF
{
  "name": "${IG_ID}",
  "version": "${IG_VERSION}",
  "title": "HealthFlow Egyptian FHIR Implementation Guide",
  "description": "Profiles, value sets, and code systems for the Egyptian HCX",
  "canonical": "${IG_CANONICAL}",
  "url": "${IG_CANONICAL}",
  "fhirVersions": ["${IG_FHIRVER}"],
  "dependencies": {
    "hl7.fhir.r4.core": "${IG_FHIRVER}"
  },
  "type": "fhir.ig"
}
EOF

# Copy the SUSHI-generated resources into the package directory.
cp fsh-generated/resources/*.json output/package/

# Create the tgz at the conventional NPM layout.
( cd output && tar --owner=0 --group=0 -czf package.tgz package )

echo "Build complete. Output at:"
echo "  - output/package.tgz   (NPM-shaped, consumed by hcx-apis runtime)"
echo "  - output/package/      (raw resources + package.json)"

# Optional: render the browsable HTML site via the HL7 IG Publisher.
if [[ "${1:-}" == "--html" ]]; then
  if ! command -v java >/dev/null; then
    echo "ERROR: java required for HL7 IG Publisher" >&2
    exit 2
  fi
  PUBLISHER_JAR="${IG_PUBLISHER_JAR:-input-cache/publisher.jar}"
  mkdir -p input-cache input/includes input/pagecontent
  if [[ ! -f input/includes/menu.xml ]]; then
    cat > input/includes/menu.xml <<'XML'
<ul xmlns="http://www.w3.org/1999/xhtml" class="nav navbar-nav">
  <li><a href="index.html">Home</a></li>
  <li><a href="artifacts.html">Artifacts</a></li>
</ul>
XML
  fi
  if [[ ! -f input/pagecontent/index.md ]]; then
    cat > input/pagecontent/index.md <<'MD'
# HealthFlow Egyptian FHIR Implementation Guide

Profiles, value sets, and code systems for the HealthFlow HCX in Egypt.
This is a starter IG. Clinical informaticists refine value sets and bindings.
MD
  fi
  if [[ ! -f "$PUBLISHER_JAR" ]]; then
    echo "Downloading HL7 IG Publisher..."
    curl -fsSL -o "$PUBLISHER_JAR" \
      https://github.com/HL7/fhir-ig-publisher/releases/latest/download/publisher.jar
  fi
  java -jar "$PUBLISHER_JAR" -ig ig.ini
fi
