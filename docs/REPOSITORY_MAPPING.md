# HealthFlow HCX Egypt - Repository Mapping and Architecture

**Document Version**: 1.0  
**Date**: December 1, 2025  
**Organization**: HealthFlow-Medical-HCX

---

## Overview

This document provides a comprehensive mapping of all repositories in the HealthFlow HCX Egypt organization, their roles in the overall architecture, and how they interact to form a complete Health Claims Exchange platform.

---

## Repository Inventory

The HealthFlow HCX Egypt platform consists of **12 repositories**, each serving a specific role in the ecosystem:

| Repository | Language | License | Role Category |
|------------|----------|---------|---------------|
| hfcx-platform | Java | MIT | Core Platform |
| hcx-apps | TypeScript | MIT | Management Applications |
| hcx-mock-payer-app | TypeScript | MIT | Testing & Simulation |
| provider-service | Java | - | Provider Integration |
| hcx-devops | Mustache | MIT | Infrastructure & Deployment |
| sunbird-rc-core | Java | MIT | Registry & Credentials |
| integration-sdks | C# | MIT | Integration Tools |
| hcx-app-common-component | TypeScript | - | Shared UI Components |
| hcx-mock-service | Java | MIT | Testing & Simulation |
| fhir-examples | - | - | Documentation & Examples |
| jwe-helper | Java | MIT | Security Utilities |
| hcx-demo-app | JavaScript | MIT | Demo & Reference |

---

## Detailed Repository Roles

### 1. Core Platform Layer

#### **hfcx-platform** (Primary Repository)
**Repository**: https://github.com/HealthFlow-Medical-HCX/hfcx-platform  
**Language**: Java  
**Description**: Health Claims Data Exchange - reference implementation

**Role**: This is the **core backend platform** that implements the HCX protocol specifications. It serves as the central gateway for all health claims data exchange.

**Key Components**:
- **API Gateway**: Entry point for all API requests with authentication and routing
- **HCX APIs**: Core business logic for claims processing
  - Coverage eligibility verification
  - Pre-authorization workflows
  - Claims submission and adjudication
  - Communication and information exchange
  - Payment notifications
- **Pipeline Jobs**: Asynchronous processing using Kafka
- **Registry Integration**: Participant management and onboarding
- **Scheduler**: Automated tasks and periodic jobs

**Technical Stack**:
- Java 17
- Spring Boot 3.x
- PostgreSQL 15
- Redis 7
- Apache Kafka 3.x
- FHIR R4

**Participants Served**: All (Providers, Payers, TPAs, Regulators)

**Key APIs**:
- `/coverageeligibility/check` - Verify patient insurance coverage
- `/preauth/submit` - Submit pre-authorization requests
- `/claim/submit` - Submit claims for adjudication
- `/communication/request` - Request additional information
- `/paymentnotice/request` - Process payment notifications

---

### 2. Management & Operations Layer

#### **hcx-apps** (Management Applications)
**Repository**: https://github.com/HealthFlow-Medical-HCX/hcx-apps  
**Language**: TypeScript  
**Description**: Reference/management/operations related user facing applications

**Role**: This repository contains **all participant-facing portals** for managing operations on the HCX platform.

**Key Applications**:

1. **System Administrator Portal**
   - User Roles: Super Admin, System Admin
   - Functions:
     - Platform configuration and management
     - Participant onboarding and approval
     - System monitoring and health checks
     - Audit log review
     - Policy and rule management

2. **Provider Portal**
   - User Roles: Provider Admin, Provider Operator
   - Functions:
     - Patient eligibility verification
     - Pre-authorization request submission
     - Claims submission and tracking
     - Payment status monitoring
     - Documentation exchange

3. **Payer Portal**
   - User Roles: Payer Admin, Payer Operator, Payer Adjudicator
   - Functions:
     - Eligibility inquiry responses
     - Pre-authorization review and approval
     - Claims adjudication
     - Payment processing
     - Provider network management

4. **TPA Portal**
   - User Roles: TPA Admin, TPA Operator
   - Functions:
     - Claims processing on behalf of payers
     - Provider network coordination
     - Administrative services
     - Reporting and analytics

5. **Beneficiary Service Platform (BSP) Portal**
   - User Roles: Beneficiary, Patient
   - Functions:
     - Claim tracking and status
     - Coverage information
     - Health record access
     - Communication with providers/payers

**Technical Stack**:
- React
- TypeScript
- Material-UI / Ant Design
- Redux for state management
- Axios for API calls

