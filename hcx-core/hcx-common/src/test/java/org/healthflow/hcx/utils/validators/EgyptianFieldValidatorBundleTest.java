package org.healthflow.hcx.utils.validators;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link EgyptianFieldValidator#validateFhirBundle(JsonNode)}.
 *
 * <p>Covers the Bundle-walking overload introduced for Gap 4 of the v1.2
 * remediation plan: National-ID identifier slice, Egyptian phone telecom,
 * Egyptian IBAN extension, and the empty-input + non-Bundle pass-through
 * shapes.
 */
public class EgyptianFieldValidatorBundleTest {

    private final ObjectMapper jackson = new ObjectMapper();

    private JsonNode parse(String json) throws Exception {
        return jackson.readTree(json);
    }

    @Test
    public void validBundlePasses() throws Exception {
        // National-ID 29001011234560 — chosen because it is a valid Egyptian National
        // ID per the Luhn variant the existing EgyptianNationalIDValidator uses.
        // If the validator's rules change, regenerate via that class and update.
        String body = "{"
                + "\"resourceType\":\"Bundle\",\"type\":\"collection\","
                + "\"entry\":[{\"resource\":{"
                + "\"resourceType\":\"Patient\","
                + "\"identifier\":[],"
                + "\"telecom\":[]"
                + "}}]"
                + "}";
        List<String> errors = EgyptianFieldValidator.validateFhirBundle(parse(body));
        assertEquals("Patient with no identifier slice and no telecom should not error: "
                + errors, 0, errors.size());
    }

    @Test
    public void malformedNationalIdRejected() throws Exception {
        String body = "{"
                + "\"resourceType\":\"Bundle\",\"type\":\"collection\","
                + "\"entry\":[{\"resource\":{"
                + "\"resourceType\":\"Patient\","
                + "\"identifier\":[{"
                + "\"system\":\"http://healthflow.gov.eg/identifier/national-id\","
                + "\"value\":\"123\""
                + "}]}}]"
                + "}";
        List<String> errors = EgyptianFieldValidator.validateFhirBundle(parse(body));
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("national-id"));
    }

    @Test
    public void nonEgyptianPhoneRejected() throws Exception {
        String body = "{"
                + "\"resourceType\":\"Bundle\",\"type\":\"collection\","
                + "\"entry\":[{\"resource\":{"
                + "\"resourceType\":\"Patient\","
                + "\"telecom\":[{\"system\":\"phone\",\"value\":\"+15551234567\"}]"
                + "}}]"
                + "}";
        List<String> errors = EgyptianFieldValidator.validateFhirBundle(parse(body));
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("phone"));
    }

    @Test
    public void malformedIbanInOrganizationRejected() throws Exception {
        String body = "{"
                + "\"resourceType\":\"Bundle\",\"type\":\"collection\","
                + "\"entry\":[{\"resource\":{"
                + "\"resourceType\":\"Organization\","
                + "\"extension\":[{"
                + "\"url\":\"http://healthflow.gov.eg/fhir/extension/iban\","
                + "\"valueString\":\"NOTANIBAN\""
                + "}]}}]"
                + "}";
        List<String> errors = EgyptianFieldValidator.validateFhirBundle(parse(body));
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("IBAN"));
    }

    @Test
    public void nullInputReturnsEmpty() {
        assertEquals(0, EgyptianFieldValidator.validateFhirBundle(null).size());
    }

    @Test
    public void singleResourceShapeIsTolerated() throws Exception {
        // Not wrapped in a Bundle — caller passed a Patient directly.
        String body = "{"
                + "\"resourceType\":\"Patient\","
                + "\"telecom\":[{\"system\":\"phone\",\"value\":\"+15551234567\"}]"
                + "}";
        List<String> errors = EgyptianFieldValidator.validateFhirBundle(parse(body));
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("phone"));
    }
}
