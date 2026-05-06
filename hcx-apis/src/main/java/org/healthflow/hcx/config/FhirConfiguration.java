package org.healthflow.hcx.config;

import org.healthflow.common.fhir.FhirValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * FHIR validator bean wiring.
 *
 * <p>Per Decision 14, validation runs at the recipient HCX-API instance only.
 * The validator is feature-flagged via {@code fhir.validation.enabled} (default
 * {@code true}). The HealthFlow Egyptian Implementation Guide is loaded from the
 * path configured at {@code fhir.ig.packagePath}; if the file does not exist
 * (e.g. the IG hasn't been published yet on a dev machine), validation falls
 * back to base R4 only and a warning is logged — see {@link FhirValidationService}.
 *
 * <p>For convenience the IG package is supported at either a filesystem path or
 * a {@code classpath:} URL; classpath resources are copied to a temp file so
 * HAPI-FHIR's NPM loader can read them.
 */
@Configuration
public class FhirConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(FhirConfiguration.class);

    @Value("${fhir.validation.enabled:true}")
    private boolean enabled;

    @Value("${fhir.ig.packagePath:classpath:fhir/egyptian-ig.tgz}")
    private String igPackagePath;

    @Bean
    public FhirValidationService fhirValidationService() {
        Path resolved = resolvePackagePath(igPackagePath);
        logger.info("FhirValidationService bean: enabled={}, igPackagePath={}, resolved={}",
                enabled, igPackagePath, resolved);
        return new FhirValidationService(enabled, resolved);
    }

    private Path resolvePackagePath(String configValue) {
        if (configValue == null || configValue.isEmpty()) {
            return null;
        }
        if (configValue.startsWith("classpath:")) {
            try {
                Resource[] resources = new PathMatchingResourcePatternResolver()
                        .getResources(configValue);
                if (resources.length == 0 || !resources[0].exists()) {
                    return null;
                }
                Path tmp = Files.createTempFile("hfcx-fhir-ig-", ".tgz");
                try (var in = resources[0].getInputStream()) {
                    Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                }
                tmp.toFile().deleteOnExit();
                return tmp;
            } catch (IOException e) {
                logger.warn("FhirConfiguration: could not load IG package from {}: {}",
                        configValue, e.getMessage());
                return null;
            }
        }
        Path p = Paths.get(configValue);
        return Files.exists(p) ? p : null;
    }
}
