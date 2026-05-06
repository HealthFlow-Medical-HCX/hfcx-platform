package org.healthflow.mock.payer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Mock payer participant for §31 cycle integration tests.
 * <p>
 * Mirrors mock-provider but represents the recipient side of forward calls
 * (coverageeligibility/check, preauth/submit, claim/submit, paymentnotice/request)
 * and the originator of the on_* callbacks.
 */
@SpringBootApplication
public class MockPayerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MockPayerApplication.class, args);
    }
}
