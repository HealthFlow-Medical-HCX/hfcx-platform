#!/bin/bash
#
# HCX Egypt Contextualization Script
# Automated refactoring to replace Swasth/India references with HealthFlow/Egypt
#
# Usage: ./contextualize-codebase.sh [--dry-run] [--repo-path /path/to/repo]
#

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Default values
DRY_RUN=false
REPO_PATH="/home/ubuntu/hcx-egypt"
LOG_FILE="/home/ubuntu/hcx-egypt/phase1/logs/contextualization-$(date +%Y%m%d_%H%M%S).log"

# Parse arguments
while [[ $# -gt 0 ]]; do
  case $1 in
    --dry-run)
      DRY_RUN=true
      shift
      ;;
    --repo-path)
      REPO_PATH="$2"
      shift 2
      ;;
    *)
      echo "Unknown option: $1"
      exit 1
      ;;
  esac
done

# Create log directory
mkdir -p "$(dirname "$LOG_FILE")"

# Logging function
log() {
  echo -e "${BLUE}[$(date '+%Y-%m-%d %H:%M:%S')]${NC} $1" | tee -a "$LOG_FILE"
}

log_success() {
  echo -e "${GREEN}[SUCCESS]${NC} $1" | tee -a "$LOG_FILE"
}

log_warning() {
  echo -e "${YELLOW}[WARNING]${NC} $1" | tee -a "$LOG_FILE"
}

log_error() {
  echo -e "${RED}[ERROR]${NC} $1" | tee -a "$LOG_FILE"
}

# Backup function
create_backup() {
  local backup_dir="${REPO_PATH}/backups/pre-contextualization-$(date +%Y%m%d_%H%M%S)"
  log "Creating backup at: $backup_dir"
  
  mkdir -p "$backup_dir"
  
  # Backup all repositories
  for repo in hfcx-platform hcx-apps hcx-specs hcx-ig hcx-integrator hcx-protocol; do
    if [ -d "${REPO_PATH}/${repo}" ]; then
      cp -r "${REPO_PATH}/${repo}" "$backup_dir/"
      log_success "Backed up: $repo"
    fi
  done
  
  echo "$backup_dir" > "${REPO_PATH}/.last_backup"
  log_success "Backup completed: $backup_dir"
}

# Function to replace text in files
replace_in_files() {
  local pattern="$1"
  local replacement="$2"
  local file_pattern="$3"
  local description="$4"
  
  log "Replacing: $description"
  log "  Pattern: $pattern -> $replacement"
  log "  Files: $file_pattern"
  
  local count=0
  
  if [ "$DRY_RUN" = true ]; then
    count=$(find "$REPO_PATH" -type f -name "$file_pattern" -exec grep -l "$pattern" {} \; 2>/dev/null | wc -l)
    log_warning "[DRY RUN] Would modify $count files"
  else
    # Use find and sed to replace in files
    while IFS= read -r file; do
      if grep -q "$pattern" "$file" 2>/dev/null; then
        sed -i "s|$pattern|$replacement|g" "$file"
        ((count++))
        log "  Modified: $file"
      fi
    done < <(find "$REPO_PATH" -type f -name "$file_pattern" 2>/dev/null)
    
    log_success "Modified $count files"
  fi
  
  echo "$count" >> "${LOG_FILE}.stats"
}

# Function to rename Java packages
rename_java_packages() {
  log "Renaming Java packages: org.swasth.* -> org.healthflow.*"
  
  if [ "$DRY_RUN" = true ]; then
    local count=$(find "$REPO_PATH" -type f -name "*.java" -exec grep -l "package org\.swasth" {} \; 2>/dev/null | wc -l)
    log_warning "[DRY RUN] Would rename packages in $count Java files"
    return
  fi
  
  local count=0
  
  # Step 1: Update package declarations
  while IFS= read -r file; do
    if grep -q "package org\.swasth" "$file" 2>/dev/null; then
      sed -i 's/package org\.swasth/package org.healthflow/g' "$file"
      ((count++))
    fi
  done < <(find "$REPO_PATH" -type f -name "*.java" 2>/dev/null)
  
  # Step 2: Update import statements
  while IFS= read -r file; do
    if grep -q "import org\.swasth" "$file" 2>/dev/null; then
      sed -i 's/import org\.swasth/import org.healthflow/g' "$file"
    fi
  done < <(find "$REPO_PATH" -type f -name "*.java" 2>/dev/null)
  
  # Step 3: Move directory structure
  for repo in hfcx-platform hcx-apps hcx-protocol; do
    local src_dir="${REPO_PATH}/${repo}/src/main/java/org/swasth"
    local dest_dir="${REPO_PATH}/${repo}/src/main/java/org/healthflow"
    
    if [ -d "$src_dir" ]; then
      mkdir -p "$(dirname "$dest_dir")"
      mv "$src_dir" "$dest_dir"
      log_success "Moved package directory: $repo"
    fi
  done
  
  log_success "Renamed Java packages in $count files"
}

