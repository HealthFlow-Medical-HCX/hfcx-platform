# Cross-SDK API parity — Java SDK

This file tracks the Java SDK's coverage of the cross-SDK parity table
defined in Section 9 of the agentic delivery prompt. Every checkbox is
populated as the corresponding sprint lands; the file is final at J7.

After all four SDKs (Java, Python, .NET, JavaScript) reach 1.0.0, an
audit runs against this file in each repo and surfaces divergences.

## Capability checklist

| Capability | Java symbol | Sprint | Status |
|---|---|---|---|
| Submit a claim | `HfcxClient.submitClaim` | J4 | TODO(J4) |
| Submit a preauth | `HfcxClient.submitPreauth` | J4 | TODO(J4) |
| Check eligibility | `HfcxClient.checkEligibility` | J4 | TODO(J4) |
| Send communication | `HfcxClient.sendCommunication` | J4 | TODO(J4) |
| Notify payment | `HfcxClient.notifyPayment` | J4 | TODO(J4) |
| JWE encrypt | `JWEHelper.encrypt` | J1 | ✓ |
| JWE decrypt | `JWEHelper.decrypt` | J1 | ✓ |
| Recipient pipeline | `RecipientHandler` | J5 | TODO(J5) |
| Get bearer token | `KeycloakTokenClient.getToken` | J2 | TODO(J2) |
| Fetch recipient cert | `RegistryClient.getRecipientCert` | J4 | TODO(J4) |
| Validate FHIR Bundle | `FhirValidationService.validate` | J1 (impl) / J5 (wired) | ✓ (impl) |
| Validate Egyptian National-ID | `EgyptianNationalIDValidator.isValid` | J1 | ✓ |
| Validate Egyptian phone | `EgyptianPhoneValidator.isValid` | J1 | ✓ |
| Validate Egyptian IBAN | `EgyptianIBANValidator.isValid` | J1 | ✓ |
| Egyptian governorate enum | `EgyptianGovernorate` (27 values) | J1 | ✓ |
| Exception: protocol error | `ProtocolException` | J6 | TODO(J6); J1 ships interim `ValidationException` |
| Exception: business error | `BusinessException` | J6 | TODO(J6) |
| Exception: technical error | `TechnicalException` | J6 | TODO(J6) |

## Allowed-divergence record

(Empty at J1. Populated as cross-SDK audits surface idiomatic differences
that are accepted as design choices. See Section 9 of the agentic delivery
prompt for what counts as allowed vs. not allowed.)

## Sister SDK status

| SDK | Repo | 1.0.0 status |
|---|---|---|
| Java | `HealthFlow-Medical-HCX/hfcx-sdk-java` | In sprint (J1 done) |
| Python | `HealthFlow-Medical-HCX/hfcx-sdk-python` | Not started |
| .NET | `HealthFlow-Medical-HCX/hfcx-sdk-dotnet` | Not started |
| JavaScript | `HealthFlow-Medical-HCX/hfcx-sdk-javascript` | Not started |
