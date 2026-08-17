package com.epam.aihelpdesk.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 008-frontend-chat-ui research.md Decision 1: {@code /documents} and {@code /chat} are ordinary
 * {@code @RestController} endpoints dispatched through Spring MVC's normal
 * {@code DispatcherServlet}, which is a separate handler mapping from the actuator endpoint's own
 * ({@code management.endpoints.web.cors.*} in {@code application.yml}, asserted by
 * {@link com.epam.aihelpdesk.HealthEndpointCorsTest}) — so that property alone does not cover them.
 * This bean grants the frontend's origin the same kind of allowance for the resources this feature
 * actually calls, and nothing else: only {@code GET}/{@code POST}/{@code DELETE} (the methods
 * {@code /documents/**} and {@code /chat} actually expose) from exactly
 * {@code http://localhost:4200}, never a wildcard origin.
 */
@Configuration
public class WebCorsConfig implements WebMvcConfigurer {

    /** The frontend's fixed local origin (plan.md Technical Context — no deployment target yet). */
    static final String FRONTEND_ORIGIN = "http://localhost:4200";

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/documents/**")
                .allowedOrigins(FRONTEND_ORIGIN)
                .allowedMethods("GET", "POST", "DELETE");
        registry.addMapping("/chat")
                .allowedOrigins(FRONTEND_ORIGIN)
                .allowedMethods("POST");
    }
}
