package org.healthflow.hcx.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Security Headers and CORS Configuration for HCX Egypt
 * Implements OWASP security best practices
 */
@Configuration
@EnableWebSecurity
public class SecurityHeadersConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Security headers
            .headers(headers -> headers
                // Prevent clickjacking
                .frameOptions().deny()
                
                // XSS protection
                .xssProtection().block(true)
                
                // Content type sniffing protection
                .contentTypeOptions().disable()
                
                // HSTS (HTTP Strict Transport Security)
                .httpStrictTransportSecurity()
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000)  // 1 year
                
                // Content Security Policy
                .contentSecurityPolicy(
                    "default-src 'self'; " +
                    "script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
                    "style-src 'self' 'unsafe-inline'; " +
                    "img-src 'self' data: https:; " +
                    "font-src 'self' data:; " +
                    "connect-src 'self' https://api.healthflow.eg; " +
                    "frame-ancestors 'none'; " +
                    "base-uri 'self'; " +
                    "form-action 'self'"
                )
                
                // Referrer Policy
                .referrerPolicy()
                    .policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                
                // Permissions Policy (formerly Feature Policy)
                .permissionsPolicy(policy -> policy
                    .policy("geolocation=(), microphone=(), camera=()")
                )
            )
            
            // CORS configuration
            .cors().configurationSource(corsConfigurationSource())
            
            // CSRF protection
            .csrf()
                .ignoringAntMatchers("/api/v1/webhook/**")  // Webhooks don't need CSRF
            
            // Authorization rules
            .and()
            .authorizeRequests()
                .antMatchers("/actuator/health", "/actuator/info").permitAll()
                .antMatchers("/api/v1/public/**").permitAll()
                .antMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated();
                
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Allowed origins (Egypt-specific domains)
        configuration.setAllowedOrigins(Arrays.asList(
            "https://hcx.healthflow.eg",
            "https://portal.healthflow.eg",
            "https://admin.healthflow.eg",
            "https://dev-hcx.healthflow.eg"  // Development environment
        ));
        
        // Allowed methods
        configuration.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "OPTIONS"
        ));
        
        // Allowed headers
        configuration.setAllowedHeaders(Arrays.asList(
            "Authorization",
            "Content-Type",
            "X-Requested-With",
            "X-HCX-Sender-Code",
            "X-HCX-Recipient-Code",
            "X-HCX-Timestamp",
            "X-HCX-Correlation-Id"
        ));
        
        // Exposed headers
        configuration.setExposedHeaders(Arrays.asList(
            "X-RateLimit-Remaining",
            "X-RateLimit-Reset",
            "X-HCX-Correlation-Id",
            "X-HCX-Status"
        ));
        
        // Allow credentials
        configuration.setAllowCredentials(true);
        
        // Max age for preflight requests (1 hour)
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }
}