**Authentication**: Keycloak-based SSO with role-based access control

---

#### **hcx-app-common-component**
**Repository**: https://github.com/HealthFlow-Medical-HCX/hcx-app-common-component  
**Language**: TypeScript

**Role**: **Shared UI component library** used across all HCX applications.

**Key Components**:
- Reusable React components (buttons, forms, tables, modals)
- Common layouts and templates
- Shared utilities and helpers
- Consistent styling and theming
- HealthFlow branding assets

**Usage**: Imported by hcx-apps and other frontend applications to ensure UI consistency.

---

### 3. Integration & SDK Layer

#### **integration-sdks**
**Repository**: https://github.com/HealthFlow-Medical-HCX/integration-sdks  
**Language**: C#, Java, Python, JavaScript

**Role**: **Software Development Kits** for integrating with the HCX platform in multiple programming languages.

**Supported Languages**:
- **C# SDK**: For .NET applications
- **Java SDK**: For Java/Spring applications
- **Python SDK**: For Python applications
- **JavaScript/Node.js SDK**: For web and Node.js applications

**Key Features**:
- API client libraries
- Authentication and encryption helpers
- FHIR resource builders and validators
- Request/response handling
- Error handling and retry logic
- Callback API implementation

**Example Usage**:
```csharp
// C# SDK Example
var client = new HealthFlowClient(apiKey, apiSecret);
var eligibilityRequest = new CoverageEligibilityRequest()
    .SetPatient(patientData)
    .SetProvider(providerData)
    .SetInsurer(insurerData);
var response = await client.CheckEligibility(eligibilityRequest);
```

---

#### **jwe-helper**
**Repository**: https://github.com/HealthFlow-Medical-HCX/jwe-helper  
**Language**: Java

**Role**: **Security utility library** for JSON Web Encryption (JWE) operations.

**Key Functions**:
- Asymmetric encryption/decryption
- Key pair generation and management
- JWE token creation and validation
- Integration with HCX security framework

**Usage**: Used by hfcx-platform and integration SDKs for secure payload encryption.

---

### 4. Provider Integration Layer

#### **provider-service**
**Repository**: https://github.com/HealthFlow-Medical-HCX/provider-service  
**Language**: Java

**Role**: **Microservice for provider-specific operations** and integrations.

**Key Functions**:
- Provider registration and profile management
- Service catalog management
- Provider network coordination
- Integration with Hospital Information Systems (HIS)
- Integration with Electronic Medical Records (EMR)
- FHIR resource transformation

**Integration Points**:
- Connects to hfcx-platform via REST APIs
- Integrates with provider HIS/EMR systems
- Publishes events to Kafka for async processing

---

### 5. Registry & Credentials Layer

#### **sunbird-rc-core**
**Repository**: https://github.com/HealthFlow-Medical-HCX/sunbird-rc-core  
**Language**: Java

**Role**: **Electronic Registries and Verifiable Credentials** infrastructure.

**Key Functions**:
- Participant registry management
  - Provider registry
  - Payer registry
  - TPA registry
  - BSP registry
- Credential issuance and verification
- Public key infrastructure (PKI)
- Digital signatures
- Decentralized identifiers (DIDs)

**Registry Data**:
- Participant codes and identifiers
- Public keys for encryption
- Participant metadata (name, address, contact)
- Accreditation and licensing information
- Network relationships

**Integration**: Used by hfcx-platform for participant lookup and verification.

---

### 6. Testing & Simulation Layer

#### **hcx-mock-payer-app**
**Repository**: https://github.com/HealthFlow-Medical-HCX/hcx-mock-payer-app  
**Language**: TypeScript

**Role**: **Mock payer application** for testing provider integrations.

**Key Functions**:
- Simulates payer behavior
- Responds to eligibility checks
- Processes pre-authorization requests
- Adjudicates claims
- Sends payment notifications
- Configurable response scenarios (approval, denial, partial approval)

**Use Cases**:
- Provider integration testing
- SDK validation
- Training and demonstrations
- Development environment testing

---

#### **hcx-mock-service**
**Repository**: https://github.com/HealthFlow-Medical-HCX/hcx-mock-service  
**Language**: Java

**Role**: **Mock backend service** for simulating HCX platform behavior.

**Key Functions**:
- Simulates HCX gateway APIs
- Generates test data
- Provides sandbox environment
- Supports all HCX workflows

**Use Cases**:
- Integration testing without production platform
- Offline development
- Load testing and performance validation

