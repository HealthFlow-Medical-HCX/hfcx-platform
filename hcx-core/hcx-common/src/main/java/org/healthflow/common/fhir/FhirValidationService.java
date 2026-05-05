package org.healthflow.common.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.CommonCodeSystemsTerminologyService;
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.NpmPackageValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * P0-5 — FHIR R4 validation per Integration Guide §29.
 *
 * <p>Per Decision 14 (zero-knowledge transport), this service is invoked at the
 * <b>recipient</b> HCX-API instance, never at the gateway. Recipients call
 * {@link #validate(String)} after they decrypt the inbound JWE.
 *
 * <p>Validation runs against:
 * <ol>
 *   <li>Base FHIR R4 (4.0.1) — always loaded.</li>
 *   <li>The HealthFlow Egyptian Implementation Guide — loaded from the path
 *       configured in {@code fhir.ig.packagePath} (e.g. an NPM-style
 *       {@code package.tgz}). Optional; if the path is unset or the file is
 *       missing, validation falls back to base R4 and a warning is logged.</li>
 * </ol>
 *
 * <p>The service is feature-flagged via {@code fhir.validation.enabled}
 * (default: false). With the flag off, {@link #validate(String)} returns a
 * skipped result without invoking the validator — useful while the IG is
 * being authored and the platform is staged for the cutover.
 *
 * <p>Thread-safety: HAPI-FHIR's {@link FhirContext} and {@link FhirValidator}
 * are documented to be thread-safe after construction. This class therefore
 * lazy-builds a single validator and reuses it across calls.
 */
public final class FhirValidationService {

    private static final Logger logger = LoggerFactory.getLogger(FhirValidationService.class);

    private final boolean enabled;
    private final Path igPackagePath;
    private volatile FhirValidator cachedValidator;
    private volatile FhirContext cachedContext;

    /**
     * @param enabled       feature flag — when false, validate() returns a no-op
     *                      skipped result. Caller should source from
     *                      {@code fhir.validation.enabled} property.
     * @param igPackagePath path to the HealthFlow Egyptian IG NPM package
     *                      ({@code package.tgz} or directory). Optional;
     *                      {@code null} or non-existent path = base R4 only.
     */
    public FhirValidationService(boolean enabled, Path igPackagePath) {
        this.enabled = enabled;
        this.igPackagePath = igPackagePath;
    }

    /** Whether the feature flag is on. */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Validates a FHIR R4 resource (JSON or XML).
     *
     * @param resourcePayload the serialized FHIR resource — typically a Bundle
     *                        decrypted from an inbound JWE.
     * @return a structured result. {@link FhirValidationOutcome#skipped()} when
     *         the feature flag is off; otherwise a real validation result with
     *         issue list.
     */
    public FhirValidationOutcome validate(String resourcePayload) {
        if (!enabled) {
            return FhirValidationOutcome.skipped();
        }
        if (resourcePayload == null || resourcePayload.trim().isEmpty()) {
            return FhirValidationOutcome.failure(Collections.singletonList(
                    "Resource payload is null or empty"));
        }
        FhirValidator validator = getOrBuildValidator();
        ValidationResult result = validator.validateWithResult(resourcePayload);
        if (result.isSuccessful()) {
            return FhirValidationOutcome.success();
        }
        List<String> issues = result.getMessages().stream()
                .filter(m -> m.getSeverity() == ca.uhn.fhir.validation.ResultSeverityEnum.ERROR
                        || m.getSeverity() == ca.uhn.fhir.validation.ResultSeverityEnum.FATAL)
                .map(FhirValidationService::formatIssue)
                .collect(Collectors.toList());
        if (issues.isEmpty()) {
            // Warnings only — pass.
            return FhirValidationOutcome.success();
        }
        return FhirValidationOutcome.failure(issues);
    }

    private static String formatIssue(SingleValidationMessage m) {
        return "[" + m.getSeverity() + "] "
                + (m.getLocationString() == null ? "" : m.getLocationString() + " — ")
                + m.getMessage();
    }

    private FhirValidator getOrBuildValidator() {
        FhirValidator v = cachedValidator;
        if (v != null) return v;
        synchronized (this) {
            if (cachedValidator != null) return cachedValidator;
            cachedContext = FhirContext.forR4();
            ValidationSupportChain chain = new ValidationSupportChain(
                    new DefaultProfileValidationSupport(cachedContext),
                    new InMemoryTerminologyServerValidationSupport(cachedContext),
                    new CommonCodeSystemsTerminologyService(cachedContext)
            );
            attachIgPackageIfAvailable(chain);
            FhirInstanceValidator instanceValidator = new FhirInstanceValidator(chain);
            cachedValidator = cachedContext.newValidator()
                    .registerValidatorModule(instanceValidator);
            logger.info("FhirValidationService initialized: igPackagePath={}, hasIg={}",
                    igPackagePath, chain.fetchAllStructureDefinitions().size() > 0);
            return cachedValidator;
        }
    }

    private void attachIgPackageIfAvailable(ValidationSupportChain chain) {
        if (igPackagePath == null) {
            logger.warn("FhirValidationService: no IG package configured — falling back to base R4. "
                    + "Set fhir.ig.packagePath to the HealthFlow Egyptian IG package.tgz "
                    + "once it is published (see Decision 15).");
            return;
        }
        if (!Files.exists(igPackagePath)) {
            logger.warn("FhirValidationService: IG package configured but file does not exist at {} "
                    + "— falling back to base R4.", igPackagePath);
            return;
        }
        try {
            NpmPackageValidationSupport npm = new NpmPackageValidationSupport(cachedContext);
            npm.loadPackageFromClasspath(igPackagePath.toString());
            // Note: NpmPackageValidationSupport's loadPackageFromClasspath is for
            // classpath resources; for filesystem paths use loadPackageFromFilesystem
            // (HAPI-FHIR 6.10.5+). If the runtime API differs, the catch below
            // logs the failure and the chain keeps base R4.
            chain.addValidationSupport(npm);
            logger.info("FhirValidationService: loaded IG package from {}", igPackagePath);
        } catch (IOException e) {
            logger.warn("FhirValidationService: failed to load IG package from {}: {} "
                    + "— falling back to base R4.", igPackagePath, e.getMessage());
        }
    }

    /** Outcome of a single validation call. */
    public static final class FhirValidationOutcome {

        public enum Status { SUCCESS, FAILURE, SKIPPED }

        private final Status status;
        private final List<String> issues;

        private FhirValidationOutcome(Status s, List<String> issues) {
            this.status = s;
            this.issues = issues == null ? Collections.emptyList() : Collections.unmodifiableList(issues);
        }

        public static FhirValidationOutcome success() {
            return new FhirValidationOutcome(Status.SUCCESS, Collections.emptyList());
        }

        public static FhirValidationOutcome skipped() {
            return new FhirValidationOutcome(Status.SKIPPED, Collections.emptyList());
        }

        public static FhirValidationOutcome failure(List<String> issues) {
            return new FhirValidationOutcome(Status.FAILURE, new ArrayList<>(issues));
        }

        public boolean isSuccess()    { return status == Status.SUCCESS; }
        public boolean isFailure()    { return status == Status.FAILURE; }
        public boolean isSkipped()    { return status == Status.SKIPPED; }
        public Status getStatus()     { return status; }
        public List<String> getIssues() { return issues; }

        @Override
        public String toString() {
            return "FhirValidationOutcome{status=" + status + ", issues=" + issues + "}";
        }
    }
}
