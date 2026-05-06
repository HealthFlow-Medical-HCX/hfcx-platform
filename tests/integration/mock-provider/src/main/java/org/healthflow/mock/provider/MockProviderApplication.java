package org.healthflow.mock.provider;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Mock provider participant for §31 cycle integration tests.
 * <p>
 * Behaviour intentionally minimal: accept any well-formed POST body, log it,
 * and respond 202 with a synthetic correlation/api-call envelope. JWE
 * decrypt/encrypt is stubbed (Decision 14 keeps the gateway transparent, so
 * a real provider would do its own crypto — see TODO(gap-17-followup) in
 * {@link org.healthflow.mock.provider.controller.ProviderController}).
 */
@SpringBootApplication
public class MockProviderApplication {

    public static void main(String[] args) {
        SpringApplication.run(MockProviderApplication.class, args);
    }
}
