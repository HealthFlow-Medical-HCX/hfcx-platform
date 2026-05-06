// TODO: Clinical SME to populate the full Egyptian service-category catalog.
// This is a placeholder starter set used only to validate IG plumbing.

CodeSystem: EgyptianServiceCategoryCS
Id: egyptian-service-category-cs
Title: "Egyptian Service Category (Starter)"
Description: "Starter code system for Egyptian service categories. TODO: replace with SME-curated list."
* ^url = "https://healthflow.gov.eg/fhir/CodeSystem/egyptian-service-category"
* ^caseSensitive = true
* #outpatient "Outpatient"
* #inpatient "Inpatient"
* #pharmacy "Pharmacy"
* #lab "Laboratory"

ValueSet: EgyptianServiceCategoryVS
Id: egyptian-service-category-vs
Title: "Egyptian Service Categories (Starter)"
Description: "Starter value set of Egyptian service categories. TODO: clinical SME refinement required."
* ^url = "https://healthflow.gov.eg/fhir/ValueSet/egyptian-service-category"
* include codes from system EgyptianServiceCategoryCS
