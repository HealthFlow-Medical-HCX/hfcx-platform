package eg.gov.healthflow.hfcx.sdk.validators;

import com.fasterxml.jackson.databind.JsonNode;
import eg.gov.healthflow.hfcx.sdk.exceptions.ValidationException;
import eg.gov.healthflow.hfcx.sdk.enums.EgyptianGovernorate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Orchestrator that runs every Egypt-specific field validator against a participant
 * payload. Wires the four standalone validators (NationalID, IBAN, Phone, Governorate)
 * into the participant CRUD path; the JSON Schema is the first line of defence at the
 * registry write level, this class is the second at the API layer.
 *
 * <p>Throws {@link ValidationException} (with code {@code ERR-B-006} =
 * "ERR_INVALID_PAYLOAD" on the wire) on the first failure. The error message is
 * field-level so the integrator can tell exactly which field is wrong.
 *
 * <p>Fields that are not present in the payload are skipped — required-field checks are
 * the JSON Schema's responsibility, not this class's. Don't add structural required-field
 * checks here.
 */
public final class EgyptianFieldValidator {

    /** ISO 9362 BIC / SWIFT — 8 or 11 chars, matches the Organisation.json regex. */
    private static final Pattern BIC_PATTERN =
            Pattern.compile("^[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?$");

    /** Meeza wallet card — 16 digits. */
    private static final Pattern MEEZA_PATTERN = Pattern.compile("^\\d{16}$");

    /** URI of the Egyptian National-ID identifier system. */
    private static final String NATIONAL_ID_SYSTEM =
            "http://healthflow.gov.eg/identifier/national-id";

    private EgyptianFieldValidator() {
    }