---

#### **hcx-demo-app**
**Repository**: https://github.com/HealthFlow-Medical-HCX/hcx-demo-app  
**Language**: JavaScript (React + Node.js)

**Role**: **Demo application** for integrators and stakeholders.

**Key Functions**:
- Demonstrates HCX integration patterns
- Shows end-to-end workflows
- Provides reference implementation
- Training and onboarding tool

**Workflows Demonstrated**:
- Patient registration
- Eligibility verification
- Pre-authorization flow
- Claim submission
- Payment tracking

---

### 7. Documentation & Examples Layer

#### **fhir-examples**
**Repository**: https://github.com/HealthFlow-Medical-HCX/fhir-examples

**Role**: **FHIR resource examples** and templates for Egyptian healthcare context.

**Contents**:
- Sample FHIR resources (Patient, Claim, Coverage, etc.)
- Egyptian-specific profiles
- Value sets and code systems
- Validation examples
- Integration templates

**Key Examples**:
- Egyptian National ID in Patient resource
- Egyptian governorates in Address
- EGP currency in monetary amounts
- Egyptian insurance company identifiers
- Local diagnosis and procedure codes

---

### 8. Infrastructure & Deployment Layer

#### **hcx-devops**
**Repository**: https://github.com/HealthFlow-Medical-HCX/hcx-devops  
**Language**: Mustache templates, Shell scripts

**Role**: **Infrastructure provisioning and deployment automation**.

**Key Components**:
- Kubernetes manifests
- Helm charts
- Terraform scripts
- Ansible playbooks
- CI/CD pipeline configurations
- Environment configuration templates

**Supported Environments**:
- Development
- Staging
- Production

**Infrastructure Components**:
- Kubernetes cluster setup
- PostgreSQL deployment
- Redis cluster
- Kafka cluster
- Monitoring stack (Prometheus, Grafana)
- Logging stack (ELK)
- Vault for secrets management

**Deployment Scripts**:
- One-click deployment
- Rolling updates
- Blue-green deployment
- Canary releases
- Rollback procedures

---

## Architecture Overview

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Participant Portals                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐             │
│  │   Provider   │  │    Payer     │  │     TPA      │             │
│  │    Portal    │  │    Portal    │  │    Portal    │             │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘             │
│         │                  │                  │                      │
│         └──────────────────┴──────────────────┘                     │
│                            │                                         │
│                     [hcx-apps]                                       │
└────────────────────────────┼────────────────────────────────────────┘
                             │
                             │ HTTPS/REST APIs
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│                       API Gateway Layer                             │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │  Authentication │ Rate Limiting │ Routing │ Load Balancing     │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                      [hfcx-platform]                                │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      Core Business Logic                            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐             │
│  │  Eligibility │  │Pre-Auth      │  │    Claims    │             │
│  │  Verification│  │Processing    │  │  Processing  │             │
│  └──────────────┘  └──────────────┘  └──────────────┘             │
│                                                                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐             │
│  │Communication │  │   Payment    │  │   Workflow   │             │
│  │   Exchange   │  │Notifications │  │    Engine    │             │
│  └──────────────┘  └──────────────┘  └──────────────┘             │
│                      [hfcx-platform]                                │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                ┌────────────┼────────────┐
                │            │            │
                ▼            ▼            ▼
        ┌──────────┐  ┌──────────┐  ┌──────────┐
        │PostgreSQL│  │  Redis   │  │  Kafka   │
        │ Database │  │  Cache   │  │ Message  │
        └──────────┘  └──────────┘  └──────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    Supporting Services                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐             │
