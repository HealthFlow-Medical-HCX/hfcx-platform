# Disaster Recovery Plan for HCX Egypt Platform

**Version**: 1.0  
**Last Updated**: December 1, 2025  
**Owner**: HealthFlow DevOps Team

---

## 1. Overview

This document outlines the disaster recovery (DR) strategy for the HCX Egypt platform, ensuring business continuity in the event of system failures, data loss, or catastrophic incidents.

### Recovery Objectives

- **Recovery Time Objective (RTO)**: 4 hours
- **Recovery Point Objective (RPO)**: 15 minutes
- **Availability Target**: 99.9% uptime (8.76 hours downtime per year)

---

## 2. Backup Strategy

### 2.1 Database Backups

**PostgreSQL Continuous Archiving**

The platform uses PostgreSQL Write-Ahead Log (WAL) archiving for point-in-time recovery.

**Configuration**:
```bash
# Enable WAL archiving
wal_level = replica
archive_mode = on
archive_command = 'aws s3 cp %p s3://hcx-egypt-backups/wal/%f --region me-south-1 --sse AES256'
archive_timeout = 300  # Archive every 5 minutes
```

**Full Backups**:
- **Frequency**: Daily at 02:00 AM Cairo time
- **Retention**: 30 days
- **Storage**: AWS S3 with versioning enabled
- **Encryption**: AES-256 server-side encryption

**Backup Script**:
```bash
#!/bin/bash
# /opt/hcx/scripts/backup-database.sh

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="hcx_egypt_backup_${TIMESTAMP}.sql.gz"

pg_dump -h postgresql -U hcx_admin -d hcx_egypt | gzip > /tmp/${BACKUP_FILE}
aws s3 cp /tmp/${BACKUP_FILE} s3://hcx-egypt-backups/daily/ --sse AES256
rm /tmp/${BACKUP_FILE}

# Verify backup integrity
aws s3api head-object --bucket hcx-egypt-backups --key daily/${BACKUP_FILE}
```

**Automated Testing**:
- Weekly restoration tests to a staging environment
- Automated validation of backup integrity
- Alert on backup failures via PagerDuty

### 2.2 Redis Backups

**Configuration**:
```conf
# Redis persistence
save 900 1      # Save after 900 seconds if at least 1 key changed
save 300 10     # Save after 300 seconds if at least 10 keys changed
save 60 10000   # Save after 60 seconds if at least 10000 keys changed

# AOF (Append-Only File) for durability
appendonly yes
appendfsync everysec
```

**Backup Schedule**:
- **Frequency**: Every 6 hours
- **Retention**: 7 days
- **Storage**: AWS S3

### 2.3 Kafka Backups

**Topic Replication**:
- Replication factor: 3
- Min in-sync replicas: 2

**Backup Strategy**:
- Mirror topics to a secondary Kafka cluster in a different availability zone
- Retain messages for 7 days
- Use Kafka Connect to archive critical topics to S3

### 2.4 Configuration and Secrets Backups

**Vault Snapshots**:
- **Frequency**: Hourly
- **Retention**: 24 hours for hourly, 30 days for daily
- **Storage**: Encrypted S3 bucket with versioning

**Kubernetes Configuration**:
- All manifests stored in Git repository
- Automated backup of etcd cluster every 6 hours
- Velero for cluster-level backups

---

## 3. High Availability Architecture

### 3.1 Multi-AZ Deployment

The platform is deployed across three availability zones in the AWS Middle East (Bahrain) region.

**Component Distribution**:

| Component | AZ-1 | AZ-2 | AZ-3 |
|-----------|------|------|------|
| API Gateway | 2 pods | 2 pods | 2 pods |
| HCX API | 3 pods | 3 pods | 3 pods |
| PostgreSQL | Primary | Standby | Standby |
| Redis | Master | Replica | Replica |
| Kafka | Broker 1 | Broker 2 | Broker 3 |

### 3.2 Database Replication

