package com.jobseekercopilot.jobservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jobseekercopilot.jobservice.model.dto.AdvertiserType;
import com.jobseekercopilot.jobservice.model.dto.Job;
import com.jobseekercopilot.jobservice.model.dto.CommuteAssessment;
import com.jobseekercopilot.jobservice.model.dto.JobDescriptionCompleteness;
import com.jobseekercopilot.jobservice.model.dto.JobSourceReference;
import com.jobseekercopilot.jobservice.repository.SavedJobRepository;
import com.jobseekercopilot.jobservice.repository.SavedJobSnapshotRepository;
import com.jobseekercopilot.jobservice.service.SavedJobNotFoundException;
import com.jobseekercopilot.jobservice.service.SavedJobSaveOutcome;
import com.jobseekercopilot.jobservice.service.SavedJobSaveResult;
import com.jobseekercopilot.jobservice.service.SavedJobService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "job-service.database.production-safety-check=false",
        "spring.datasource.url=jdbc:h2:mem:saved_jobs;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "job-service.security.jwk-set-uri=http://localhost:65534/jwks",
        "job-service.security.issuer=test-issuer",
        "job-service.security.audience=test-audience"
})
class SavedJobPersistenceIntegrationTest {

    @Autowired
    private SavedJobService service;

    @Autowired
    private SavedJobRepository savedJobRepository;

    @Autowired
    private SavedJobSnapshotRepository snapshotRepository;

    @Test
    void savesReplaysVersionsUnsaveAndReactivatesWithoutLosingHistory() {
        String owner = "owner-" + UUID.randomUUID();
        Job firstSnapshot = job("canonical-" + UUID.randomUUID(), "First description");
        firstSnapshot.setApplicationStatus("APPLIED");
        firstSnapshot.setApplicationId(UUID.randomUUID());
        firstSnapshot.setMatchScore(0.98);
        firstSnapshot.setDistanceMiles(1.5);
        CommuteAssessment commuteAssessment = new CommuteAssessment();
        commuteAssessment.setProviderAttribution("GOOGLE_MAPS");
        CommuteAssessment.ModeAssessment mode = new CommuteAssessment.ModeAssessment();
        mode.setDurationMinutes(37);
        commuteAssessment.setModes(List.of(mode));
        firstSnapshot.setCommuteAssessment(commuteAssessment);

        SavedJobSaveResult created = service.save(owner, firstSnapshot);
        SavedJobSaveResult replayed = service.save(owner, firstSnapshot);

        assertEquals(SavedJobSaveOutcome.CREATED, created.outcome());
        assertEquals(SavedJobSaveOutcome.REPLAYED, replayed.outcome());
        assertEquals(created.savedJob().savedJobId(), replayed.savedJob().savedJobId());
        assertEquals(1, replayed.savedJob().snapshotVersion());
        assertEquals(created.savedJob().contentSha256(), replayed.savedJob().contentSha256());
        assertNull(replayed.savedJob().job().getApplicationStatus());
        assertNull(replayed.savedJob().job().getApplicationId());
        assertNull(replayed.savedJob().job().getMatchScore());
        assertNull(replayed.savedJob().job().getDistanceMiles());
        assertNull(replayed.savedJob().job().getCommuteAssessment());

        Job changedSnapshot = job(
                firstSnapshot.getCanonicalJobId(),
                "Provider changed this description");
        SavedJobSaveResult updated = service.save(owner, changedSnapshot);

        assertEquals(SavedJobSaveOutcome.UPDATED, updated.outcome());
        assertEquals(created.savedJob().savedJobId(), updated.savedJob().savedJobId());
        assertEquals(2, updated.savedJob().snapshotVersion());
        assertNotEquals(
                created.savedJob().contentSha256(),
                updated.savedJob().contentSha256());
        assertEquals(
                2,
                snapshotRepository.countBySavedJobId(
                        updated.savedJob().savedJobId()));

        service.unsave(owner, updated.savedJob().savedJobId());
        service.unsave(owner, updated.savedJob().savedJobId());
        assertThrows(
                SavedJobNotFoundException.class,
                () -> service.get(owner, updated.savedJob().savedJobId()));
        assertEquals(0, service.list(owner, 0, 20).totalElements());

        SavedJobSaveResult reactivated = service.save(owner, changedSnapshot);
        assertEquals(SavedJobSaveOutcome.REACTIVATED, reactivated.outcome());
        assertEquals(updated.savedJob().savedJobId(), reactivated.savedJob().savedJobId());
        assertEquals(2, reactivated.savedJob().snapshotVersion());
        assertEquals(
                2,
                snapshotRepository.countBySavedJobId(
                        reactivated.savedJob().savedJobId()));
    }

    @Test
    void otherUsersCannotReadOrUnsaveTheSnapshot() {
        String owner = "owner-" + UUID.randomUUID();
        String otherOwner = "other-" + UUID.randomUUID();
        SavedJobSaveResult saved =
                service.save(owner, job("canonical-" + UUID.randomUUID(), "Description"));

        assertThrows(
                SavedJobNotFoundException.class,
                () -> service.get(otherOwner, saved.savedJob().savedJobId()));
        service.unsave(otherOwner, saved.savedJob().savedJobId());

        assertEquals(
                saved.savedJob().contentSha256(),
                service.get(owner, saved.savedJob().savedJobId()).contentSha256());
    }

