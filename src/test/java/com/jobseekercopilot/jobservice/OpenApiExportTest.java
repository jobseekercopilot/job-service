package com.jobseekercopilot.jobservice;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "job-service.database.production-safety-check=false",
        "spring.datasource.url=jdbc:h2:mem:openapi;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "job-service.security.jwk-set-uri=http://localhost:65534/jwks",
        "job-service.security.issuer=test-issuer",
        "job-service.security.audience=test-audience"
})
@AutoConfigureMockMvc
class OpenApiExportTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exportsReviewedProducerContract() throws Exception {
        String contract = mockMvc.perform(get("/v3/api-docs.yaml")
                        .with(user("contract-owner")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(contract.contains("/api/jobs/saved"));
        assertTrue(contract.contains("SavedJobResponse"));
        assertTrue(contract.contains("version: 2.4.0"));
        assertTrue(contract.contains("ApprenticeshipDetails:"));
        assertTrue(contract.contains("APPRENTICESHIP"));
        assertTrue(contract.contains("OFFICIAL_PROVIDER"));
        assertTrue(contract.contains("pageSize:"));
        assertTrue(contract.contains("totalPages:"));
        assertTrue(contract.contains("TargetRoleJobResults:"));
        assertTrue(contract.contains("UNAVAILABLE"));
        assertTrue(contract.contains("JOB_TITLE_AZ"));
        assertTrue(contract.contains("candidateProfile:"));
        assertTrue(contract.contains("MatchAssessment:"));
        assertTrue(contract.contains("hardGateReasons:"));
        assertTrue(contract.contains("ProviderDataProvenance:"));
        assertTrue(contract.contains("SearchFreshness:"));
        assertTrue(contract.contains("SearchQualitySummary:"));
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/openapi.yaml"), contract);
    }
}
