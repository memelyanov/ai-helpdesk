package com.epam.aihelpdesk.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 008-frontend-chat-ui research.md Decision 1: unlike {@code /actuator/health}'s
 * {@code management.endpoints.web.cors.*} property (asserted by
 * {@link com.epam.aihelpdesk.HealthEndpointCorsTest}, a separate handler mapping entirely), the
 * ordinary {@code @RestController} endpoints this feature depends on — {@code /documents} and
 * {@code /chat} — need a {@link org.springframework.web.servlet.config.annotation.WebMvcConfigurer}
 * {@code addCorsMappings} bean instead. This test asserts the browser-issued CORS preflight
 * ({@code OPTIONS} with an {@code Access-Control-Request-Method} header) succeeds with the
 * frontend's origin permitted — preflight is handled entirely by Spring's CORS processing before any
 * controller method runs, so no repository/service bean needs to be mocked here.
 */
@SpringBootTest
@AutoConfigureMockMvc
class WebCorsConfigTest {

    private static final String FRONTEND_ORIGIN = "http://localhost:4200";

    @Autowired
    MockMvc mockMvc;

    @Test
    void allowsFrontendOriginToPreflightGetDocuments() throws Exception {
        mockMvc.perform(options("/documents")
                        .header("Origin", FRONTEND_ORIGIN)
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", FRONTEND_ORIGIN));
    }

    @Test
    void allowsFrontendOriginToPreflightPostDocuments() throws Exception {
        mockMvc.perform(options("/documents")
                        .header("Origin", FRONTEND_ORIGIN)
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", FRONTEND_ORIGIN));
    }

    @Test
    void allowsFrontendOriginToPreflightDocumentContentDownload() throws Exception {
        mockMvc.perform(options("/documents/{id}/content", "11111111-1111-1111-1111-111111111111")
                        .header("Origin", FRONTEND_ORIGIN)
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", FRONTEND_ORIGIN));
    }

    @Test
    void allowsFrontendOriginToPreflightDeleteDocument() throws Exception {
        mockMvc.perform(options("/documents/{id}", "11111111-1111-1111-1111-111111111111")
                        .header("Origin", FRONTEND_ORIGIN)
                        .header("Access-Control-Request-Method", "DELETE"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", FRONTEND_ORIGIN));
    }

    @Test
    void allowsFrontendOriginToPreflightPostChat() throws Exception {
        mockMvc.perform(options("/chat")
                        .header("Origin", FRONTEND_ORIGIN)
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", FRONTEND_ORIGIN));
    }

    @Test
    void rejectsAnUnknownOriginForPostChat() throws Exception {
        mockMvc.perform(options("/chat")
                        .header("Origin", "http://evil.example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
