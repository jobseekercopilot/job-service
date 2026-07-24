package com.jobseekercopilot.jobservice;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class JobServiceContractSecurityTest {

    @Test
    void publishedContractRequiresBearerAndCannotReintroduceRawIdentityHeader() throws IOException {
        String contract = Files.readString(Path.of("api/openapi.yaml"));

        assertTrue(contract.contains("bearerAuth:"));
        assertTrue(contract.contains("scheme: bearer"));
        assertTrue(contract.contains("\"401\":"));
        assertFalse(contract.contains("X-User-Id"));
    }

    @Test
    void applicationLoggingPolicyCannotEmitSearchIdentityOrRequestDetails() throws IOException {
        String searchService = Files.readString(Path.of(
                "src/main/java/com/jobseekercopilot/jobservice/service/JobSearchService.java"));
        String matchingClient = Files.readString(Path.of(
                "src/main/java/com/jobseekercopilot/jobservice/service/JobMatchingClient.java"));
        String configuration = Files.readString(Path.of("src/main/resources/application.yml"));

        assertFalse(searchService.contains("userId={}"));
        assertFalse(searchService.contains("targetRole={}"));
        assertFalse(searchService.contains("location={}"));
        assertFalse(matchingClient.contains("userId={}"));
        assertTrue(configuration.contains("org.springframework.web: INFO"));
        assertTrue(configuration.contains("org.springframework.security: INFO"));
    }
}
