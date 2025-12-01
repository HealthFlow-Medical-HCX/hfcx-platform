# HCX Egypt Platform - Deployment Guide

**Version**: 1.0  
**Last Updated**: December 1, 2025

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Infrastructure Setup](#infrastructure-setup)
3. [Security Configuration](#security-configuration)
4. [Application Deployment](#application-deployment)
5. [Monitoring Setup](#monitoring-setup)
6. [Post-Deployment Verification](#post-deployment-verification)
7. [Troubleshooting](#troubleshooting)

---

## Prerequisites

### Required Tools

- kubectl 1.28+
- helm 3.12+
- terraform 1.5+ (for infrastructure provisioning)
- aws-cli 2.x (if deploying on AWS)

### Access Requirements

- Kubernetes cluster with at least 3 nodes (16GB RAM, 8 vCPU each)
- Domain names configured (*.healthflow.eg)
- SSL/TLS certificates
- AWS account with appropriate permissions (if using AWS)

---

## Infrastructure Setup

### Step 1: Provision Kubernetes Cluster

```bash
# Using AWS EKS
eksctl create cluster \
  --name hcx-egypt-prod \
  --region me-south-1 \
  --nodegroup-name standard-workers \
  --node-type m5.2xlarge \
  --nodes 3 \
  --nodes-min 3 \
  --nodes-max 10 \
  --managed
```

### Step 2: Create Namespaces

```bash
kubectl create namespace hcx-egypt-prod
kubectl create namespace monitoring
kubectl create namespace vault
```

### Step 3: Deploy Storage Classes

```bash
kubectl apply -f - <<YAML
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: fast-ssd
provisioner: kubernetes.io/aws-ebs
parameters:
  type: gp3
  iops: "3000"
  throughput: "125"
volumeBindingMode: WaitForFirstConsumer
YAML
```

---

## Security Configuration

### Step 1: Deploy HashiCorp Vault

```bash
# Add Helm repository
helm repo add hashicorp https://helm.releases.hashicorp.com
helm repo update

# Install Vault
helm install vault hashicorp/vault \
  --namespace vault \
  --set server.ha.enabled=true \
  --set server.ha.replicas=3 \
  --values deployment/vault/values.yml

# Initialize Vault
kubectl exec -n vault vault-0 -- vault operator init
```

### Step 2: Configure Secrets

```bash
# Enable Kubernetes auth
kubectl exec -n vault vault-0 -- vault auth enable kubernetes

# Create policies and roles
kubectl exec -n vault vault-0 -- vault policy write hcx-policy /vault/config/policy.hcl
kubectl exec -n vault vault-0 -- vault write auth/kubernetes/role/hcx-api \
  bound_service_account_names=hcx-api \
  bound_service_account_namespaces=hcx-egypt-prod \
  policies=hcx-policy \
  ttl=24h
```

### Step 3: Store Database Credentials

```bash
kubectl exec -n vault vault-0 -- vault kv put secret/hcx-egypt/database \
  username=hcx_admin \
  password=<SECURE_PASSWORD>
```

---

## Application Deployment

### Step 1: Deploy PostgreSQL

```bash
# Apply PostgreSQL configuration
kubectl apply -f phase1/infrastructure/kubernetes-dev.yml

# Wait for PostgreSQL to be ready
kubectl wait --for=condition=ready pod -l app=postgresql -n hcx-egypt-prod --timeout=300s

# Initialize database schema
kubectl exec -n hcx-egypt-prod postgresql-0 -- psql -U hcx_admin -d hcx_egypt -f /docker-entrypoint-initdb.d/schema.sql
```

### Step 2: Deploy Redis

```bash
# Deploy Redis cluster
kubectl apply -f deployment/performance/redis-cluster.yml

# Verify Redis cluster
kubectl exec -n hcx-egypt-prod redis-0 -- redis-cli cluster info
```

### Step 3: Deploy Kafka

```bash
# Add Strimzi operator
kubectl create -f 'https://strimzi.io/install/latest?namespace=hcx-egypt-prod'

# Deploy Kafka cluster
kubectl apply -f deployment/kafka/kafka-cluster.yml

# Create topics
kubectl apply -f deployment/kafka/topics.yml
```

### Step 4: Deploy Application Services

```bash
# Build and push Docker images
docker build -t healthflow/api-gateway:latest ./api-gateway
docker push healthflow/api-gateway:latest

docker build -t healthflow/hcx-api:latest ./hcx-apis
docker push healthflow/hcx-api:latest

# Deploy services
kubectl apply -f deployment/api-gateway.yml
kubectl apply -f deployment/hcx-api.yml
kubectl apply -f deployment/hcx-pipeline-jobs.yml

# Apply autoscaling
kubectl apply -f phase3/performance/hpa-config.yml
```

---

## Monitoring Setup

### Step 1: Deploy Prometheus

```bash
# Add Prometheus Helm repository
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

# Install Prometheus
helm install prometheus prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --values phase1/monitoring/prometheus-config.yml
```

### Step 2: Deploy Grafana Dashboards

```bash
# Import dashboards
kubectl apply -f phase1/monitoring/grafana-dashboard.json
```

### Step 3: Deploy Jaeger

```bash
kubectl apply -f phase3/monitoring/jaeger-tracing-config.yml
```

### Step 4: Deploy ELK Stack

```bash
kubectl apply -f phase3/monitoring/elk-stack-config.yml
```

---

## Post-Deployment Verification

### Health Checks

```bash
# Check all pods are running
kubectl get pods -n hcx-egypt-prod

# Check services
kubectl get svc -n hcx-egypt-prod

# Test API Gateway
curl https://api.healthflow.eg/actuator/health

# Test HCX API
curl https://api.healthflow.eg/api/v1/participant/search
```

### Performance Tests

```bash
# Run load test
kubectl run load-test --image=williamyeh/wrk --rm -it -- \
  wrk -t12 -c400 -d30s https://api.healthflow.eg/api/v1/health
```

### Security Audit

```bash
# Run security scan
kubectl run security-scan --image=aquasec/trivy --rm -it -- \
  trivy image healthflow/api-gateway:latest
```

---

## Troubleshooting

### Common Issues

**Issue**: Pods stuck in Pending state
```bash
# Check node resources
kubectl describe nodes

# Check PVC status
kubectl get pvc -n hcx-egypt-prod
```

**Issue**: Database connection failures
```bash
# Check PostgreSQL logs
kubectl logs -n hcx-egypt-prod postgresql-0

# Test connection
kubectl exec -n hcx-egypt-prod postgresql-0 -- psql -U hcx_admin -d hcx_egypt -c "SELECT 1"
```

**Issue**: High latency
```bash
# Check Jaeger traces
open http://jaeger.healthflow.eg:16686

# Check Prometheus metrics
kubectl port-forward -n monitoring svc/prometheus-server 9090:80
```

---

## Rollback Procedures

### Application Rollback

```bash
# Rollback to previous version
kubectl rollout undo deployment/api-gateway -n hcx-egypt-prod
kubectl rollout undo deployment/hcx-api -n hcx-egypt-prod

# Check rollout status
kubectl rollout status deployment/api-gateway -n hcx-egypt-prod
```

### Database Rollback

```bash
# Restore from backup
kubectl exec -n hcx-egypt-prod postgresql-0 -- /opt/hcx/scripts/restore-database.sh <BACKUP_DATE>
```

---

## Support

For deployment assistance, contact:
- DevOps Team: devops@healthflow.eg
- On-Call Engineer: +20 100 XXX XXXX
