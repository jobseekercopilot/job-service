package com.jobseekercopilot.jobservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jobseekercopilot.jobservice.model.dto.Job;
import com.jobseekercopilot.jobservice.model.dto.JobSourceReference;
import com.jobseekercopilot.jobservice.model.dto.SavedJobPageResponse;
import com.jobseekercopilot.jobservice.service.SavedJobSaveOutcome;
import com.jobseekercopilot.jobservice.service.SavedJobSaveResult;
import com.jobseekercopilot.jobservice.service.SavedJobService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@EnabledIfEnvironmentVariable(
        named = "JOB_SERVICE_POSTGRES_TEST_MODE",
        matches = "seed|verify")
@SpringBootTest(properties = {
        "job-service.database.production-safety-check=false",
        "spring.datasource.url=${JOB_SERVICE_POSTGRES_TEST_URL}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.datasource.username=${JOB_SERVICE_POSTGRES_TEST_USERNAME}",
        "spring.datasource.password=${JOB_SERVICE_POSTGRES_TEST_PASSWORD}",
        "job-service.security.jwk-set-uri=http://localhost:65534/jwks",
        "job-service.security.issuer=test-issuer",
        "job-service.security.audience=test-audience"
})
class SavedJobPostgresqlIntegrationTest {

    private static final String OWNER = "search-11-postgresql-owner";
    private static final String CANONICAL_ID = "search-11-postgresql-job";

    @Autowired
    private SavedJobService service;

    @Test
    void provesMigrationPersistenceAndRestoredSnapshot() {
        String mode = System.getenv("JOB_SERVICE_POSTGRES_TEST_MODE");
        if ("seed".equals(mode)) {
            SavedJobSaveResult created = service.save(OWNER, canonicalJob());
            SavedJobSaveResult replayed = service.save(OWNER, canonicalJob());

            assertEquals(SavedJobSaveOutcome.CREATED, created.outcome());
            assertEquals(SavedJobSaveOutcome.REPLAYED, replayed.outcome());
            assertEquals(
                    created.savedJob().savedJobId(),
                    replayed.savedJob().savedJobId());
        }

        SavedJobPageResponse savedJobs = service.list(OWNER, 0, 20);
        assertEquals(1, savedJobs.totalElements());
        assertEquals(CANONICAL_ID, savedJobs.items().get(0).canonicalJobId());
        assertEquals(1, savedJobs.items().get(0).snapshotVersion());
        assertTrue(savedJobs.items().get(0).contentVersion().startsWith("sha256:"));
        assertEquals(
                "PostgreSQL restart and restore evidence",
                savedJobs.items().get(0).job().getDescription());
    }

    private Job canonicalJob() {
        Job job = new Job();
        job.setCanonicalJobId(CANONICAL_ID);
        job.setId(CANONICAL_ID);
        job.setTitle("Platform Engineer");
        job.setCompanyName("Example Ltd");
        job.setDescription("PostgreSQL restart and restore evidence");
        job.setPrimarySource("REED");
        job.setExternalJobId("reed-search-11");

        JobSourceReference source = new JobSourceReference();
        source.setIntegrationProvider("REED");
        source.setExternalJobId("reed-search-11");
        source.setListingUrl("https://jobs.example.test/search-11");
        source.setRetrievedAtUtc(
                OffsetDateTime.parse("2026-07-26T12:00:00Z"));
        job.setSources(List.of(source));
        return job;
    }
}
