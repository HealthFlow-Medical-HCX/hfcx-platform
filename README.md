# HealthFlow HCX Egypt Platform

**Health Claims Exchange Platform for Egypt**

[![Build Status](https://github.com/HealthFlow-Medical-HCX/hfcx-platform/workflows/CI/badge.svg)](https://github.com/HealthFlow-Medical-HCX/hfcx-platform/actions)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

---

## Overview

The HealthFlow HCX Egypt platform is a comprehensive health claims exchange system designed specifically for the Egyptian healthcare ecosystem. It facilitates seamless communication between healthcare providers, insurance companies (payors), third-party administrators (TPAs), and regulatory bodies.

This platform is a contextualized and localized version of the open-source HCX protocol, adapted to meet Egyptian regulatory requirements and healthcare standards.

## Key Features

- **Egyptian Localization**: Support for Egyptian National IDs, phone numbers, governorates, and Arabic language
- **FHIR R4 Compliance**: Fully compliant with HL7 FHIR R4 standards
- **Secure Communication**: End-to-end encryption with digital signatures
- **Real-time Processing**: Asynchronous claim processing with Kafka
- **Multi-tenancy**: Support for multiple healthcare providers and payors
- **Regulatory Compliance**: Aligned with Egyptian Financial Regulatory Authority (FRA) requirements

## Architecture

The platform consists of the following core components:

- **API Gateway**: Entry point for all API requests with authentication and rate limiting
- **HCX APIs**: Core business logic for claim processing, eligibility checks, and pre-authorization
- **Pipeline Jobs**: Asynchronous processing of claims, payments, and notifications
- **Registry**: Participant management and onboarding
- **Scheduler**: Automated tasks and periodic jobs

## Technology Stack

- **Backend**: Java 17, Spring Boot 3.x
- **Database**: PostgreSQL 15 with streaming replication
- **Cache**: Redis 7 with clustering
- **Message Queue**: Apache Kafka 3.x
- **Orchestration**: Kubernetes
- **Monitoring**: Prometheus, Grafana, Jaeger, ELK Stack
- **Secrets Management**: HashiCorp Vault

## Getting Started

### Prerequisites

- Java 17 or higher
- Maven 3.8+
- Docker and Docker Compose
- Kubernetes cluster (for production)

### Local Development Setup

```bash
# Clone the repository
git clone https://github.com/HealthFlow-Medical-HCX/hfcx-platform.git
cd hfcx-platform

# Build the project
mvn clean install

# Start dependencies (PostgreSQL, Redis, Kafka)
docker-compose up -d

# Run the API Gateway
cd api-gateway
mvn spring-boot:run

# Run the HCX API
cd ../hcx-apis
mvn spring-boot:run
```

### Configuration

All configuration files are located in `src/main/resources/`. Key configurations:

- `application.yml`: Main application configuration
- `application-vault.yml`: Vault integration for secrets management
- `rate-limiting-config.yml`: API rate limiting rules

### Environment Variables

```bash
# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=hcx_egypt
DB_USERNAME=hcx_admin
DB_PASSWORD=<from-vault>

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=<from-vault>

# Kafka
KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# Vault
VAULT_URI=https://vault.healthflow.eg:8200
VAULT_TOKEN=<service-token>
```

## Deployment

### Production Deployment

The platform is designed to run on Kubernetes. Deployment manifests are located in the `deployment/` directory.

```bash
# Apply Kubernetes configurations
kubectl apply -f deployment/monitoring/
kubectl apply -f deployment/performance/
kubectl apply -f deployment/vault/

# Deploy the application
kubectl apply -f phase1/infrastructure/kubernetes-dev.yml
```

### Monitoring

- **Prometheus**: http://prometheus.healthflow.eg
- **Grafana**: http://grafana.healthflow.eg
- **Jaeger**: http://jaeger.healthflow.eg:16686
- **Kibana**: http://kibana.healthflow.eg:5601

## Documentation

- [Phase 1 Completion Report](docs/phase1/Phase1_Completion_Report.md)
- [Phase 2 Completion Report](docs/phase2_completion_report.md)
- [Phase 3 Completion Report](docs/phase3_completion_report.md)
- [Disaster Recovery Plan](docs/operations/disaster-recovery-plan.md)
- [API Documentation](docs/api/)

## Egyptian Localization

### Supported Features

- **National ID Validation**: 14-digit Egyptian National ID with Luhn algorithm
- **Phone Number Validation**: Egyptian mobile (+20 1XX XXX XXXX) and landline formats
- **Governorates**: All 27 Egyptian governorates supported
- **Currency**: Egyptian Pound (EGP) with proper formatting
- **IBAN**: 29-character Egyptian IBAN validation
- **Language**: Arabic (RTL) and English support

### Validators

Egyptian-specific validators are located in:
```
hcx-core/hcx-common/src/main/java/org/healthflow/hcx/utils/validators/
```

## Contributing

We welcome contributions from the community. Please read our [Contributing Guidelines](CONTRIBUTING.md) before submitting pull requests.

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## Support

For technical support or questions:
- Email: support@healthflow.eg
- Documentation: https://docs.healthflow.eg
- Issue Tracker: https://github.com/HealthFlow-Medical-HCX/hfcx-platform/issues

## Acknowledgments

This platform is based on the open-source HCX protocol developed by the Swasth Digital Health Foundation. We acknowledge their contribution to the healthcare interoperability ecosystem.

---

**HealthFlow Medical** | Building the future of healthcare in Egypt
