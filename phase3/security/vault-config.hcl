# HashiCorp Vault Configuration for HCX Egypt
# Secrets Management and Encryption

storage "postgresql" {
  connection_url = "postgres://vault:VAULT_PASSWORD@postgresql:5432/vault?sslmode=require"
  ha_enabled     = "true"
}

listener "tcp" {
  address       = "0.0.0.0:8200"
  tls_cert_file = "/vault/config/tls/vault.crt"
  tls_key_file  = "/vault/config/tls/vault.key"
  
  # Security headers
  tls_min_version = "tls12"
  tls_cipher_suites = "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"
}

# API address
api_addr = "https://vault.healthflow.eg:8200"

# Cluster address for HA
cluster_addr = "https://vault.healthflow.eg:8201"

# UI
ui = true

# Telemetry
telemetry {
  prometheus_retention_time = "30s"
  disable_hostname = false
}

# Seal configuration (auto-unseal with cloud KMS)
seal "awskms" {
  region     = "me-south-1"  # AWS Middle East (Bahrain)
  kms_key_id = "alias/hcx-egypt-vault-unseal"
}

# Enable audit logging
# audit device will be configured via CLI after initialization
