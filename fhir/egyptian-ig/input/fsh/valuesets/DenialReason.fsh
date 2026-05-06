// TODO: Clinical / payer SME to populate the full denial-reason catalog
// (alignment with Egyptian regulator guidance pending). Placeholder only.

CodeSystem: HfcxDenialReasonCS
Id: hfcx-denial-reason-cs
Title: "HFCX Denial Reasons (Starter)"
Description: "Starter code system for claim denial reasons. TODO: SME refinement."
* ^url = "https://healthflow.gov.eg/fhir/CodeSystem/denial-reason"
* ^caseSensitive = true
* #not-covered "Service not covered by policy"
* #exceeds-limit "Exceeds policy limit"
* #missing-authorization "Missing prior authorization"
* #ineligible "Member ineligible at date of service"

ValueSet: HfcxDenialReasonVS
Id: hfcx-denial-reason-vs
Title: "HFCX Denial Reasons (Starter)"
Description: "Starter value set of claim denial reasons. TODO: clinical / payer SME refinement required."
* ^url = "https://healthflow.gov.eg/fhir/ValueSet/denial-reason"
* include codes from system HfcxDenialReasonCS