# Function to update Maven configurations
update_maven_configs() {
  log "Updating Maven pom.xml files"
  
  replace_in_files \
    "<groupId>org\.swasth</groupId>" \
    "<groupId>org.healthflow</groupId>" \
    "pom.xml" \
    "Maven group IDs"
  
  replace_in_files \
    "<artifactId>swasth-" \
    "<artifactId>healthflow-" \
    "pom.xml" \
    "Maven artifact IDs"
  
  replace_in_files \
    "<name>Swasth " \
    "<name>HealthFlow " \
    "pom.xml" \
    "Maven project names"
}

# Function to update API endpoints
update_api_endpoints() {
  log "Updating API endpoints"
  
  # Update domain names
  replace_in_files \
    "dev-hcx\.swasth\.app" \
    "dev-hcx.healthflow.eg" \
    "*.{yml,yaml,properties,json,js,jsx,ts,tsx}" \
    "Development API endpoints"
  
  replace_in_files \
    "staging-hcx\.swasth\.app" \
    "staging-hcx.healthflow.eg" \
    "*.{yml,yaml,properties,json,js,jsx,ts,tsx}" \
    "Staging API endpoints"
  
  replace_in_files \
    "prod-hcx\.swasth\.app" \
    "hcx.healthflow.eg" \
    "*.{yml,yaml,properties,json,js,jsx,ts,tsx}" \
    "Production API endpoints"
  
  replace_in_files \
    "docs\.swasth\.app" \
    "docs.healthflow.eg" \
    "*.{yml,yaml,properties,json,md,html}" \
    "Documentation endpoints"
}

# Function to update Keycloak configuration
update_keycloak_config() {
  log "Updating Keycloak configuration"
  
  # Gap V3 v1.4: corrected realm slug. Earlier runs of this script wrote
  # 'healthflow-hcx-egypt' which did not match the realm declared in
  # deployment/keycloak/hcx-egypt-realm.json. The realm slug is 'hcx-egypt';
  # the participant instance name (a different concept) is
  # 'healthflow-hcx-egypt' and remains unchanged elsewhere.
  replace_in_files \
    "swasth-health-claim-exchange" \
    "hcx-egypt" \
    "*.{yml,yaml,properties,json}" \
    "Keycloak realm names"
  
  replace_in_files \
    "swasth-hcx-gateway" \
    "healthflow-hcx-gateway" \
    "*.{yml,yaml,properties,json}" \
    "Keycloak client IDs"
}

# Function to update frontend branding
update_frontend_branding() {
  log "Updating frontend branding"
  
  replace_in_files \
    "Swasth HCX" \
    "HealthFlow HCX Egypt" \
    "*.{js,jsx,ts,tsx,html,json}" \
    "Application titles"
  
  replace_in_files \
    "Swasth Health Claim Exchange" \
    "HealthFlow Health Claim Exchange - Egypt" \
    "*.{js,jsx,ts,tsx,html,md}" \
    "Application descriptions"
  
  # Update package.json names
  replace_in_files \
    '"name": "swasth-' \
    '"name": "healthflow-' \
    "package.json" \
    "NPM package names"
}

# Function to update documentation
update_documentation() {
  log "Updating documentation"
  
  replace_in_files \
    "Swasth" \
    "HealthFlow" \
    "*.md" \
    "Markdown documentation - Swasth references"
  
  replace_in_files \
    "India" \
    "Egypt" \
    "*.md" \
    "Markdown documentation - India references"
  
  replace_in_files \
    "IRDAI" \
    "FRA (Financial Regulatory Authority)" \
    "*.md" \
    "Regulatory body references"
  
  replace_in_files \
    "NHA\|ABDM" \
    "MoHP (Ministry of Health and Population)" \
    "*.md" \
    "Health authority references"
}

# Function to update LICENSE files
update_licenses() {
  log "Updating LICENSE files"
  
  if [ "$DRY_RUN" = true ]; then
    log_warning "[DRY RUN] Would update LICENSE files"
    return
  fi
  
  local count=0
  while IFS= read -r file; do
    if grep -q "Swasth" "$file" 2>/dev/null; then
      sed -i 's/Swasth/HealthFlow Medical/g' "$file"
      sed -i 's/India/Egypt/g' "$file"
      ((count++))
      log "  Updated: $file"
    fi
  done < <(find "$REPO_PATH" -type f -name "LICENSE*" 2>/dev/null)
  
  log_success "Updated $count LICENSE files"
}

