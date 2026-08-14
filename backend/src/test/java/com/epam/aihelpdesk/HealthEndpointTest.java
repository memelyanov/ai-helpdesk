package com.epam.aihelpdesk;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import javax.sql.DataSource;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The three cases of contracts/health-api.md, exercised through MockMvc against the mock servlet
 * context (webEnvironment MOCK — no real socket bound, unlike RANDOM_PORT). Per research
 * Decision 11, the default suite contacts no real database: the "reachable" case mocks the
 * {@code DataSource} bean the real, unmodified {@code DataSourceHealthIndicator} queries, and the
 * "unreachable" case points the real datasource at a guaranteed-refused local port — both leave
 * the built-in "db" health contributor itself untouched (FR-006, FR-007, FR-020).
 */
class HealthEndpointTest {

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    class CaseADatabaseReachable {

        @MockitoBean
        DataSource dataSource;

        @Autowired
        MockMvc mockMvc;

        @Test
        void reportsOverallUpWithDbUp() throws Exception {
            Connection connection = mock(Connection.class);
            DatabaseMetaData metaData = mock(DatabaseMetaData.class);
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.getMetaData()).thenReturn(metaData);
            when(metaData.getURL()).thenReturn("jdbc:postgresql://localhost:5432/aihelpdesk");
            when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");
            when(connection.isValid(anyInt())).thenReturn(true);

            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("\"status\":\"UP\"")))
                    .andExpect(content().string(containsString("\"db\":{\"status\":\"UP\"")))
                    .andExpect(content().string(containsString("\"database\":\"PostgreSQL\"")));
        }
    }

    @Nested
    @SpringBootTest(properties = "spring.datasource.url=jdbc:postgresql://localhost:1/refused")
    @AutoConfigureMockMvc
    class CaseBDatabaseUnreachable {

        @Autowired
        MockMvc mockMvc;

        @Test
        void reportsOverallDownWithConnectionErrorNamed() throws Exception {
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(content().string(containsString("\"status\":\"DOWN\"")))
                    .andExpect(content().string(containsString("\"db\":{\"status\":\"DOWN\"")))
                    .andExpect(content().string(containsString("CannotGetJdbcConnectionException")));
        }
    }

    @Nested
    @SpringBootTest(properties = {
            "spring.datasource.url=jdbc:postgresql://localhost:5432/aihelpdesk",
            "spring.ai.azure.openai.api-key=",
            "spring.ai.azure.openai.endpoint=",
            "spring.ai.azure.openai.chat.options.deployment-name=",
            "spring.ai.azure.openai.embedding.options.deployment-name="
    })
    @AutoConfigureMockMvc
    class CaseCAzureUnconfiguredDatabaseReachable {

        @MockitoBean
        DataSource dataSource;

        @Autowired
        MockMvc mockMvc;

        @Test
        void reportsOverallUpWithAzureUnknown() throws Exception {
            Connection connection = mock(Connection.class);
            DatabaseMetaData metaData = mock(DatabaseMetaData.class);
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.getMetaData()).thenReturn(metaData);
            when(metaData.getURL()).thenReturn("jdbc:postgresql://localhost:5432/aihelpdesk");
            when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");
            when(connection.isValid(anyInt())).thenReturn(true);

            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("\"status\":\"UP\"")))
                    .andExpect(content().string(containsString("\"azureOpenAi\":{\"status\":\"UNKNOWN\"")));
        }
    }
}
