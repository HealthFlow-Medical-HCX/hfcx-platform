# Keycloak Configuration for HCX Egypt

This directory contains the Keycloak realm configuration for the HCX Egypt deployment.

## Files

- `hcx-egypt-realm.json` - Complete realm export including clients, roles, and configuration

## Realm: hcx-egypt

### Configured Clients

| Client ID | Type | Purpose |
|-----------|------|---------|
| `admin-cli` | Service | Administrative operations |
| `beneficiary-portal` | Public | Beneficiary web application |
| `bsp-portal` | Public | BSP (Benefit Service Provider) web application |
| `hcx-api` | Confidential | HCX API service account |
| `hcx-api-gateway` | Confidential | API Gateway service account |
| `hcx-functional-tester` | Confidential | Testing and QA client |
| `hcx-provider-service` | Confidential | Provider service account |
| `mock-payer-app` | Public | Mock payer web application |
| `opd-portal` | Public | OPD (Outpatient Department) portal |

### Configured Roles

| Role | Description |
|------|-------------|
| `admin` | Administrative access to all functions |
| `beneficiary` | Beneficiary/patient user role |
| `hcx-gateway` | Gateway service role with full API access |
| `payer` | Payer organization role |
| `provider` | Healthcare provider organization role |

### Role-Based Access Control (RBAC)

The API Gateway enforces RBAC based on roles defined in `api-gateway/src/main/resources/rbac.yaml`. Each role has specific permissions for HCX protocol endpoints.

**Important**: Roles defined in `rbac.yaml` must exist in Keycloak and be assigned to users/service accounts for authorization to work correctly.

## Importing the Realm

### Method 1: Docker Volume Mount (Recommended for new deployments)

```bash
docker run -d \
  --name hcx-keycloak \
  -p 8080:8080 \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  -v $(pwd)/hcx-egypt-realm.json:/opt/keycloak/data/import/hcx-egypt-realm.json \
  quay.io/keycloak/keycloak:22.0.5 \
  start-dev --import-realm
```

### Method 2: Admin API (For existing Keycloak instances)

```bash
# Get admin token
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/realms/master/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=admin-cli&username=admin&password=admin" \
  | jq -r .access_token)

# Import realm
curl -X POST http://localhost:8080/admin/realms \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d @hcx-egypt-realm.json
```

### Method 3: Keycloak Admin Console

1. Navigate to http://localhost:8080/admin
2. Login with admin credentials
3. Click "Add realm" in the top-left dropdown
4. Click "Select file" and choose `hcx-egypt-realm.json`
5. Click "Create"

## Exporting the Realm

To export the current realm configuration:

```bash
# Using export command
docker exec hcx-keycloak /opt/keycloak/bin/kc.sh export \
  --dir /tmp \
  --realm hcx-egypt \
  --users realm_file

# Copy from container
docker cp hcx-keycloak:/tmp/hcx-egypt-realm.json ./hcx-egypt-realm.json
```

## Client Secrets

**Security Note**: Client secrets are included in the realm export. For production deployments:

1. Rotate all client secrets after import
2. Store secrets securely (e.g., HashiCorp Vault, AWS Secrets Manager)
3. Use environment variables for secret injection
4. Never commit production secrets to version control

## Service Account Setup

For service-to-service authentication, clients with `serviceAccountsEnabled: true` can use the client credentials grant:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/realms/hcx-egypt/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=hcx-api&client_secret=YOUR_SECRET" \
  | jq -r .access_token)
```

### Assigning Roles to Service Accounts

Service accounts need roles assigned to access protected endpoints:

```bash
# Get admin token
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/realms/master/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=admin-cli&username=admin&password=admin" \
  | jq -r .access_token)

# Get client UUID
CLIENT_UUID=$(curl -s http://localhost:8080/admin/realms/hcx-egypt/clients?clientId=hcx-api \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq -r .[0].id)

# Get service account user ID
SERVICE_ACCOUNT_USER=$(curl -s http://localhost:8080/admin/realms/hcx-egypt/clients/$CLIENT_UUID/service-account-user \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq -r .id)

# Get role details
ROLE=$(curl -s http://localhost:8080/admin/realms/hcx-egypt/roles/provider \
  -H "Authorization: Bearer $ADMIN_TOKEN")

# Assign role
curl -X POST http://localhost:8080/admin/realms/hcx-egypt/users/$SERVICE_ACCOUNT_USER/role-mappings/realm \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d "[$ROLE]"
```

## Testing Authentication

### Test Client Credentials Flow

```bash
# Get token
curl -X POST http://localhost:8080/realms/hcx-egypt/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=hcx-functional-tester&client_secret=functional-tester-secret"

# Decode token to verify roles
echo "TOKEN_HERE" | cut -d "." -f2 | base64 -d | jq .
```

## Troubleshooting

### Issue: "Authorization header is missing"
- Ensure the JWT token is included in the `Authorization: Bearer <token>` header

### Issue: "ERR_ACCESS_DENIED"
- Verify the service account has the required role assigned
- Check that the role exists in both Keycloak and `rbac.yaml`
- Ensure the role is included in the JWT token's `realm_access.roles` array

### Issue: "Invalid token"
- Check that `JWT_JWK_URL` in API Gateway points to the correct Keycloak endpoint
- Verify the token is not expired (default: 5 minutes)
- Ensure the token was issued by the correct realm

## Production Recommendations

1. **Enable HTTPS**: Configure SSL/TLS certificates for Keycloak
2. **Change Admin Password**: Use a strong password for the admin account
3. **Enable Email Verification**: Configure SMTP for user registration
4. **Set Up Backup**: Regularly export and backup realm configuration
5. **Monitor Sessions**: Enable event logging and monitoring
6. **Rate Limiting**: Configure brute force protection
7. **Token Expiration**: Adjust token lifespans based on security requirements

## References

- [Keycloak Documentation](https://www.keycloak.org/documentation)
- [HCX Protocol Specification](https://docs.hcxprotocol.io/)
- [OAuth 2.0 Client Credentials](https://oauth.net/2/grant-types/client-credentials/)

---

**Last Updated**: December 3, 2025  
**Keycloak Version**: 22.0.5  
**Realm**: hcx-egypt
