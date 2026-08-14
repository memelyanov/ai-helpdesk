package com.epam.aihelpdesk;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Constitution v1.3.0 requires the API key never appear in a response, in whole or in part
 * (FR-009, data-model.md handling rules). Binds a known sentinel value and asserts it is absent
 * from the health payload. Uses MockMvc (webEnvironment MOCK) — no real socket bound.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:1/refused",
        "spring.ai.azure.openai.api-key=sentinel-secret-value-should-never-leak-42",
        "spring.ai.azure.openai.endpoint=https://example.openai.azure.com",
        "spring.ai.azure.openai.chat.options.deployment-name=chat-dep"
})
@AutoConfigureMockMvc
class HealthResponseSecretTest {

    private static final String SENTINEL = "sentinel-secret-value-should-never-leak-42";

    @Autowired
    MockMvc mockMvc;

    @Test
    void apiKeyNeverAppearsInHealthResponse() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(content().string(not(containsString(SENTINEL))));
    }
}