**PostgreSQL Streaming Replication**:
- 1 primary + 2 synchronous standbys
- Automatic failover using Patroni
- Connection pooling via PgBouncer

**Failover Process**:
1. Patroni detects primary failure (5-second timeout)
2. Promotes synchronous standby to primary
3. Updates DNS records
4. Notifies operations team
5. Total failover time: < 30 seconds

### 3.3 Load Balancing

**AWS Application Load Balancer (ALB)**:
- Health checks every 10 seconds
- Automatic removal of unhealthy targets
- Cross-zone load balancing enabled
- SSL/TLS termination with ACM certificates

---

## 4. Disaster Recovery Procedures

### 4.1 Database Recovery

**Scenario: Complete Database Loss**

**Steps**:
1. Provision new PostgreSQL instance
2. Restore latest full backup from S3
3. Apply WAL files for point-in-time recovery
4. Verify data integrity
5. Update application connection strings
6. Resume operations

**Estimated Time**: 2-3 hours

**Script**:
```bash
#!/bin/bash
# /opt/hcx/scripts/restore-database.sh

BACKUP_DATE=$1  # Format: YYYYMMDD_HHMMSS

# Download backup
aws s3 cp s3://hcx-egypt-backups/daily/hcx_egypt_backup_${BACKUP_DATE}.sql.gz /tmp/

# Restore
gunzip < /tmp/hcx_egypt_backup_${BACKUP_DATE}.sql.gz | psql -h postgresql-new -U hcx_admin -d hcx_egypt

# Apply WAL files for PITR
restore_command = 'aws s3 cp s3://hcx-egypt-backups/wal/%f %p'
recovery_target_time = '2025-12-01 14:30:00 EET'
```

### 4.2 Complete Regional Failure

**Scenario: AWS Middle East Region Outage**

**Steps**:
1. Activate DR site in AWS Europe (Frankfurt) region
2. Restore database from cross-region replicated backups
3. Update DNS to point to DR site
4. Verify all services are operational
5. Communicate status to stakeholders

**Estimated Time**: 4-6 hours

**Prerequisites**:
- Cross-region S3 replication enabled
- DR environment pre-provisioned in standby mode
- Regular DR drills (quarterly)

---

## 5. Monitoring and Alerting

### 5.1 Critical Alerts

**Immediate Response (P1)**:
- Database primary failure
- Complete service outage
- Data corruption detected
- Security breach

**High Priority (P2)**:
- Backup failures
- Replication lag > 5 minutes
- Disk space > 85%
- High error rates (> 5%)

### 5.2 Alert Channels

- **PagerDuty**: On-call engineer rotation
- **Slack**: #hcx-egypt-alerts channel
- **Email**: ops-team@healthflow.eg
- **SMS**: For P1 incidents

---

## 6. Testing and Validation

### 6.1 DR Drill Schedule

- **Monthly**: Backup restoration test
- **Quarterly**: Full DR failover simulation
- **Annually**: Complete regional failover test

### 6.2 Success Criteria

- RTO < 4 hours
- RPO < 15 minutes
- Zero data loss for committed transactions
- All critical services operational
- Successful communication to stakeholders

---

## 7. Contact Information

**On-Call Rotation**: +20 100 XXX XXXX  
**Incident Commander**: [Name], DevOps Lead  
**Database Administrator**: [Name]  
**Security Officer**: [Name]

**Escalation Path**:
1. On-call engineer
2. DevOps lead
3. CTO
4. CEO

---

## 8. Appendices

### A. Backup Verification Checklist

- [ ] Backup file exists in S3
- [ ] File size is reasonable (> 1GB)
- [ ] Backup completed without errors
- [ ] Restoration test successful
- [ ] Data integrity verified
- [ ] Backup retention policy enforced

### B. Post-Incident Review Template

- Incident summary
- Root cause analysis
- Timeline of events
- Actions taken
- Lessons learned
- Action items for improvement