    @Test
    void anExpiredProviderListingRemainsAStableExplicitSnapshot() {
        Job job = job("canonical-" + UUID.randomUUID(), "Retained description");
        job.setExpiresAtUtc(OffsetDateTime.now().minusDays(1));

        SavedJobSaveResult saved =
                service.save("owner-" + UUID.randomUUID(), job);

        assertEquals("EXPIRED_SNAPSHOT", saved.savedJob().sourceState());
        assertEquals("Retained description", saved.savedJob().job().getDescription());
        assertEquals(
                saved.savedJob().contentVersion(),
                "sha256:" + saved.savedJob().contentSha256());
    }

    @Test
    void preservesConfirmedAdvertAndHiringPartyContextInTheImmutableSnapshot() {
        Job job = job(
                "canonical-" + UUID.randomUUID(),
                "Complete responsibilities, requirements and application details.");
        job.setAdvertiserName("Harnham");
        job.setAdvertiserType(AdvertiserType.RECRUITER);
        job.setHiringOrganisationName(null);
        job.setApplicationContactName("Molly Bird");
        job.setDescriptionCompleteness(
                JobDescriptionCompleteness.USER_CONFIRMED);

        SavedJobSaveResult saved = service.save(
                "owner-" + UUID.randomUUID(),
                job);

        assertEquals("Harnham", saved.savedJob().job().getAdvertiserName());
        assertEquals(
                AdvertiserType.RECRUITER,
                saved.savedJob().job().getAdvertiserType());
        assertNull(saved.savedJob().job().getHiringOrganisationName());
        assertEquals(
                "Molly Bird",
                saved.savedJob().job().getApplicationContactName());
        assertEquals(
                JobDescriptionCompleteness.USER_CONFIRMED,
                saved.savedJob().job().getDescriptionCompleteness());
    }

    @Test
    void concurrentEquivalentSavesConvergeOnOneIdentityAndSnapshot() throws Exception {
        String owner = "owner-" + UUID.randomUUID();
        Job job = job("canonical-" + UUID.randomUUID(), "Concurrent description");
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<SavedJobSaveResult>> futures = new ArrayList<>();
            for (int index = 0; index < 4; index++) {
                futures.add(executor.submit(() -> service.save(owner, job)));
            }
            List<SavedJobSaveResult> results = new ArrayList<>();
            for (Future<SavedJobSaveResult> future : futures) {
                results.add(future.get());
            }

            UUID savedJobId = results.get(0).savedJob().savedJobId();
            assertEquals(
                    1,
                    results.stream()
                            .filter(result -> result.outcome()
                                    == SavedJobSaveOutcome.CREATED)
                            .count());
            assertEquals(
                    1,
                    results.stream()
                            .map(result -> result.savedJob().savedJobId())
                            .distinct()
                            .count());
            assertEquals(
                    1,
                    savedJobRepository.countByUserIdAndCanonicalJobId(
                            owner,
                            job.getCanonicalJobId()));
            assertEquals(1, snapshotRepository.countBySavedJobId(savedJobId));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void aRevisitedHistoricalDigestDoesNotReuseTheNextSnapshotVersion() {
        String owner = "owner-" + UUID.randomUUID();
        String canonicalJobId = "canonical-" + UUID.randomUUID();
        Job first = job(canonicalJobId, "First description");
        Job second = job(canonicalJobId, "Second description");
        Job third = job(canonicalJobId, "Third description");

        SavedJobSaveResult versionOne = service.save(owner, first);
        SavedJobSaveResult versionTwo = service.save(owner, second);
        SavedJobSaveResult historicalReplay = service.save(owner, first);
        SavedJobSaveResult versionThree = service.save(owner, third);

        assertEquals(1, versionOne.savedJob().snapshotVersion());
        assertEquals(2, versionTwo.savedJob().snapshotVersion());
        assertEquals(1, historicalReplay.savedJob().snapshotVersion());
        assertEquals(SavedJobSaveOutcome.UPDATED, historicalReplay.outcome());
        assertEquals(3, versionThree.savedJob().snapshotVersion());
        assertEquals(
                3,
                snapshotRepository.countBySavedJobId(
                        versionThree.savedJob().savedJobId()));
    }

    @Test
    void rejectsUnboundedOrUnsupportedSnapshotsBeforePersistence() {
        Job unsupported = job(
                "canonical-" + UUID.randomUUID(),
                "Description");
        unsupported.setCanonicalSchemaVersion("99.0");
        assertThrows(
                IllegalArgumentException.class,
                () -> service.save("owner", unsupported));

        Job oversized = job(
                "canonical-" + UUID.randomUUID(),
                "x".repeat(12_001));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.save("owner", oversized));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.list("owner", 0, 101));
    }

    private Job job(String canonicalJobId, String description) {
        Job job = new Job();
        job.setCanonicalJobId(canonicalJobId);
        job.setId(canonicalJobId);
        job.setTitle("Platform Engineer");
        job.setCompanyName("Example Ltd");
        job.setDescription(description);
        job.setPrimarySource("REED");
        job.setExternalJobId("reed-123");

        JobSourceReference source = new JobSourceReference();
        source.setIntegrationProvider("REED");
        source.setExternalJobId("reed-123");
        source.setListingUrl("https://jobs.example.test/123");
        source.setRetrievedAtUtc(
                OffsetDateTime.parse("2026-07-26T12:00:00Z"));
        job.setSources(List.of(source));
        return job;
    }
}
