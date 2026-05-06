package org.healthflow.hcx.utils.validators;

import org.healthflow.common.exception.ClientException;
import org.healthflow.common.exception.ErrorCodes;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Unit tests for the P0-4b validator orchestrator. Builds participant payload
 * fragments and asserts that every Egypt-specific field is checked, every
 * Indian-locale value is rejected, and absent fields are tolerated.
 */
public class EgyptianFieldValidatorTest {

    /** A real-format Egyptian National ID: 2 (1900s), 1980-05-15, Cairo (01), seq 234, valid Luhn. */
    private static final String VALID_NATIONAL_ID = computeValidNationalId();

    /** Reference IBAN cited in the Egyptian banking docs as a valid sample. */
    private static final String VALID_IBAN = "EG380019000500000000263180002";

    /** Sample BIC for National Bank of Egypt. */
    private static final String VALID_BIC = "NBEGEGCX";

    /** Random-looking 16-digit Meeza wallet number. */
    private static final String VALID_MEEZA = "5061111122223333";

    private static String computeValidNationalId() {
        // EgyptianNationalIDValidator's substring offsets parse positions 0..12 as
        // the body (13 chars): 1 century + 4 year + 2 month + 2 day + 2 governorate
        // + 2 sequence = 13. The 14th char is the Luhn check computed below.
        String body = "2" + "1980" + "05" + "15" + "01" + "23"; // 13 digits
        int sum = 0;
        for (int i = 0; i < 13; i++) {
            int digit = Character.getNumericValue(body.charAt(i));
            if (i % 2 == 0) {
                digit *= 2;
                if (digit > 9) digit -= 9;
            }
            sum += digit;
        }
        int check = (10 - (sum % 10)) % 10;
        return body + check;
    }

    // ----- positive cases -----

    @Test
    public void nullPayloadIsNoOp() throws Exception {
        EgyptianFieldValidator.validate(null);
    }

    @Test
    public void emptyPayloadIsAccepted() throws Exception {
        EgyptianFieldValidator.validate(new HashMap<>());
    }

    @Test
    public void fullValidEgyptianPayloadAccepted() throws Exception {
        Map<String, Object> payment = new HashMap<>();
        payment.put("iban", VALID_IBAN);
        payment.put("bic", VALID_BIC);

        Map<String, Object> address = new HashMap<>();
        address.put("governorate", "Cairo");
        address.put("postal_code", "11511");

        Map<String, Object> p = new HashMap<>();
        p.put("national_id", VALID_NATIONAL_ID);
        p.put("primary_mobile", "+201001234567");
        p.put("payment_details", payment);
        p.put("address", address);
        p.put("additional_mobile", Arrays.asList("+201112345678"));

        EgyptianFieldValidator.validate(p);
    }

    @Test
    public void meezaOnlyPaymentAccepted() throws Exception {
        Map<String, Object> payment = new HashMap<>();
        payment.put("meeza_card", VALID_MEEZA);

        Map<String, Object> p = new HashMap<>();
        p.put("payment_details", payment);

        EgyptianFieldValidator.validate(p);
    }

    @Test
    public void absentOptionalFieldsAreSkipped() throws Exception {
        // Only roles + status — none of the Egyptian-specific fields. Must not throw.
        Map<String, Object> p = new HashMap<>();
        p.put("roles", Collections.singletonList("provider"));
        p.put("status", "Active");
        EgyptianFieldValidator.validate(p);
    }

    // ----- negative cases -----

    @Test
    public void invalidNationalIdRejected() {
        Map<String, Object> p = new HashMap<>();
        p.put("national_id", "12345678901234"); // wrong century digit + bad luhn
        assertReject(p, "Invalid Egyptian National ID");
    }

    @Test
    public void shortNationalIdRejected() {
        Map<String, Object> p = new HashMap<>();
        p.put("national_id", "123");
        assertReject(p, "Invalid Egyptian National ID");
    }

    @Test
    public void invalidIbanRejected() {
        Map<String, Object> payment = new HashMap<>();
        payment.put("iban", "INVALID-IBAN");
        payment.put("bic", VALID_BIC);
        Map<String, Object> p = new HashMap<>();
        p.put("payment_details", payment);
        assertReject(p, "Invalid Egyptian IBAN");
    }

    @Test
    public void ibanProvidedWithoutBicRejected() {
        Map<String, Object> payment = new HashMap<>();
        payment.put("iban", VALID_IBAN);
        // bic missing
        Map<String, Object> p = new HashMap<>();
        p.put("payment_details", payment);
        assertReject(p, "Invalid BIC");
    }

    @Test
    public void invalidBicFormatRejected() {
        Map<String, Object> payment = new HashMap<>();
        payment.put("iban", VALID_IBAN);
        payment.put("bic", "lowercase8"); // wrong case + 10 chars (not 8 or 11)
        Map<String, Object> p = new HashMap<>();
        p.put("payment_details", payment);
        assertReject(p, "Invalid BIC");
    }

    @Test
    public void invalidMeezaRejected() {
        Map<String, Object> payment = new HashMap<>();
        payment.put("meeza_card", "1234"); // not 16 digits
        Map<String, Object> p = new HashMap<>();
        p.put("payment_details", payment);
        assertReject(p, "Invalid Meeza card number");
    }

    @Test
    public void invalidMobileRejected() {
        Map<String, Object> p = new HashMap<>();
        p.put("primary_mobile", "555-INDIAN-NUMBER");
        assertReject(p, "Invalid Egyptian primary_mobile");
    }

    @Test
    public void invalidAdditionalMobileRejected() {
        Map<String, Object> p = new HashMap<>();
        p.put("additional_mobile", Arrays.asList("+201001234567", "not-a-phone"));
        assertReject(p, "Invalid Egyptian additional_mobile[1]");
    }

    @Test
    public void invalidGovernorateRejected() {
        Map<String, Object> address = new HashMap<>();
        address.put("governorate", "Hyderabad");
        address.put("postal_code", "12345");
        Map<String, Object> p = new HashMap<>();
        p.put("address", address);
        assertReject(p, "Invalid governorate");
    }

    // ----- helper -----

    private void assertReject(Map<String, Object> payload, String expectedMessageFragment) {
        try {
            EgyptianFieldValidator.validate(payload);
            fail("Expected ClientException for: " + expectedMessageFragment);
        } catch (ClientException e) {
            assertEquals("Wrong error code", ErrorCodes.ERR_INVALID_PAYLOAD, e.getErrCode());
            assertTrue("Message did not contain '" + expectedMessageFragment
                            + "': " + e.getMessage(),
                    e.getMessage().contains(expectedMessageFragment));
        }
    }
}
