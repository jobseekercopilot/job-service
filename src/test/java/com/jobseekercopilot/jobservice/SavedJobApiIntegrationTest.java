package com.jobseekercopilot.jobservice;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "job-service.database.production-safety-check=false",
        "spring.datasource.url=jdbc:h2:mem:saved_job_api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "job-service.security.jwk-set-uri=http://localhost:65534/jwks",
        "job-service.security.issuer=test-issuer",
        "job-service.security.audience=test-audience"
})
@AutoConfigureMockMvc
class SavedJobApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void authenticatedCrudIsOwnerScopedIdempotentAndVersioned() throws Exception {
        String canonicalJobId = "canonical-" + UUID.randomUUID();
        String body = jobJson(canonicalJobId, "Original description");

        MvcResult created = mockMvc.perform(post("/api/jobs/saved")
                        .with(jwt().jwt(token -> token.subject("alice")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Saved-Job-Outcome", "CREATED"))
                .andExpect(jsonPath("$.canonicalJobId").value(canonicalJobId))
                .andExpect(jsonPath("$.snapshotVersion").value(1))
                .andExpect(jsonPath("$.contentSha256").isString())
                .andExpect(jsonPath("$.job.applicationStatus").doesNotExist())
                .andReturn();
        String savedJobId = com.jayway.jsonpath.JsonPath.read(
                created.getResponse().getContentAsString(),
                "$.savedJobId");

        mockMvc.perform(post("/api/jobs/saved")
                        .with(jwt().jwt(token -> token.subject("alice")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Saved-Job-Outcome", "REPLAYED"))
                .andExpect(jsonPath("$.savedJobId").value(savedJobId))
                .andExpect(jsonPath("$.snapshotVersion").value(1));

        mockMvc.perform(get("/api/jobs/saved/{savedJobId}", savedJobId)
                        .with(jwt().jwt(token -> token.subject("bob"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("SAVED_JOB_NOT_FOUND"));

        mockMvc.perform(get("/api/jobs/saved")
                        .with(jwt().jwt(token -> token.subject("alice"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].savedJobId").value(savedJobId));

        mockMvc.perform(delete("/api/jobs/saved/{savedJobId}", savedJobId)
                        .with(jwt().jwt(token -> token.subject("alice"))))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/jobs/saved/{savedJobId}", savedJobId)
                        .with(jwt().jwt(token -> token.subject("alice"))))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/jobs/saved/{savedJobId}", savedJobId)
                        .with(jwt().jwt(token -> token.subject("alice"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingAuthenticationAndMalformedSnapshotsFailClosed() throws Exception {
        mockMvc.perform(get("/api/jobs/saved"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/jobs/saved")
                        .with(jwt().jwt(token -> token.subject("alice")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "canonicalSchemaVersion": "2.0",
                                  "canonicalJobId": "job-without-content"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_SAVED_JOB"));
    }

    private String jobJson(String canonicalJobId, String description) {
        return """
                {
                  "canonicalSchemaVersion": "2.0",
                  "canonicalJobId": "%s",
                  "id": "%s",
                  "title": "Platform Engineer",
                  "companyName": "Example Ltd",
                  "description": "%s",
                  "primarySource": "REED",
                  "externalJobId": "reed-123",
                  "applicationStatus": "APPLIED",
                  "sources": [
                    {
                      "integrationProvider": "REED",
                      "externalJobId": "reed-123",
                      "listingUrl": "https://jobs.example.test/123",
                      "retrievedAtUtc": "2026-07-26T12:00:00Z"
                    }
                  ]
                }
                """.formatted(canonicalJobId, canonicalJobId, description);
    }
}
