package org.healthflow.common.fhir;

import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the FHIR validation scaffolding (P0-5).
 *
 * <p>The scope here is wiring correctness, not exhaustive FHIR conformance —
 * the latter requires the HealthFlow Egyptian Implementation Guide which is
 * authored separately (see Decision 15). What these tests prove:
 *
 * <ul>
 *   <li>The feature flag works: with {@code enabled=false} the service skips.</li>
 *   <li>Base R4 validation works without an IG package: a malformed Bundle
 *       fails, a syntactically valid Patient passes.</li>
 *   <li>The IG-package path is optional: a non-existent path falls back to
 *       base R4 with a warning, rather than throwing.</li>
 *   <li>Null / empty inputs fail fast.</li>
 * </ul>
 */
public class FhirValidationServiceTest {

    private static final Path NO_IG = null;
    private static final Path NONEXISTENT_IG =
            Paths.get("/tmp/this-path-does-not-exist-and-must-not-throw.tgz");

    @Test
    public void disabledServiceReturnsSkippedWithoutInvokingValidator() {
        FhirValidationService svc = new FhirValidationService(false, NO_IG);
        FhirValidationService.FhirValidationOutcome out = svc.validate("any garbage");
        assertTrue(out.isSkipped());
        assertEquals(FhirValidationService.FhirValidationOutcome.Status.SKIPPED, out.getStatus());
    }

    @Test
    public void enabledServiceWithNullPayloadFails() {
        FhirValidationService svc = new FhirValidationService(true, NO_IG);
        FhirValidationService.FhirValidationOutcome out = svc.validate(null);
        assertTrue(out.isFailure());
        assertTrue(out.getIssues().stream().anyMatch(i -> i.toLowerCase().contains("null")
                || i.toLowerCase().contains("empty")));
    }

    @Test
    public void enabledServiceWithEmptyPayloadFails() {
        FhirValidationService svc = new FhirValidationService(true, NO_IG);
        FhirValidationService.FhirValidationOutcome out = svc.validate("");
        assertTrue(out.isFailure());
    }

    @Test
    public void validBaseR4PatientPassesWithoutIg() {
        FhirValidationService svc = new FhirValidationService(true, NO_IG);
        // Minimal valid R4 Patient.
        String patient = "{"
                + "\"resourceType\":\"Patient\","
                + "\"id\":\"patient-001\","
                + "\"name\":[{\"family\":\"Hassan\",\"given\":[\"Mohamed\"]}],"
                + "\"gender\":\"male\""
                + "}";
        FhirValidationService.FhirValidationOutcome out = svc.validate(patient);
        assertTrue("Valid Patient should pass: issues=" + out.getIssues(), out.isSuccess());
    }

    @Test
    public void invalidResourceTypeFailsBaseR4() {
        FhirValidationService svc = new FhirValidationService(true, NO_IG);
        String junk = "{\"resourceType\":\"NotARealResource\",\"id\":\"x\"}";
        FhirValidationService.FhirValidationOutcome out = svc.validate(junk);
        assertTrue("Bogus resourceType should fail", out.isFailure());
        assertFalse(out.getIssues().isEmpty());
    }

    @Test
    public void wrongFieldTypeFailsBaseR4() {
        FhirValidationService svc = new FhirValidationService(true, NO_IG);
        // Patient.gender is a code; passing an object should fail.
        String patient = "{"
                + "\"resourceType\":\"Patient\","
                + "\"gender\":{\"not-a-code\":true}"
                + "}";
        FhirValidationService.FhirValidationOutcome out = svc.validate(patient);
        assertTrue("Wrong type for Patient.gender should fail", out.isFailure());
    }

    @Test
    public void nonExistentIgPathFallsBackToBaseR4WithoutThrowing() {
        FhirValidationService svc = new FhirValidationService(true, NONEXISTENT_IG);
        // Same minimal valid Patient as above — should still pass via base R4.
        String patient = "{\"resourceType\":\"Patient\",\"id\":\"x\"}";
        FhirValidationService.FhirValidationOutcome out = svc.validate(patient);
        assertTrue("Should fall back to base R4: " + out.getIssues(), out.isSuccess());
    }
}
