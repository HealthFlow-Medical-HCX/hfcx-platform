# HealthFlow References Analysis - HCX Egypt Contextualization

## Executive Summary

This document provides a comprehensive analysis of all HealthFlow and India-specific references found across the HCX repositories that require contextualization for Egypt.

## Search Results Summary

**Total Matches Found**: 177 occurrences
**Files Affected**: 49 files
**Repositories Analyzed**: 5 (hfcx-platform, hcx-apps, provider-service, hcx-devops, integration-sdks)

## Categories of References

### 1. **Copyright and Licensing**
- **Location**: LICENSE files across all repositories
- **Current**: Copyright (c) 2024 HealthFlow Digital Health Foundation
- **Required Change**: Copyright (c) 2025 HealthFlow Group

### 2. **Package Names and Group IDs**
- **Location**: pom.xml files (Java projects)
- **Current**: `org.swasth.*`
- **Required Change**: `org.healthflow.*` or `eg.healthflow.*`
- **Files Affected**:
  - `/hfcx-platform/pom.xml`
  - `/hfcx-platform/api-gateway/pom.xml`
  - All module pom.xml files

### 3. **Java Package Names**
- **Location**: All Java source files
- **Current**: `package org.swasth.*`
- **Required Change**: `package org.healthflow.*` or `eg.healthflow.*`
- **Estimated Files**: 100+ Java files

### 4. **API Endpoints and URLs**
- **Location**: Configuration files, API specifications
- **Current References**:
  - `dev-hcx.swasth.app`
  - `docs.swasth.app`
  - `hcx@swasthapp.org`
  - `swasthalliance.org`
- **Required Changes**:
  - `dev-hcx.healthflow.tech` or `hcx.healthflow.gov.eg`
  - `docs.healthflow.tech`
  - `hcx@healthflow.tech`
  - `healthflow.tech`

### 5. **Keycloak/Authentication Realms**
- **Location**: Configuration files, API gateway
- **Current**: `swasth-health-claim-exchange` realm
- **Required Change**: `healthflow-egypt-hcx` or `egypt-health-claim-exchange`
- **Files**:
  - `api-gateway/src/main/resources/application.yaml`
  - `demo-app/server/controller/eligibility.js`

### 6. **Branding and UI References**
- **Location**: Frontend applications
- **Current**: "HealthFlow HCX POC Application"
- **Required Change**: "HealthFlow Egypt HCX Application"
- **Files**:
  - `demo-app/client/public/index.html`
  - `demo-app/client/src/components/navbar/nav.js`
  - `demo-app/client/build/index.html`

### 7. **SonarCloud Configuration**
- **Location**: pom.xml
- **Current**: `swasth-digital-health-foundation`
- **Required Change**: `healthflow-medical-hcx`

## Priority Levels

### **Critical Priority** (Must be changed for basic functionality)
1. API endpoints and URLs
2. Keycloak realm names
3. Package names in Java code
4. Database connection strings (if any)

### **High Priority** (User-facing and branding)
1. UI text and titles
2. Email addresses
3. Domain names in documentation
4. Copyright notices

### **Medium Priority** (Internal references)
1. Java package names
2. Maven group IDs
3. SonarCloud configuration
4. Internal documentation

### **Low Priority** (Non-functional)
1. Comments in code
2. README files
3. Developer documentation

## Recommended Approach

### Phase 1: Core Infrastructure (Week 1-2)
1. Update all configuration files with new domains and endpoints
2. Update Keycloak realm configurations
3. Update API specifications

### Phase 2: Code Refactoring (Week 3-4)
1. Refactor Java package names from `org.swasth.*` to `org.healthflow.*`
2. Update all import statements
3. Update Maven group IDs and artifact IDs

### Phase 3: Frontend and Branding (Week 5)
1. Update all UI text and branding
2. Update logos and images
3. Update email addresses and contact information

### Phase 4: Documentation (Week 6)
1. Update all README files
2. Update API documentation
3. Update deployment guides

## Automated Search and Replace Strategy

### Safe Replacements (Can be automated)
```bash
# Copyright notices
find . -type f -name "*.java" -o -name "*.xml" -o -name "*.md" | xargs sed -i 's/HealthFlow Digital Health Foundation/HealthFlow Group/g'

# Package names (requires careful testing)
find . -type f -name "*.java" | xargs sed -i 's/package org\.swasth\./package org.healthflow./g'
find . -type f -name "*.java" | xargs sed -i 's/import org\.swasth\./import org.healthflow./g'

# Maven group IDs
find . -type f -name "pom.xml" | xargs sed -i 's/<groupId>org\.swasth<\/groupId>/<groupId>org.healthflow<\/groupId>/g'

# Domain names
find . -type f | xargs sed -i 's/swasth\.app/healthflow.tech/g'
find . -type f | xargs sed -i 's/swasthalliance\.org/healthflow.tech/g'
```

### Manual Review Required
1. Keycloak realm configurations
2. Database schema names
3. API endpoint URLs in configuration
4. Hardcoded URLs in JavaScript/TypeScript
5. Docker image names

## Risk Assessment

### High Risk Areas
1. **Database migrations**: Schema names may be hardcoded
2. **API contracts**: External systems may depend on specific URLs
3. **Authentication flows**: Keycloak realm changes affect all authentication

### Medium Risk Areas
1. **Package refactoring**: May break imports if not done comprehensively
2. **Frontend builds**: Compiled JavaScript may contain hardcoded references

### Low Risk Areas
1. **Documentation**: No functional impact
2. **Comments**: No functional impact

## Testing Strategy

### Unit Tests
- Verify all package imports resolve correctly
- Verify configuration loading

### Integration Tests
- Test API endpoints with new URLs
- Test authentication with new Keycloak realm
- Test database connections

### End-to-End Tests
- Full user journey testing
- Cross-browser testing for frontend changes

## Next Steps

1. **Create backup** of all repositories before making changes
2. **Set up development environment** with Egypt-specific configurations
3. **Create feature branch** for contextualization work
4. **Implement changes** in priority order
5. **Test thoroughly** at each phase
6. **Deploy to staging** environment for validation
7. **Document all changes** for future reference

## Appendix: Complete File List

See attached file for complete list of all 177 occurrences across 49 files.
