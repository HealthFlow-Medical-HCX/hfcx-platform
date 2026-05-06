Profile: HfcxPatient
Parent: Patient
Id: hfcx-patient
Title: "HealthFlow HCX Patient"
Description: "Patient profile for the Egyptian HCX, requiring National-ID and Egyptian address."

* identifier 1..* MS
* identifier ^slicing.discriminator.type = #pattern
* identifier ^slicing.discriminator.path = "system"
* identifier ^slicing.rules = #open
* identifier contains nationalId 1..1 MS
* identifier[nationalId].system 1..1
* identifier[nationalId].system = "http://healthflow.gov.eg/identifier/national-id"
* identifier[nationalId].value 1..1
* identifier[nationalId].value obeys hfcx-nid-format

* address 1..* MS
* address.country 1..1
* address.country = "EG"
* address.state 1..1
* address.state from EgyptianGovernorateVS (extensible)

Invariant: hfcx-nid-format
Description: "Egyptian National-ID is exactly 14 digits"
Severity: #error
Expression: "matches('^[0-9]{14}$')"
