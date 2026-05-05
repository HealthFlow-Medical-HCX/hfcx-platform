package org.healthflow.hcx.utils.validators;

import org.healthflow.common.exception.ClientException;
import org.healthflow.common.exception.ErrorCodes;
import org.healthflow.hcx.enums.EgyptianGovernorate;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Orchestrator that runs every Egypt-specific field validator against a participant
 * payload. Wires the four standalone validators (NationalID, IBAN, Phone, Governorate)
 * into the participant CRUD path; the JSON Schema is the first line of defence at the
 * registry write level, this class is the second at the API layer.
 *
 * <p>Throws {@link ClientException} with {@link ErrorCodes#ERR_INVALID_PAYLOAD} on the
 * first failure. The error message is field-level so the integrator can tell exactly
 * which field is wrong.
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

    private EgyptianFieldValidator() {
    }

    /**
     * Validates every Egypt-specific field on a participant payload (whether the
     * request shape is the org-CRUD body or the onboarding payload — both pass
     * through here).
     *
     * @param participant the request body as a {@code Map<String, Object>}; nulls
     *                    are tolerated and treated as no-op
     * @throws ClientException with ERR_INVALID_PAYLOAD on the first invalid field
     */
    @SuppressWarnings("unchecked")
    public static void validate(Map<String, Object> participant) throws ClientException {
        if (participant == null) {
            return;
        }

        // 1. National ID — 14 digits, governorate code, valid date, Luhn check.
        Object nid = participant.get("national_id");
        if (nid != null && !nid.toString().isEmpty()) {
            if (!EgyptianNationalIDValidator.isValid(nid.toString())) {
                throw new ClientException(ErrorCodes.ERR_INVALID_PAYLOAD,
                        "Invalid Egyptian National ID: must be 14 digits with valid governorate code, birth date, and Luhn check digit");
            }
        }

        // 2. Primary mobile — Egyptian mobile or landline. Skipped if absent.
        Object phone = participant.get("primary_mobile");
        if (phone != null && !phone.toString().isEmpty()) {
            if (!EgyptianPhoneValidator.isValid(phone.toString())) {
                throw new ClientException(ErrorCodes.ERR_INVALID_PAYLOAD,
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
                    throw new ClientException(ErrorCodes.ERR_INVALID_PAYLOAD,
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
                    throw new ClientException(ErrorCodes.ERR_INVALID_PAYLOAD,
                            "Invalid Egyptian IBAN: must be 29 chars (EG + 27 digits) with valid mod-97 check");
                }
                Object bic = payment.get("bic");
                if (bic == null || !BIC_PATTERN.matcher(bic.toString()).matches()) {
                    throw new ClientException(ErrorCodes.ERR_INVALID_PAYLOAD,
                            "Invalid BIC: required when iban is provided; expected ISO 9362 format");
                }
            }

            Object meeza = payment.get("meeza_card");
            if (meeza != null && !MEEZA_PATTERN.matcher(meeza.toString()).matches()) {
                throw new ClientException(ErrorCodes.ERR_INVALID_PAYLOAD,
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
                    throw new ClientException(ErrorCodes.ERR_INVALID_PAYLOAD,
                            "Invalid governorate: must be one of the 27 official Egyptian governorates");
                }
            }
        }
    }
}
