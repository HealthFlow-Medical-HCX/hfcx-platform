Profile: HfcxClaim
Parent: Claim
Id: hfcx-claim
Title: "HealthFlow HCX Claim"
Description: "Claim profile for the Egyptian HCX exchange."

* insurance 1..*
* provider 1..1
* patient 1..1
* diagnosis 1..*
* diagnosis.diagnosisCodeableConcept from http://hl7.org/fhir/sid/icd-10 (extensible)
* item 1..*
* item.productOrService MS
