package com.epam.aihelpdesk;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The application context MUST load with no database reachable and every AZURE_OPEN_AI_* variable
 * unset (FR-007, FR-019, SC-009). Fails until application.yml defaults spring.ai.model.chat and
 * spring.ai.model.embedding to "none" — without that gate, Spring AI's Azure auto-configuration
 * tries to build a client with no endpoint and throws IllegalArgumentException during context
 * refresh.
 */
@SpringBootTest
class AiHelpdeskApplicationTests {

    @Test
    void contextLoads() {
        // No assertions needed: a failing context refresh fails this test on its own.
    }
}