│  │  Registry    │  │  Provider    │  │   Security   │             │
│  │  (Sunbird)   │  │   Service    │  │  (JWE/Vault) │             │
│  └──────────────┘  └──────────────┘  └──────────────┘             │
└─────────────────────────────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    External Integrations                            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐             │
│  │   Provider   │  │    Payer     │  │     TPA      │             │
│  │   Systems    │  │   Systems    │  │   Systems    │             │
│  │  (HIS/EMR)   │  │   (Claims)   │  │  (Admin)     │             │
│  └──────────────┘  └──────────────┘  └──────────────┘             │
└─────────────────────────────────────────────────────────────────────┘
```

### Data Flow Architecture

```
Provider System                HCX Platform              Payer System
     │                              │                         │
     │  1. Eligibility Check        │                         │
     ├─────────────────────────────>│                         │
     │     (FHIR CoverageEligibility│                         │
     │      Request)                │                         │
     │                              │  2. Route to Payer      │
     │                              ├────────────────────────>│
     │                              │                         │
     │                              │  3. Payer Response      │
     │                              │<────────────────────────┤
     │  4. Eligibility Response     │                         │
     │<─────────────────────────────┤                         │
     │                              │                         │
     │  5. Pre-Auth Request         │                         │
     ├─────────────────────────────>│                         │
     │                              │  6. Route to Payer      │
     │                              ├────────────────────────>│
     │                              │                         │
     │                              │  7. Approval/Denial     │
     │                              │<────────────────────────┤
     │  8. Pre-Auth Response        │                         │
     │<─────────────────────────────┤                         │
     │                              │                         │
     │  9. Claim Submission         │                         │
     ├─────────────────────────────>│                         │
     │                              │  10. Route to Payer     │
     │                              ├────────────────────────>│
     │                              │                         │
     │                              │  11. Adjudication       │
     │                              │<────────────────────────┤
     │  12. Claim Response          │                         │
     │<─────────────────────────────┤                         │
     │                              │                         │
     │                              │  13. Payment Notice     │
     │                              │<────────────────────────┤
     │  14. Payment Notification    │                         │
     │<─────────────────────────────┤                         │
```

---

## Repository Dependencies

### Dependency Graph

```
hcx-apps
  ├── hcx-app-common-component (UI components)
  ├── hfcx-platform (API calls)
  └── integration-sdks (optional, for advanced features)

hfcx-platform
  ├── sunbird-rc-core (registry lookup)
  ├── jwe-helper (encryption)
  ├── provider-service (provider operations)
  └── hcx-devops (deployment)

integration-sdks
  ├── jwe-helper (encryption)
  └── hfcx-platform (API integration)

hcx-mock-payer-app
  ├── integration-sdks (HCX client)
  └── hfcx-platform (API calls)

hcx-demo-app
  └── integration-sdks (HCX client)

provider-service
  ├── hfcx-platform (API calls)
  └── jwe-helper (encryption)
```

---

## Deployment Architecture

### Production Deployment

```
┌─────────────────────────────────────────────────────────────────────┐
│                      Load Balancer (AWS ALB)                        │
│                    https://api.healthflow.eg                        │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    Kubernetes Cluster (EKS)                         │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │                      Namespace: hcx-egypt-prod                  │ │
│  │                                                                  │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │ │
│  │  │ API Gateway  │  │   HCX API    │  │  Pipeline    │         │ │
│  │  │  (3 pods)    │  │  (9 pods)    │  │   Jobs       │         │ │
│  │  └──────────────┘  └──────────────┘  └──────────────┘         │ │
│  │                                                                  │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │ │
│  │  │  Provider    │  │   Registry   │  │   Frontend   │         │ │
│  │  │  Service     │  │  (Sunbird)   │  │   (hcx-apps) │         │ │
│  │  └──────────────┘  └──────────────┘  └──────────────┘         │ │
│  └────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
                             │
                ┌────────────┼────────────┐
                │            │            │
                ▼            ▼            ▼
        ┌──────────┐  ┌──────────┐  ┌──────────┐
        │PostgreSQL│  │  Redis   │  │  Kafka   │
        │  (RDS)   │  │ElastiCache│ │  (MSK)   │
        │ Multi-AZ │  │ Cluster  │  │  Cluster │
        └──────────┘  └──────────┘  └──────────┘
```

### Monitoring Stack

```
┌─────────────────────────────────────────────────────────────────────┐
│                      Namespace: monitoring                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐             │
│  │  Prometheus  │  │   Grafana    │  │    Jaeger    │             │
│  │  (Metrics)   │  │ (Dashboards) │  │  (Tracing)   │             │
│  └──────────────┘  └──────────────┘  └──────────────┘             │
│                                                                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐             │
│  │Elasticsearch │  │   Logstash   │  │    Kibana    │             │
│  │   (Logs)     │  │  (Ingestion) │  │   (Search)   │             │
│  └──────────────┘  └──────────────┘  └──────────────┘             │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Integration Patterns

### 1. Provider Integration Pattern

```
Provider HIS/EMR
      │
      │ Internal API
      ▼
Provider Integration Layer (provider-service)
      │
      │ Transform to FHIR
      ▼
HCX SDK (integration-sdks)
      │
      │ Encrypt & Sign
      ▼
HCX Gateway (hfcx-platform)
      │
      │ Route to Payer
      ▼
Payer System
```

