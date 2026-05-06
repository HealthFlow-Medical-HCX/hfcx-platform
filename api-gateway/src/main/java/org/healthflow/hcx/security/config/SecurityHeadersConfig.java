package org.healthflow.hcx.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * Security headers + CORS configuration for the HCX Egypt gateway.
 *
 * <p>Originally written against Spring Security's servlet stack
 * ({@code HttpSecurity} / {@code SecurityFilterChain}); the gateway is
 * reactive (Spring Cloud Gateway / WebFlux) so the servlet APIs do not
 * exist on the classpath. This class is rewritten to expose only a
 * reactive {@link CorsConfigurationSource} bean, which Spring Cloud
 * Gateway picks up automatically when CORS is enabled.
 *
 * <p>The original class also tried to set HTTP security headers
 * (X-Frame-Options, HSTS, CSP, etc.). On the reactive stack those are
 * configured via {@code spring.cloud.gateway.default-filters} or via
 * a custom {@code GlobalFilter} that sets headers on the response.
 * That work is tracked separately; for now this class no longer fails
 * to compile and CORS is correctly wired.
 */
@Configuration
public class SecurityHeadersConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Egypt-specific allowed origins.
        configuration.setAllowedOrigins(Arrays.asList(
                "https://hcx.healthflow.eg",
                "https://portal.healthflow.eg",
                "https://admin.healthflow.eg",
                "https://dev-hcx.healthflow.eg"
        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "X-Requested-With",
                "X-HCX-Sender-Code",
                "X-HCX-Recipient-Code",
                "X-HCX-Timestamp",
                "X-HCX-Correlation-Id"
        ));
        configuration.setExposedHeaders(Arrays.asList(
                "X-RateLimit-Remaining",
                "X-RateLimit-Reset",
                "X-HCX-Correlation-Id",
                "X-HCX-Status"
        ));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
