# HCX Egypt - Deployment Guide

**Version**: 1.0  
**Last Updated**: December 3, 2025  
**Status**: Production Ready

---

## Table of Contents

1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Quick Start](#quick-start)
4. [Detailed Deployment](#detailed-deployment)
5. [Configuration](#configuration)
6. [Verification](#verification)
7. [Troubleshooting](#troubleshooting)
8. [Production Considerations](#production-considerations)

---

## Overview

This guide provides step-by-step instructions for deploying the HCX Egypt platform. The platform consists of:

- **Infrastructure**: PostgreSQL, Redis, Kafka, Elasticsearch, Keycloak
- **Backend Services**: HCX API, API Gateway, Provider Service
- **Frontend Applications**: Beneficiary Portal, OPD Portal, BSP Portal, Mock Payer App

---

## Prerequisites

### System Requirements

| Component | Minimum | Recommended |
|-----------|---------|-------------|
| CPU | 4 cores | 8 cores |
| RAM | 8 GB | 16 GB |
| Disk | 50 GB | 100 GB |
| OS | Ubuntu 20.04+ | Ubuntu 22.04 LTS |

### Software Requirements

- Docker 20.10+
- Docker Compose 2.0+
- Git 2.30+
- OpenSSH Client

### Network Requirements

- Ports 3001-3004 (Frontend applications)
- Ports 8080-8083 (Backend services)
- Ports 5432, 6379, 9092, 9200 (Infrastructure)

---

## Quick Start

For a rapid deployment on a fresh server:

```bash
# Clone the repository
git clone https://github.com/HealthFlow-Medical-HCX/hfcx-platform.git
cd hfcx-platform/deployment

# Start all services
docker-compose -f docker-compose-egypt.yml up -d

# Wait for services to be healthy (2-3 minutes)
docker-compose -f docker-compose-egypt.yml ps

# Verify deployment
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
```

Access the applications:
- Keycloak Admin: http://localhost:8080/admin (admin/admin)
- Beneficiary Portal: http://localhost:3001
- OPD Portal: http://localhost:3002
- BSP Portal: http://localhost:3003
- Mock Payer App: http://localhost:3004

---

## Detailed Deployment

### Step 1: Prepare the Environment

```bash
# Update system packages
sudo apt update && sudo apt upgrade -y

# Install Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
sudo usermod -aG docker $USER

# Install Docker Compose
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# Verify installation
docker --version
docker-compose --version

# Logout and login again for group changes to take effect
```

### Step 2: Clone the Repository

```bash
# Clone the platform repository
git clone https://github.com/HealthFlow-Medical-HCX/hfcx-platform.git
cd hfcx-platform

# Checkout the deployment branch
git checkout deployment-fixes-dec-2025
```

### Step 3: Build Docker Images

```bash
# Build HCX API
cd hcx-apis
mvn clean package -DskipTests
docker build -t hcx-api:egypt-fixed .

# Build API Gateway
cd ../api-gateway
mvn clean package -DskipTests
docker build -t hcx-api-gateway:egypt-fixed .

# Build Provider Service
cd ../provider-service
mvn clean package -DskipTests
docker build -t hcx-provider-service:egypt-fixed .

# Build Frontend Applications
cd ../beneficiary-portal
docker build -t beneficiary-portal:latest .

cd ../opd-portal
docker build -t opd-portal:latest .

cd ../bsp-portal
docker build -t bsp-portal:latest .

cd ../mock-payer-app
docker build -t mock-payer-app:latest .
```

### Step 4: Configure Environment Variables

Create a `.env` file in the `deployment/` directory:

```bash
# PostgreSQL Configuration
POSTGRES_DB=registry
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres

# Keycloak Configuration
KEYCLOAK_ADMIN=admin
KEYCLOAK_ADMIN_PASSWORD=admin

# Service Secrets (Change in production!)
PROVIDER_SERVICE_CLIENT_SECRET=provider-secret
```

### Step 5: Deploy Infrastructure Services

```bash
cd deployment

# Start infrastructure services first
docker-compose -f docker-compose-egypt.yml up -d \
  hcx-postgres \
  hcx-redis \
  hcx-kafka \
  hcx-elasticsearch \
  hcx-keycloak

# Wait for services to be healthy
sleep 60

# Verify infrastructure
docker-compose -f docker-compose-egypt.yml ps
```

### Step 6: Deploy Backend Services

```bash
# Start backend services
docker-compose -f docker-compose-egypt.yml up -d \
  hcx-api \
  hcx-api-gateway \
  hcx-provider-service

# Wait for services to start
sleep 90

# Check logs
docker logs hcx-api
docker logs hcx-api-gateway
```

### Step 7: Deploy Frontend Applications

```bash
# Start frontend applications
docker-compose -f docker-compose-egypt.yml up -d \
  beneficiary-portal \
  opd-portal \
  bsp-portal \
  mock-payer-app

# Verify all services are running
docker-compose -f docker-compose-egypt.yml ps
```

---

## Configuration

### Important Configuration Notes

#### 1. API Gateway Environment Variable

**Critical**: The API Gateway requires the `hcx-api.basePath` property to be set using **dot notation**, not environment variable format.

**Correct**:
```yaml
environment:
  hcx-api.basePath: http://hcx-api:8080
```

**Incorrect** (will not work):
```yaml
environment:
  HCX_API_BASEPATH: http://hcx-api:8080
```

**Reason**: Spring Boot's relaxed binding does not correctly map `HCX_API_BASEPATH` to `hcx-api.basePath` due to the hyphen in the property name.

#### 2. PostgreSQL Connection

The HCX API uses environment variables for PostgreSQL configuration:

```yaml
environment:
  POSTGRES_HOST: hcx-postgres
  POSTGRES_PORT: 5432
  POSTGRES_DB: registry
  POSTGRES_USER: postgres
  POSTGRES_PASSWORD: postgres
```

These are mapped in `application.yml`:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5432}/${POSTGRES_DB:registry}
    username: ${POSTGRES_USER:postgres}
    password: ${POSTGRES_PASSWORD:postgres}
```

#### 3. Keycloak Realm Import

The Keycloak container automatically imports the `hcx-egypt` realm on startup:

```yaml
volumes:
  - ./keycloak/hcx-egypt-realm.json:/opt/keycloak/data/import/hcx-egypt-realm.json:ro
command: start-dev --import-realm
```

#### 4. Service Account Roles

**Important**: After deployment, service accounts need roles assigned for RBAC to work.

See [keycloak/README.md](keycloak/README.md) for detailed instructions on assigning roles.

---

## Verification

### Health Checks

```bash
# Infrastructure
curl http://localhost:5432  # PostgreSQL (should connect)
curl http://localhost:6379  # Redis (should connect)
curl http://localhost:9092  # Kafka (should connect)
curl http://localhost:9200  # Elasticsearch
curl http://localhost:8080/health  # Keycloak

# Backend Services
curl http://localhost:8082/actuator/health  # HCX API
curl http://localhost:8081/actuator/health  # API Gateway
curl http://localhost:8083/actuator/health  # Provider Service

# Frontend Applications
curl -I http://localhost:3001  # Beneficiary Portal (200 OK)
curl -I http://localhost:3002  # OPD Portal (200 OK)
curl -I http://localhost:3003  # BSP Portal (200 OK)
curl -I http://localhost:3004  # Mock Payer App (200 OK)
```

### Functional Testing

#### 1. Test Keycloak Authentication

```bash
# Get access token
TOKEN=$(curl -s -X POST http://localhost:8080/realms/hcx-egypt/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=hcx-functional-tester&client_secret=functional-tester-secret" \
  | jq -r .access_token)

echo "Token: $TOKEN"

# Decode token to verify roles
echo $TOKEN | cut -d "." -f2 | base64 -d | jq .
```

#### 2. Test API Gateway Routing

```bash
# Test with authentication
curl -X POST http://localhost:8081/v0.7/coverageeligibility/check \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{}'

# Expected: ERR_INVALID_PAYLOAD (correct - JWE encryption required)
# Not expected: ERR_SERVICE_UNAVAILABLE or ERR_ACCESS_DENIED
```

#### 3. Test Database Connectivity

```bash
# Check if HCX API can connect to PostgreSQL
docker logs hcx-api 2>&1 | grep -i "started\|error"

# Should see: "Started HCXApplication in X.XXX seconds"
```

---

## Troubleshooting

### Common Issues

#### Issue 1: API Gateway returns ERR_SERVICE_UNAVAILABLE

**Symptom**: API Gateway cannot route requests to HCX API

**Cause**: Incorrect environment variable configuration

**Solution**:
```bash
# Verify the environment variable is set correctly
docker inspect hcx-api-gateway | grep "hcx-api.basePath"

# Should show: "hcx-api.basePath=http://hcx-api:8080"

# If not, recreate the container with correct configuration
docker-compose -f docker-compose-egypt.yml up -d --force-recreate hcx-api-gateway
```

#### Issue 2: ERR_ACCESS_DENIED on authenticated requests

**Symptom**: Valid JWT token but access denied

**Cause**: Service account missing required roles

**Solution**: Assign roles to service account (see [keycloak/README.md](keycloak/README.md))

```bash
# Quick fix: Assign provider role
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/realms/master/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=admin-cli&username=admin&password=admin" \
  | jq -r .access_token)

# Get client and user IDs, then assign role
# (See full script in keycloak/README.md)
```

#### Issue 3: HCX API fails to start with database connection error

**Symptom**: `Connection to localhost:5432 refused`

**Cause**: PostgreSQL environment variables not set

**Solution**:
```bash
# Verify environment variables
docker inspect hcx-api | grep POSTGRES

# Recreate with correct configuration
docker-compose -f docker-compose-egypt.yml up -d --force-recreate hcx-api
```

#### Issue 4: Keycloak realm not imported

**Symptom**: `hcx-egypt` realm doesn't exist

**Solution**:
```bash
# Verify volume mount
docker inspect hcx-keycloak | grep "hcx-egypt-realm.json"

# Manually import if needed
docker cp ./keycloak/hcx-egypt-realm.json hcx-keycloak:/tmp/
docker exec hcx-keycloak /opt/keycloak/bin/kc.sh import \
  --file /tmp/hcx-egypt-realm.json
```

### Viewing Logs

```bash
# View logs for a specific service
docker logs -f hcx-api
docker logs -f hcx-api-gateway
docker logs -f hcx-keycloak

# View logs for all services
docker-compose -f docker-compose-egypt.yml logs -f

# Search for errors
docker logs hcx-api 2>&1 | grep -i error
```

### Restarting Services

```bash
# Restart a single service
docker-compose -f docker-compose-egypt.yml restart hcx-api

# Restart all services
docker-compose -f docker-compose-egypt.yml restart

# Force recreate a service
docker-compose -f docker-compose-egypt.yml up -d --force-recreate hcx-api
```

---

## Production Considerations

### Security Hardening

1. **Change Default Passwords**
   ```bash
   # Update in .env file
   KEYCLOAK_ADMIN_PASSWORD=<strong-password>
   POSTGRES_PASSWORD=<strong-password>
   ```

2. **Use Secrets Management**
   - Store secrets in HashiCorp Vault, AWS Secrets Manager, or similar
   - Use Docker secrets for sensitive data
   - Never commit secrets to version control

3. **Enable HTTPS**
   - Configure SSL/TLS certificates
   - Use Let's Encrypt for free certificates
   - Set up reverse proxy (Nginx/Traefik) with SSL termination

4. **Configure Firewall**
   ```bash
   # Allow only necessary ports
   sudo ufw allow 80/tcp   # HTTP
   sudo ufw allow 443/tcp  # HTTPS
   sudo ufw allow 22/tcp   # SSH
   sudo ufw enable
   ```

### Performance Optimization

1. **Database Tuning**
   - Apply PostgreSQL configuration from `phase3/postgres-tuning.conf`
   - Enable connection pooling
   - Set appropriate shared_buffers and work_mem

2. **Redis Configuration**
   - Enable persistence (AOF or RDB)
   - Configure maxmemory policy
   - Set up Redis Sentinel for high availability

3. **JVM Tuning**
   ```yaml
   environment:
     JAVA_OPTS: "-Xms2g -Xmx4g -XX:+UseG1GC"
   ```

4. **Resource Limits**
   ```yaml
   deploy:
     resources:
       limits:
         cpus: '2'
         memory: 4G
       reservations:
         cpus: '1'
         memory: 2G
   ```

### Monitoring and Logging

1. **Deploy Monitoring Stack**
   ```bash
   # Deploy Prometheus and Grafana
   cd ../phase3
   docker-compose -f docker-compose-monitoring.yml up -d
   ```

2. **Configure Log Aggregation**
   - Set up ELK stack (Elasticsearch, Logstash, Kibana)
   - Configure log rotation
   - Set up log retention policies

3. **Set Up Alerting**
   - Configure Prometheus alerts
   - Set up PagerDuty/Slack integration
   - Monitor critical metrics (CPU, memory, disk, response time)

### Backup and Recovery

1. **Database Backups**
   ```bash
   # Automated PostgreSQL backup
   docker exec hcx-postgres pg_dump -U postgres registry > backup.sql
   
   # Schedule with cron
   0 2 * * * docker exec hcx-postgres pg_dump -U postgres registry > /backups/hcx-$(date +\%Y\%m\%d).sql
   ```

2. **Keycloak Backup**
   ```bash
   # Export realm configuration
   docker exec hcx-keycloak /opt/keycloak/bin/kc.sh export \
     --dir /tmp --realm hcx-egypt --users realm_file
   docker cp hcx-keycloak:/tmp/hcx-egypt-realm.json ./backups/
   ```

3. **Volume Backups**
   ```bash
   # Backup Docker volumes
   docker run --rm -v postgres-data:/data -v $(pwd):/backup \
     alpine tar czf /backup/postgres-data.tar.gz /data
   ```

### High Availability

1. **Database Replication**
   - Set up PostgreSQL streaming replication
   - Configure automatic failover with Patroni

2. **Load Balancing**
   - Deploy multiple instances of backend services
   - Use Nginx or HAProxy for load balancing
   - Configure health checks

3. **Service Discovery**
   - Use Consul or etcd for service discovery
   - Implement circuit breakers
   - Configure retry policies

### Scaling

1. **Horizontal Scaling**
   ```yaml
   deploy:
     replicas: 3
   ```

2. **Database Connection Pooling**
   ```yaml
   spring:
     datasource:
       hikari:
         maximum-pool-size: 20
         minimum-idle: 5
   ```

3. **Caching Strategy**
   - Implement Redis caching for frequently accessed data
   - Configure cache TTL appropriately
   - Use cache-aside pattern

---

## Support and Maintenance

### Regular Maintenance Tasks

- [ ] Weekly: Review logs for errors
- [ ] Weekly: Check disk space usage
- [ ] Monthly: Update Docker images
- [ ] Monthly: Review and rotate secrets
- [ ] Quarterly: Security audit
- [ ] Quarterly: Performance review

### Updating the Platform

```bash
# Pull latest changes
git pull origin deployment-fixes-dec-2025

# Rebuild images
docker-compose -f docker-compose-egypt.yml build

# Rolling update
docker-compose -f docker-compose-egypt.yml up -d --no-deps --build hcx-api
docker-compose -f docker-compose-egypt.yml up -d --no-deps --build hcx-api-gateway
```

### Getting Help

- **Documentation**: https://docs.hcxprotocol.io/
- **GitHub Issues**: https://github.com/HealthFlow-Medical-HCX/hfcx-platform/issues
- **Community**: HCX Protocol Community Forum

---

## Appendix

### Port Reference

| Service | Port | Protocol | Purpose |
|---------|------|----------|---------|
| PostgreSQL | 5432 | TCP | Database |
| Redis | 6379 | TCP | Cache |
| Kafka | 9092 | TCP | Message Broker |
| Elasticsearch | 9200 | HTTP | Search Engine |
| Keycloak | 8080 | HTTP | Identity Provider |
| HCX API | 8082 | HTTP | Core API |
| API Gateway | 8081 | HTTP | API Gateway |
| Provider Service | 8083 | HTTP | Provider Service |
| Beneficiary Portal | 3001 | HTTP | Web UI |
| OPD Portal | 3002 | HTTP | Web UI |
| BSP Portal | 3003 | HTTP | Web UI |
| Mock Payer App | 3004 | HTTP | Web UI |

### Environment Variables Reference

See [ENVIRONMENT_VARIABLES.md](ENVIRONMENT_VARIABLES.md) for complete reference.

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                     Frontend Layer                          │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐      │
│  │Beneficiary│ │   OPD    │ │   BSP    │ │  Payer   │      │
│  │  Portal  │ │  Portal  │ │  Portal  │ │   App    │      │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘      │
└───────┼───────────┼─────────────┼─────────────┼────────────┘
        │           │             │             │
        └───────────┴─────────────┴─────────────┘
                         │
┌────────────────────────┼────────────────────────────────────┐
│                        ▼                                     │
│              ┌──────────────────┐                           │
│              │   API Gateway    │                           │
│              │   (Port 8081)    │                           │
│              └────────┬─────────┘                           │
│                       │                                      │
│              ┌────────┴─────────┐                           │
│              ▼                  ▼                            │
│      ┌──────────────┐   ┌──────────────┐                   │
│      │   HCX API    │   │   Provider   │                   │
│      │ (Port 8082)  │   │   Service    │                   │
│      └──────┬───────┘   └──────┬───────┘                   │
└─────────────┼──────────────────┼────────────────────────────┘
              │                  │
┌─────────────┼──────────────────┼────────────────────────────┐
│             ▼                  ▼                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │  PostgreSQL  │  │    Redis     │  │   Keycloak   │     │
│  │ (Port 5432)  │  │ (Port 6379)  │  │ (Port 8080)  │     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
│                                                              │
│  ┌──────────────┐  ┌──────────────┐                        │
│  │    Kafka     │  │Elasticsearch │                        │
│  │ (Port 9092)  │  │ (Port 9200)  │                        │
│  └──────────────┘  └──────────────┘                        │
│                                                              │
│                  Infrastructure Layer                        │
└──────────────────────────────────────────────────────────────┘
```

---

**Document Version**: 1.0  
**Last Updated**: December 3, 2025  
**Maintained by**: HealthFlow Medical HCX Team