### 2. Payer Integration Pattern

```
Payer Claims System
      │
      │ Internal API
      ▼
Payer Integration Layer
      │
      │ Transform to FHIR
      ▼
HCX SDK (integration-sdks)
      │
      │ Encrypt & Sign
      ▼
HCX Gateway (hfcx-platform)
      │
      │ Route to Provider
      ▼
Provider System
```

### 3. Portal Integration Pattern

```
User Browser
      │
      │ HTTPS
      ▼
Portal Application (hcx-apps)
      │
      │ REST API
      ▼
API Gateway (hfcx-platform)
      │
      │ Authenticate & Authorize
      ▼
Business Logic (hfcx-platform)
      │
      │ Database Operations
      ▼
PostgreSQL
```

---

## Security Architecture

### Authentication Flow

```
1. User Login
   ├─> Keycloak (SSO)
   ├─> Validate Credentials
   ├─> Issue JWT Token
   └─> Return to Portal

2. API Request
   ├─> Include JWT in Authorization Header
   ├─> API Gateway validates JWT
   ├─> Extract user roles and permissions
   └─> Route to appropriate service

3. Participant Authentication
   ├─> API Key + Secret
   ├─> Validate against Registry
   ├─> Generate Bearer Token
   └─> Use for subsequent requests
```

### Encryption Flow

```
1. Sender
   ├─> Lookup recipient public key from Registry
   ├─> Create FHIR payload
   ├─> Encrypt with recipient public key (JWE)
   ├─> Sign with sender private key
   └─> Send to HCX Gateway

2. HCX Gateway
   ├─> Validate signature
   ├─> Verify sender identity
   ├─> Route encrypted payload
   └─> Do NOT decrypt (end-to-end encryption)

3. Recipient
   ├─> Receive encrypted payload
   ├─> Decrypt with private key
   ├─> Validate signature
   └─> Process payload
```

---

## Development Workflow

### Local Development Setup

1. **Clone Repositories**
   ```bash
   git clone https://github.com/HealthFlow-Medical-HCX/hfcx-platform.git
   git clone https://github.com/HealthFlow-Medical-HCX/hcx-apps.git
   git clone https://github.com/HealthFlow-Medical-HCX/provider-service.git
   git clone https://github.com/HealthFlow-Medical-HCX/sunbird-rc-core.git
   ```

2. **Start Dependencies**
   ```bash
   cd hfcx-platform
   docker-compose up -d  # PostgreSQL, Redis, Kafka
   ```

3. **Start Backend Services**
   ```bash
   # Terminal 1: HCX Platform
   cd hfcx-platform
   mvn spring-boot:run

   # Terminal 2: Provider Service
   cd provider-service
   mvn spring-boot:run

   # Terminal 3: Registry
   cd sunbird-rc-core
   mvn spring-boot:run
   ```

4. **Start Frontend**
   ```bash
   cd hcx-apps
   npm install
   npm start
   ```

5. **Access Applications**
   - API Gateway: http://localhost:8080
   - Provider Portal: http://localhost:3000
   - Payer Portal: http://localhost:3001
   - Admin Portal: http://localhost:3002

---

## Testing Strategy

### Unit Testing
- **hfcx-platform**: JUnit, Mockito
- **hcx-apps**: Jest, React Testing Library
- **integration-sdks**: Language-specific testing frameworks

### Integration Testing
- **hcx-mock-service**: Simulate HCX gateway
- **hcx-mock-payer-app**: Simulate payer responses
- **Reference Applications**: End-to-end workflow testing

### Performance Testing
- Load testing with Apache JMeter
- Stress testing with Gatling
- Monitoring with Prometheus

---

## Conclusion

The HealthFlow HCX Egypt platform is a comprehensive, modular system consisting of 12 repositories that work together to provide a complete health claims exchange solution. Each repository has a specific role, from core platform services to participant portals, integration tools, and infrastructure automation.

The architecture follows modern best practices with:
- **Microservices architecture** for scalability
- **API-first design** for interoperability
- **FHIR R4 compliance** for standardization
- **End-to-end encryption** for security
- **Kubernetes deployment** for reliability
- **Comprehensive monitoring** for observability

This modular design allows for independent development, testing, and deployment of each component while maintaining a cohesive overall system.

---

**For Questions or Support**:
- Technical Documentation: https://docs.healthflow.eg
- GitHub Organization: https://github.com/HealthFlow-Medical-HCX
- Support Email: support@healthflow.eg
