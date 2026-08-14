package com.epam.aihelpdesk;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * FR-008 / contracts/frontend-health-consumption.md: the frontend's origin must be permitted to
 * call the health endpoint from the browser. Asserts the CORS response header Actuator's own
 * {@code WebMvcEndpointHandlerMapping} adds when {@code management.endpoints.web.cors.*} is
 * configured — a {@code WebMvcConfigurer} {@code addCorsMappings} bean would NOT affect this
 * separate handler mapping (research.md Decision 3), so this test only passes once the
 * actuator-specific property is set. The response status is deliberately not asserted here: CORS
 * headers apply regardless of whether the health check itself reports UP or DOWN.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HealthEndpointCorsTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void allowsFrontendOriginOnHealthEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/health").header("Origin", "http://localhost:4200"))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
    }
}