    /**
     * Walk a FHIR Bundle and validate every Egypt-specific field embedded in
     * its entries.
     *
     * <p>Per Decision 14, this runs at the recipient HCX-API instance after the
     * inbound JWE has been decrypted. The Bundle is the cleartext payload.
     *
     * <p>For each entry resource:
     * <ul>
     *   <li>{@code Patient} — the National-ID identifier slice (system =
     *       http://healthflow.gov.eg/identifier/national-id) is checked with
     *       the full Luhn-style validator.</li>
     *   <li>{@code Patient.telecom} / {@code Practitioner.telecom} — phone
     *       contacts are validated when the resource address country is EG
     *       (or absent — the platform is Egypt-only by deployment).</li>
     *   <li>{@code Organization.payment} (FHIR-extension namespace) — IBANs are
     *       checked against the Egyptian mod-97 algorithm.</li>
     * </ul>
     *
     * <p>Returns a list of human-readable errors. An empty list means the
     * Bundle passed Egyptian field checks. The caller decides whether to
     * raise on the first error or aggregate.
     *
     * <p>Tolerates JSON shapes that are not actually Bundles (e.g. a single
     * resource): walks {@code resourceType=Bundle.entry[*].resource} when
     * present, otherwise treats the root as a single resource.
     */
    public static List<String> validateFhirBundle(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return Collections.emptyList();
        }
        List<String> errors = new ArrayList<>();
        if ("Bundle".equals(textOrEmpty(root.path("resourceType"))) && root.has("entry")) {
            for (JsonNode entry : root.path("entry")) {
                JsonNode resource = entry.path("resource");
                if (resource.isMissingNode() || resource.isNull()) continue;
                validateResource(resource, errors);
            }
        } else {
            validateResource(root, errors);
        }
        return errors;
    }

    private static void validateResource(JsonNode resource, List<String> errors) {
        String type = textOrEmpty(resource.path("resourceType"));
        switch (type) {
            case "Patient":
                validatePatient(resource, errors);
                break;
            case "Practitioner":
                validateTelecoms(resource.path("telecom"), "Practitioner", errors);
                break;
            case "Organization":
                validateOrganization(resource, errors);
                break;
            default:
                // No Egyptian-specific checks for other resource types yet.
        }
    }

    private static void validatePatient(JsonNode patient, List<String> errors) {
        // National ID identifier slice
        for (JsonNode identifier : patient.path("identifier")) {
            String system = textOrEmpty(identifier.path("system"));
            if (NATIONAL_ID_SYSTEM.equals(system)) {
                String value = textOrEmpty(identifier.path("value"));
                if (value.isEmpty()) {
                    errors.add("Patient.identifier[system=national-id].value is missing");
                } else if (!EgyptianNationalIDValidator.isValid(value)) {
                    errors.add("Patient.identifier[system=national-id].value '" + value
                            + "' is not a valid Egyptian National ID");
                }
            }
        }
        validateTelecoms(patient.path("telecom"), "Patient", errors);
    }

    private static void validateOrganization(JsonNode org, List<String> errors) {
        validateTelecoms(org.path("telecom"), "Organization", errors);
        // Some payment extensions store an iban field at the resource root.
        for (JsonNode extension : org.path("extension")) {
            String url = textOrEmpty(extension.path("url"));
            if (url.toLowerCase().contains("iban")) {
                String iban = textOrEmpty(extension.path("valueString"));
                if (!iban.isEmpty() && !EgyptianIBANValidator.isValid(iban)) {
                    errors.add("Organization extension[" + url + "] '" + iban
                            + "' is not a valid Egyptian IBAN");
                }
            }
        }
    }

    private static void validateTelecoms(JsonNode telecoms, String resourceType, List<String> errors) {
        if (telecoms == null || !telecoms.isArray()) return;
        for (JsonNode tel : telecoms) {
            String system = textOrEmpty(tel.path("system"));
            if (!"phone".equalsIgnoreCase(system)) continue;
            String value = textOrEmpty(tel.path("value"));
            if (value.isEmpty()) continue;
            if (!EgyptianPhoneValidator.isValid(value)) {
                errors.add(resourceType + ".telecom[system=phone].value '" + value
                        + "' is not a valid Egyptian phone number");
            }
        }
    }

    private static String textOrEmpty(JsonNode n) {
        return n == null || n.isMissingNode() || n.isNull() ? "" : n.asText("");
    }

    /**
     * Validates every Egypt-specific field on a participant payload (whether the
     * request shape is the org-CRUD body or the onboarding payload — both pass
     * through here).
     *
     * @param participant the request body as a {@code Map<String, Object>}; nulls
     *                    are tolerated and treated as no-op
     * @throws ValidationException with ERR_INVALID_PAYLOAD on the first invalid field
     */
    @SuppressWarnings("unchecked")
    public static void validate(Map<String, Object> participant) throws ValidationException {
        if (participant == null) {
            return;
        }

        // 1. National ID — 14 digits, governorate code, valid date, Luhn check.
        Object nid = participant.get("national_id");
        if (nid != null && !nid.toString().isEmpty()) {
            if (!EgyptianNationalIDValidator.isValid(nid.toString())) {
                throw new ValidationException(
                        "Invalid Egyptian National ID: must be 14 digits with valid governorate code, birth date, and Luhn check digit");
            }
        }

        // 2. Primary mobile — Egyptian mobile or landline. Skipped if absent.
        Object phone = participant.get("primary_mobile");
        if (phone != null && !phone.toString().isEmpty()) {
            if (!EgyptianPhoneValidator.isValid(phone.toString())) {
                throw new ValidationException(
                        "Invalid Egyptian primary_mobile: expected +20-prefixed mobile or landline number");
            }
        }

        // 3. Additional mobiles, if any.
        Object addlMobile = participant.get("additional_mobile");
        if (addlMobile instanceof Iterable) {
            int idx = 0;
            for (Object m : (Iterable<Object>) addlMobile) {
                if (m != null && !m.toString().isEmpty()
                        && !EgyptianPhoneValidator.isValid(m.toString())) {
                    throw new ValidationException(
                            "Invalid Egyptian additional_mobile[" + idx + "]: expected +20-prefixed number");
                }
                idx++;
            }
        }

        // 4. Payment details — IBAN+BIC or Meeza_card. The schema's oneOf already
        // enforces the choice; we re-validate the IBAN's mod-97 check (which the
        // schema regex cannot express) and the BIC pattern.
        Object pd = participant.get("payment_details");
        if (pd instanceof Map) {
            Map<String, Object> payment = (Map<String, Object>) pd;

            Object iban = payment.get("iban");
            if (iban != null) {
                if (!EgyptianIBANValidator.isValid(iban.toString())) {
                    throw new ValidationException(
                            "Invalid Egyptian IBAN: must be 29 chars (EG + 27 digits) with valid mod-97 check");
                }
                Object bic = payment.get("bic");
                if (bic == null || !BIC_PATTERN.matcher(bic.toString()).matches()) {
                    throw new ValidationException(
                            "Invalid BIC: required when iban is provided; expected ISO 9362 format");
                }
            }

            Object meeza = payment.get("meeza_card");
            if (meeza != null && !MEEZA_PATTERN.matcher(meeza.toString()).matches()) {
                throw new ValidationException(
                        "Invalid Meeza card number: must be 16 digits");
            }
        }

        // 5. Address governorate — must be one of the 27 official governorates.
        Object addr = participant.get("address");
        if (addr instanceof Map) {
            Object gov = ((Map<String, Object>) addr).get("governorate");
            if (gov != null && !gov.toString().isEmpty()) {
                try {
                    EgyptianGovernorate.fromEnglishName(gov.toString());
                } catch (IllegalArgumentException e) {
                    throw new ValidationException(
                            "Invalid governorate: must be one of the 27 official Egyptian governorates");
                }
            }
        }
    }
}