# Function to generate summary report
generate_report() {
  local report_file="${REPO_PATH}/phase1/docs/contextualization-report-$(date +%Y%m%d_%H%M%S).md"
  
  log "Generating summary report: $report_file"
  
  cat > "$report_file" << EOF
# HCX Egypt Contextualization Report

**Date**: $(date '+%Y-%m-%d %H:%M:%S')
**Mode**: $([ "$DRY_RUN" = true ] && echo "DRY RUN" || echo "LIVE")
**Repository Path**: $REPO_PATH

## Summary

This report summarizes the automated contextualization of the HCX codebase from Swasth/India to HealthFlow/Egypt.

## Changes Applied

### 1. Java Package Renaming
- **From**: \`org.swasth.*\`
- **To**: \`org.healthflow.*\`
- **Files Modified**: See detailed log

### 2. Maven Configuration Updates
- **Group IDs**: \`org.swasth\` → \`org.healthflow\`
- **Artifact IDs**: \`swasth-*\` → \`healthflow-*\`
- **Project Names**: Updated in all pom.xml files

### 3. API Endpoint Updates
- **Development**: \`dev-hcx.swasth.app\` → \`dev-hcx.healthflow.eg\`
- **Staging**: \`staging-hcx.swasth.app\` → \`staging-hcx.healthflow.eg\`
- **Production**: \`prod-hcx.swasth.app\` → \`hcx.healthflow.eg\`
- **Documentation**: \`docs.swasth.app\` → \`docs.healthflow.eg\`

### 4. Keycloak Configuration
- **Realm**: \`swasth-health-claim-exchange\` → \`hcx-egypt\`
- **Client ID**: \`swasth-hcx-gateway\` → \`healthflow-hcx-gateway\`

### 5. Frontend Branding
- **Application Title**: "Swasth HCX" → "HealthFlow HCX Egypt"
- **NPM Packages**: \`swasth-*\` → \`healthflow-*\`

### 6. Documentation
- All Markdown files updated
- Regulatory references updated (IRDAI → FRA, NHA/ABDM → MoHP)

### 7. LICENSE Files
- Copyright holder updated to HealthFlow Medical
- Country references updated to Egypt

## Verification Steps

1. **Build Test**: Run \`mvn clean install\` in each repository
2. **Unit Tests**: Run \`mvn test\` to ensure no regressions
3. **Integration Tests**: Run end-to-end tests
4. **Manual Review**: Check critical configuration files

## Rollback Instructions

If issues are encountered, restore from backup:

\`\`\`bash
BACKUP_DIR=\$(cat ${REPO_PATH}/.last_backup)
cd ${REPO_PATH}
for repo in hfcx-platform hcx-apps hcx-specs hcx-ig hcx-integrator hcx-protocol; do
  rm -rf \${repo}
  cp -r \${BACKUP_DIR}/\${repo} .
done
\`\`\`

## Next Steps

1. Review changes in each repository
2. Run automated tests
3. Commit changes to a feature branch
4. Create pull requests for review
5. Deploy to development environment for testing

## Detailed Log

See full log: $LOG_FILE

---

**Generated by**: HCX Egypt Contextualization Script
**Script Version**: 1.0.0
EOF

  log_success "Report generated: $report_file"
}

# Main execution
main() {
  log "========================================="
  log "HCX Egypt Contextualization Script"
  log "========================================="
  log "Mode: $([ "$DRY_RUN" = true ] && echo "DRY RUN" || echo "LIVE")"
  log "Repository: $REPO_PATH"
  log "Log file: $LOG_FILE"
  log "========================================="
  
  # Verify repository exists
  if [ ! -d "$REPO_PATH" ]; then
    log_error "Repository path does not exist: $REPO_PATH"
    exit 1
  fi
  
  # Create backup (skip in dry-run mode)
  if [ "$DRY_RUN" = false ]; then
    create_backup
  fi
  
  # Execute refactoring steps
  log "Starting contextualization process..."
  
  rename_java_packages
  update_maven_configs
  update_api_endpoints
  update_keycloak_config
  update_frontend_branding
  update_documentation
  update_licenses
  
  # Generate report
  generate_report
  
  log "========================================="
  log_success "Contextualization completed!"
  log "========================================="
  
  if [ "$DRY_RUN" = true ]; then
    log_warning "This was a DRY RUN. No changes were made."
    log "Run without --dry-run to apply changes."
  else
    log "Review the changes and run tests before committing."
    log "Backup location: $(cat ${REPO_PATH}/.last_backup)"
  fi
}

# Run main function
main
