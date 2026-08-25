package com.jobseekercopilot.jobservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jobseekercopilot.jobservice.config.JobSearchResilienceProperties;
import com.jobseekercopilot.jobservice.model.dto.Aspirations;
import com.jobseekercopilot.jobservice.model.dto.AdvertiserType;
import com.jobseekercopilot.jobservice.model.dto.CanonicalLocation;
import com.jobseekercopilot.jobservice.model.dto.CandidateProfile;
import com.jobseekercopilot.jobservice.model.dto.HomeLocation;
import com.jobseekercopilot.jobservice.model.dto.Job;
import com.jobseekercopilot.jobservice.model.dto.JobDescriptionCompleteness;
import com.jobseekercopilot.jobservice.model.dto.JobSalary;
import com.jobseekercopilot.jobservice.model.dto.JobSearchRequest;
import com.jobseekercopilot.jobservice.model.dto.JobSourceReference;
import com.jobseekercopilot.jobservice.model.dto.JobSkill;
import com.jobseekercopilot.jobservice.model.dto.JobSkillType;
import com.jobseekercopilot.jobservice.model.dto.ProviderResultStatus;
import com.jobseekercopilot.jobservice.model.dto.ProviderDataProvenance;
import com.jobseekercopilot.jobservice.model.dto.ReedJobSearchResponse;
import com.jobseekercopilot.jobservice.model.dto.WorkPreferences;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class JobSearchServiceTest {

    private final ProviderSearchCoordinator providerSearchCoordinator =
            mock(ProviderSearchCoordinator.class);
    private final OptionalJobMatchingEnricher matchingEnricher =
            mock(OptionalJobMatchingEnricher.class);
    private final JobDeduplicationService deduplicationService =
            new JobDeduplicationService(true, 7, 0.90, 0.80);
    private final JobResultEnrichmentService jobResultEnrichmentService =
            new JobResultEnrichmentService(new SalaryNormalisationService());
    private final DistanceCalculationService distanceCalculationService =
            new DistanceCalculationService();
    private final JobSearchResilienceProperties resilience =
            new JobSearchResilienceProperties(1000, 500, 250, 100, 4, 8);
    private final JobSearchService service = new JobSearchService(
            providerSearchCoordinator,
            deduplicationService,
            jobResultEnrichmentService,
            distanceCalculationService,
            matchingEnricher,
            resilience,
            10);

    @Test
    void returnsProviderDetailsForTheAuthenticatedOwner() {
        Job detail = job("reed-42", "Software Developer", "REED");
        when(providerSearchCoordinator.details(
                "user-1", "REED", "reed-42"))
                .thenReturn(Optional.of(detail));

        Job result = service.getJobDetails(
                "user-1", "REED", "reed-42");

        assertThat(result).isSameAs(detail);
    }

    @Test
    void reportsMissingProviderDetailsWithoutFallingBackToAStalePreview() {
        when(providerSearchCoordinator.details(
                "user-1", "REED", "missing"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getJobDetails(
                "user-1", "REED", "missing"))
                .isInstanceOf(JobSearchService.JobNotFoundException.class)
                .hasMessage("Job details are not available");
    }

    @Test
    void returnsOnlyTheAuthenticatedOwnersSelectedNhsPreview() {
        Job preview = job("5554443", "Senior Digital Developer", "NHS_JOBS");
        preview.setDescription("Short NHS Jobs search-feed preview");
        preview.setDescriptionCompleteness(JobDescriptionCompleteness.FULL);
        preview.setSourceUrl(
                "https://beta.jobs.nhs.uk/candidate/jobadvert/M0048-26-0423");
        when(providerSearchCoordinator.search(
                eq("user-1"), any(JobSearchCriteria.class), anySet(), anyLong()))
                .thenReturn(fanOut(
                        List.of(preview),
                        List.of(status("NHS_JOBS", "SUCCESS", 1)),
                        true,
                        true,
                        true));
        when(providerSearchCoordinator.details(
                "user-1", "NHS_JOBS", "5554443"))
                .thenReturn(Optional.empty());
        when(providerSearchCoordinator.details(
                "user-2", "NHS_JOBS", "5554443"))
                .thenReturn(Optional.empty());
        passThroughMatching();

        service.searchJobs("user-1", request("digital developer"));

        Job selected = service.getJobDetails(
                "user-1", "nhs_jobs", "5554443");
        assertThat(selected.getExternalJobId()).isEqualTo("5554443");
        assertThat(selected.getSourceUrl()).isEqualTo(
                "https://beta.jobs.nhs.uk/candidate/jobadvert/M0048-26-0423");
        assertThat(selected.getDescriptionCompleteness())
                .isEqualTo(JobDescriptionCompleteness.PREVIEW);
        assertThatThrownBy(() -> service.getJobDetails(
                "user-2", "NHS_JOBS", "5554443"))
                .isInstanceOf(JobSearchService.JobNotFoundException.class)
                .hasMessage("Job details are not available");
    }

    @Test
    void doesNotUseTheSelectedPreviewFallbackForAnotherProvider() {
        Job preview = job("reed-42", "Software Developer", "REED");
        preview.setDescriptionCompleteness(JobDescriptionCompleteness.PREVIEW);
        when(providerSearchCoordinator.search(
                eq("user-1"), any(JobSearchCriteria.class), anySet(), anyLong()))
                .thenReturn(fanOut(
                        List.of(preview),
                        List.of(status("REED", "SUCCESS", 1)),
                        true,
                        true,
                        true));
        when(providerSearchCoordinator.details(
                "user-1", "REED", "reed-42"))
                .thenReturn(Optional.empty());
        passThroughMatching();

        service.searchJobs("user-1", request("software developer"));

        assertThatThrownBy(() -> service.getJobDetails(
                "user-1", "REED", "reed-42"))
                .isInstanceOf(JobSearchService.JobNotFoundException.class)
                .hasMessage("Job details are not available");
    }

    @Test
    void expiresTheOwnerScopedNhsPreviewWithTheSearchCacheTtl() {
        MutableClock clock = new MutableClock(
                Instant.parse("2026-08-25T04:00:00Z"));
        JobSearchService clockedService = new JobSearchService(
                providerSearchCoordinator,
                deduplicationService,
                jobResultEnrichmentService,
                distanceCalculationService,
                matchingEnricher,
                resilience,
                1,
                new JobDiscoveryClassifier(clock),
                clock);
        Job preview = job("5554443", "Senior Digital Developer", "NHS_JOBS");
        preview.setDescriptionCompleteness(JobDescriptionCompleteness.PREVIEW);
        when(providerSearchCoordinator.search(
                eq("user-1"), any(JobSearchCriteria.class), anySet(), anyLong()))
                .thenReturn(fanOut(
                        List.of(preview),
                        List.of(status("NHS_JOBS", "SUCCESS", 1)),
                        true,
                        true,
                        true));
        when(providerSearchCoordinator.details(
                "user-1", "NHS_JOBS", "5554443"))
                .thenReturn(Optional.empty());
        passThroughMatching();

        clockedService.searchJobs("user-1", request("digital developer"));
        clock.advanceSeconds(61);

        assertThatThrownBy(() -> clockedService.getJobDetails(
                "user-1", "NHS_JOBS", "5554443"))
                .isInstanceOf(JobSearchService.JobNotFoundException.class)
                .hasMessage("Job details are not available");
    }

    @Test
    void prefersACompleteNhsProviderDetailOverTheCachedPreview() {
        Job preview = job("5554443", "Senior Digital Developer", "NHS_JOBS");
        preview.setDescription("Short NHS Jobs search-feed preview");
        preview.setDescriptionCompleteness(JobDescriptionCompleteness.PREVIEW);
        Job complete = job("5554443", "Senior Digital Developer", "NHS_JOBS");
        complete.setDescription("Complete supported provider detail");
        complete.setDescriptionCompleteness(JobDescriptionCompleteness.FULL);
        when(providerSearchCoordinator.search(
                eq("user-1"), any(JobSearchCriteria.class), anySet(), anyLong()))
                .thenReturn(fanOut(
                        List.of(preview),
                        List.of(status("NHS_JOBS", "SUCCESS", 1)),
                        true,
                        true,
                        true));
        when(providerSearchCoordinator.details(
                "user-1", "NHS_JOBS", "5554443"))
                .thenReturn(Optional.of(complete));
        passThroughMatching();

        service.searchJobs("user-1", request("digital developer"));

        Job selected = service.getJobDetails(
                "user-1", "NHS_JOBS", "5554443");
        assertThat(selected.getDescription())
                .isEqualTo("Complete supported provider detail");
        assertThat(selected.getDescriptionCompleteness())
                .isEqualTo(JobDescriptionCompleteness.FULL);
    }

    @Test
    void rejectsProviderDetailsWithAKnownExpiredDeadline() {
        Job detail = job("reed-42", "Software Developer", "REED");
        detail.setExpiresAtUtc(java.time.OffsetDateTime.parse(
                "2020-01-01T00:00:00Z"));
        when(providerSearchCoordinator.details(
                "user-1", "REED", "reed-42"))
                .thenReturn(Optional.of(detail));

        assertThatThrownBy(() -> service.getJobDetails(
                "user-1", "REED", "reed-42"))
                .isInstanceOf(JobSearchService.JobNotFoundException.class)
                .hasMessage("Job is no longer available");
    }

    @Test
    void returnsHealthyJobsAndDeterministicPartialStatusWhenOneProviderFails() {
        Job reedJob = job("reed-1", "Cleaning Operative", "REED");
        when(providerSearchCoordinator.search(
                eq("user-1"), any(JobSearchCriteria.class), anySet(), anyLong()))
                .thenReturn(fanOut(
                        List.of(reedJob),
                        List.of(
                                status("REED", "SUCCESS", 1),
                                status("ADZUNA", "UNAVAILABLE", 0)),
                        true,
                        true,
                        false));
        when(matchingEnricher.enrich(eq("user-1"), any(), anyLong()))
                .thenAnswer(invocation -> new OptionalJobMatchingEnricher.MatchingOutcome(
                        invocation.getArgument(1), "COMPLETE", false));

        var result = service.searchJobs("user-1", request("cleaning"));

        assertThat(result.getJobs()).extracting(Job::getId).containsExactly("reed-1");
        assertThat(result.getSearchStatus()).isEqualTo("PARTIAL");
        assertThat(result.getMatchingStatus()).isEqualTo("COMPLETE");
        assertThat(result.getProviderResults())
                .extracting("provider", "status", "rawResultCount")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("REED", "SUCCESS", 1),
                        org.assertj.core.groups.Tuple.tuple("ADZUNA", "UNAVAILABLE", 0));
    }

    @Test
    void returnsCompleteSuccessfulResponseForHealthyZeroResultProvider() {
        when(providerSearchCoordinator.search(
                eq("user-1"), any(JobSearchCriteria.class), anySet(), anyLong()))
                .thenReturn(fanOut(
                        List.of(),
                        List.of(status("REED", "SUCCESS", 0)),
                        true,
                        true,
                        true));
        when(matchingEnricher.enrich(eq("user-1"), any(), anyLong()))
                .thenReturn(new OptionalJobMatchingEnricher.MatchingOutcome(
                        List.of(), "COMPLETE", false));

        var result = service.searchJobs("user-1", request("developer"));

        assertThat(result.getJobs()).isEmpty();
        assertThat(result.getTotalResults()).isZero();
        assertThat(result.getSearchStatus()).isEqualTo("COMPLETE");
        assertThat(result.getProviderResults())
                .extracting("provider", "status", "rawResultCount")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "REED", "SUCCESS", 0));
    }

    @Test
    void acceptsSkippedOptionalEmploymentTypesWithoutInventingADefault() {
        when(providerSearchCoordinator.search(
                eq("user-1"), any(JobSearchCriteria.class), anySet(), anyLong()))
                .thenReturn(fanOut(
                        List.of(),
                        List.of(status("REED", "SUCCESS", 0)),
                        true,
                        true,
                        true));
        when(matchingEnricher.enrich(eq("user-1"), any(), anyLong()))
                .thenReturn(new OptionalJobMatchingEnricher.MatchingOutcome(
                        List.of(), "COMPLETE", false));
        JobSearchRequest request = request("support analyst");
        WorkPreferences preferences = new WorkPreferences();
        preferences.setRemotePreference("REMOTE");
        request.setWorkPreferences(preferences);

        var result = service.searchJobs("user-1", request);

        assertThat(result.getSearchStatus()).isEqualTo("COMPLETE");
        ArgumentCaptor<JobSearchCriteria> criteria =
                ArgumentCaptor.forClass(JobSearchCriteria.class);
        verify(providerSearchCoordinator).search(
                eq("user-1"), criteria.capture(), anySet(), anyLong());
        assertThat(criteria.getValue().getEmploymentTypes()).isEmpty();
    }

    @Test
    void forwardsCandidateEvidenceAndExactTargetRoleToDeterministicMatching() {
        JobSearchRequest request = request("Software Developer");
        CandidateProfile candidate = new CandidateProfile();
        candidate.setSkills(List.of("Java"));
        request.setCandidateProfile(candidate);
        Job providerJob = job("job-1", "Junior Software Developer", "REED");
        when(providerSearchCoordinator.search(
                eq("user-1"), any(JobSearchCriteria.class), anySet(), anyLong()))
                .thenReturn(fanOut(
                        List.of(providerJob),
                        List.of(status("REED", "SUCCESS", 1)),
                        true, true, true));
        when(matchingEnricher.enrich(
                eq("user-1"), any(), anyLong(), isNull(), isNull(),
                eq("Software Developer"), same(candidate)))
                .thenAnswer(invocation ->
                        new OptionalJobMatchingEnricher.MatchingOutcome(
                                invocation.getArgument(1), "COMPLETE", false));

        var result = service.searchJobs("user-1", request);

        assertThat(result.getJobs()).singleElement()
                .extracting(Job::getTitle)
                .isEqualTo("Junior Software Developer");
        verify(matchingEnricher).enrich(
                eq("user-1"), any(), anyLong(), isNull(), isNull(),
                eq("Software Developer"), same(candidate));
    }

    @Test
    void juniorStressSliceFiltersTrainingAndOccupationMismatchThenRanksJuniorFirst() {
        JobSearchRequest request = request("Junior Software Developer");
        CandidateProfile candidate = new CandidateProfile();
        candidate.setSkills(List.of("Java"));
        request.setCandidateProfile(candidate);
        Job junior = job("junior", "Junior Software Developer", "REED");
        junior.setDescription("Permanent salaried vacancy building Java services");
        Job senior = job("senior", "Senior Software Developer", "REED");
        senior.setDescription("Lead Java services; 7 years experience required");
        Job training = job("training", "Software Developer Career Programme", "REED");
        training.setDescription("Training provider course fee with finance and job placement support");
        Job retail = job("retail", "Retail Sales Assistant", "REED");
        retail.setDescription("Serve customers in a shop");
        when(providerSearchCoordinator.search(
                eq("user-1"), any(JobSearchCriteria.class), anySet(), anyLong()))
                .thenReturn(fanOut(
                        List.of(senior, training, retail, junior),
                        List.of(status("REED", "SUCCESS", 4)),
                        true, true, true));
        when(matchingEnricher.enrich(
                eq("user-1"), any(), anyLong(), isNull(), isNull(),
                eq("Junior Software Developer"), same(candidate)))
                .thenAnswer(invocation -> {
                    List<Job> visible = invocation.getArgument(1);
                    visible.forEach(job -> job.setMatchScore(
                            "JUNIOR_ENTRY".equals(
                                    job.getDiscoveryAssessment().getSeniority())
                                    ? .82 : .45));
                    return new OptionalJobMatchingEnricher.MatchingOutcome(
                            visible, "COMPLETE", false);
                });

        var result = service.searchJobs("user-1", request);

        assertThat(result.getJobs())
                .extracting(Job::getTitle)
                .containsExactly(
                        "Junior Software Developer",
                        "Senior Software Developer");
        assertThat(result.getQualitySummary().getAssessedJobCount()).isEqualTo(4);
        assertThat(result.getQualitySummary().getEligibleJobCount()).isEqualTo(2);
        assertThat(result.getQualitySummary().getExcludedPaidTrainingCount()).isOne();
        assertThat(result.getQualitySummary().getExcludedOccupationMismatchCount()).isOne();
        assertThat(result.getJobs().get(1).getDiscoveryAssessment().getSeniority())
                .isEqualTo("SENIOR");
    }

    @Test
    void matchingOutagePreservesProviderJobsAndMarksResponsePartial() {
        Job reedJob = job("reed-1", "Developer", "REED");
        when(providerSearchCoordinator.search(
                eq("user-1"), any(JobSearchCriteria.class), anySet(), anyLong()))
                .thenReturn(fanOut(
                        List.of(reedJob),
                        List.of(status("REED", "SUCCESS", 1)),
                        true,
                        true,
                        true));
        when(matchingEnricher.enrich(eq("user-1"), any(), anyLong()))
                .thenAnswer(invocation -> new OptionalJobMatchingEnricher.MatchingOutcome(
                        invocation.getArgument(1), "UNAVAILABLE", true));

        var result = service.searchJobs("user-1", request("developer"));

        assertThat(result.getJobs()).extracting(Job::getId).containsExactly("reed-1");
        assertThat(result.getSearchStatus()).isEqualTo("PARTIAL");
        assertThat(result.getMatchingStatus()).isEqualTo("UNAVAILABLE");
    }

    @Test
    void allAttemptedProviderFailuresProduceStableServiceUnavailableError() {
        when(providerSearchCoordinator.search(
                eq("user-1"), any(JobSearchCriteria.class), anySet(), anyLong()))
                .thenReturn(fanOut(
                        List.of(),
                        List.of(
                                status("REED", "TIMED_OUT", 0),
                                status("ADZUNA", "RATE_LIMITED", 0)),
                        true,
                        false,
                        false));

        assertThatThrownBy(() -> service.searchJobs("user-1", request("developer")))
                .isInstanceOf(JobSearchService.DownstreamServiceUnavailableException.class)
                .hasMessage("All requested providers are unavailable");
    }

    @Test
    void addsDistanceBeforeOptionalMatching() {
        Job job = jobWithCoordinates("reed-1", 51.5074, -0.1278);
        when(providerSearchCoordinator.search(
                eq("user-1"), any(JobSearchCriteria.class), anySet(), anyLong()))
                .thenReturn(fanOut(
                        List.of(job),
                        List.of(status("REED", "SUCCESS", 1)),
                        true,
                        true,
                        true));
        when(matchingEnricher.enrich(eq("user-1"), any(), anyLong()))
                .thenAnswer(invocation -> new OptionalJobMatchingEnricher.MatchingOutcome(
                        invocation.getArgument(1), "COMPLETE", false));

        service.searchJobs(
                "user-1",
                requestWithHomeLocation("developer", 51.5010, -0.1416));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Job>> jobsCaptor = ArgumentCaptor.forClass(List.class);
        verify(matchingEnricher).enrich(eq("user-1"), jobsCaptor.capture(), anyLong());
        assertThat(jobsCaptor.getValue().get(0).getDistanceMiles())
                .isNotNull()
                .isLessThan(1.0);
    }

    @Test
    void providerCacheNeverLeaksFirstUsersApplicationState() {
        UUID firstUsersApplication = UUID.randomUUID();
        when(providerSearchCoordinator.search(
                any(), any(JobSearchCriteria.class), anySet(), anyLong()))
                .thenReturn(fanOut(
                        List.of(job("reed-1", "Developer", "REED")),
                        List.of(status("REED", "SUCCESS", 1)),
                        true,
                        true,
                        true));
        when(matchingEnricher.enrich(eq("user-1"), any(), anyLong()))
                .thenAnswer(invocation -> {
                    List<Job> jobs = invocation.getArgument(1);
                    jobs.get(0).setApplicationId(firstUsersApplication);
                    jobs.get(0).setApplicationStatus("APPLIED");
                    return new OptionalJobMatchingEnricher.MatchingOutcome(
                            jobs, "COMPLETE", false);
                });
        when(matchingEnricher.enrich(eq("user-2"), any(), anyLong()))
                .thenAnswer(invocation -> new OptionalJobMatchingEnricher.MatchingOutcome(
                        invocation.getArgument(1), "UNAVAILABLE", true));

        var firstResult = service.searchJobs("user-1", request("developer"));
        var secondResult = service.searchJobs("user-2", request("developer"));

        assertThat(firstResult.getJobs().get(0).getApplicationId())
                .isEqualTo(firstUsersApplication);
        assertThat(secondResult.getJobs().get(0).getApplicationId()).isNull();
        assertThat(secondResult.getJobs().get(0).getApplicationStatus()).isNull();
        verify(providerSearchCoordinator, times(1))
                .search(any(), any(JobSearchCriteria.class), anySet(), anyLong());
    }

    @Test
    void providerCacheDeepCopiesCanonicalNestedEvidence() {
        Job providerJob = job("reed-1", "Developer", "REED");
        JobSkill skill = new JobSkill();
        skill.setName("Java");
        skill.setRawName("java");
        skill.setType(JobSkillType.REQUIRED);
        providerJob.setSkills(List.of(skill));
        when(providerSearchCoordinator.search(
                any(), any(JobSearchCriteria.class), anySet(), anyLong()))
                .thenReturn(fanOut(
                        List.of(providerJob),
                        List.of(status("REED", "SUCCESS", 1)),
                        true,
                        true,
                        true));
        when(matchingEnricher.enrich(eq("user-1"), any(), anyLong()))
                .thenAnswer(invocation -> {
                    List<Job> jobs = invocation.getArgument(1);
                    jobs.get(0).getSkills().get(0)
                            .setName("user-specific mutation");
                    return new OptionalJobMatchingEnricher.MatchingOutcome(
                            jobs, "COMPLETE", false);
                });
        when(matchingEnricher.enrich(eq("user-2"), any(), anyLong()))
                .thenAnswer(invocation ->
                        new OptionalJobMatchingEnricher.MatchingOutcome(
                                invocation.getArgument(1),
                                "UNAVAILABLE",
                                true));

        service.searchJobs("user-1", request("developer"));
        var secondResult =
                service.searchJobs("user-2", request("developer"));

        assertThat(secondResult.getJobs().get(0).getSkills())
                .singleElement()
                .satisfies(cachedSkill -> {
                    assertThat(cachedSkill.getName()).isEqualTo("Java");
                    assertThat(cachedSkill.getRawName()).isEqualTo("java");
                    assertThat(cachedSkill.getType())
                            .isEqualTo(JobSkillType.REQUIRED);
                });
        verify(providerSearchCoordinator, times(1))
                .search(any(), any(JobSearchCriteria.class), anySet(), anyLong());
    }

    @Test
    void providerCachePreservesAdvertiserAndDescriptionCompleteness() {
        Job providerJob = job("reed-1", "Developer", "REED");
        providerJob.setAdvertiserName("Example Recruiter");
        providerJob.setAdvertiserType(AdvertiserType.RECRUITER);
        providerJob.setHiringOrganisationName("Example Employer");
        providerJob.setApplicationContactName("Alex Recruiter");
        providerJob.setDescriptionCompleteness(
                JobDescriptionCompleteness.PREVIEW);
        when(providerSearchCoordinator.search(
                any(), any(JobSearchCriteria.class), anySet(), anyLong()))
                .thenReturn(fanOut(
                        List.of(providerJob),
                        List.of(status("REED", "SUCCESS", 1)),
                        true,
                        true,
                        true));
        when(matchingEnricher.enrich(any(), any(), anyLong()))
                .thenAnswer(invocation ->
                        new OptionalJobMatchingEnricher.MatchingOutcome(
                                invocation.getArgument(1),
                                "COMPLETE",
                                false));

        var result = service.searchJobs("user-1", request("developer"));

        assertThat(result.getJobs()).singleElement().satisfies(job -> {
            assertThat(job.getAdvertiserName())
                    .isEqualTo("Example Recruiter");
            assertThat(job.getAdvertiserType())
                    .isEqualTo(AdvertiserType.RECRUITER);
            assertThat(job.getHiringOrganisationName())
                    .isEqualTo("Example Employer");
            assertThat(job.getApplicationContactName())
                    .isEqualTo("Alex Recruiter");
            assertThat(job.getDescriptionCompleteness())
                    .isEqualTo(JobDescriptionCompleteness.PREVIEW);
        });
    }

    @Test
    void partialProviderResultsAreNotCached() {
        when(providerSearchCoordinator.search(
                any(), any(JobSearchCriteria.class), anySet(), anyLong()))
                .thenReturn(fanOut(
                        List.of(job("reed-1", "Developer", "REED")),
                        List.of(
                                status("REED", "SUCCESS", 1),
                                status("ADZUNA", "TIMED_OUT", 0)),
                        true,
                        true,
                        false));
        when(matchingEnricher.enrich(any(), any(), anyLong()))
                .thenAnswer(invocation -> new OptionalJobMatchingEnricher.MatchingOutcome(
                        invocation.getArgument(1), "COMPLETE", false));

        service.searchJobs("user-1", request("developer"));
        service.searchJobs("user-2", request("developer"));

        verify(providerSearchCoordinator, times(2))
                .search(any(), any(JobSearchCriteria.class), anySet(), anyLong());
    }

    @Test
    void reusesOwnerScopedPartialSnapshotForStableSubsequentPages() {
        when(providerSearchCoordinator.search(
                eq("user-1"), any(JobSearchCriteria.class), anySet(), anyLong()))
                .thenReturn(fanOut(
                        List.of(
                                job("first", "A role", "REED"),
                                job("second", "B role", "REED"),
                                job("third", "C role", "REED")),
                        List.of(
                                status("REED", "SUCCESS", 3),
                                status("ADZUNA", "TIMED_OUT", 0)),
                        true,
                        true,
                        false));
        passThroughMatching();
        JobSearchRequest firstPage = request("developer");
        firstPage.setPage(1);
        firstPage.setPageSize(2);
        firstPage.setSort("JOB_TITLE_AZ");
        JobSearchRequest secondPage = request("developer");
        secondPage.setPage(2);
        secondPage.setPageSize(2);
        secondPage.setSort("JOB_TITLE_AZ");

        var pageOne = service.searchJobs("user-1", firstPage);
        var pageTwo = service.searchJobs("user-1", secondPage);

        assertThat(pageOne.getSearchStatus()).isEqualTo("PARTIAL");
        assertThat(pageTwo.getSearchStatus()).isEqualTo("PARTIAL");
        assertThat(pageOne.getJobs())
                .extracting(Job::getCanonicalJobId)
                .doesNotContainAnyElementsOf(
                        pageTwo.getJobs().stream()
                                .map(Job::getCanonicalJobId)
                                .toList());
        assertThat(pageTwo.getResultsByTargetRole())
                .singleElement()
                .satisfies(role -> {
                    assertThat(role.getSearchStatus()).isEqualTo("PARTIAL");
                    assertThat(role.getProviderResults())
                            .extracting(
                                    ProviderResultStatus::getProvider,
                                    ProviderResultStatus::getStatus)
                            .containsExactly(
                                    org.assertj.core.groups.Tuple.tuple(
                                            "REED",
                                            "SUCCESS"),
                                    org.assertj.core.groups.Tuple.tuple(
                                            "ADZUNA",
                                            "TIMED_OUT"));
                });
        verify(providerSearchCoordinator, times(1))
                .search(any(), any(JobSearchCriteria.class), anySet(), anyLong());
        verify(matchingEnricher, times(2))
                .enrich(eq("user-1"), any(), anyLong());
    }

    @Test
    void defaultsToAStableBoundedFirstPageWithTruthfulMetadata() {
        List<Job> jobs = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            Job job = job(
                    "job-" + index,
                    "Role " + index,
                    index % 2 == 0 ? "REED" : "ADZUNA");
            job.setMatchScore((double) index);
            jobs.add(job);
        }
        when(providerSearchCoordinator.search(
                eq("user-1"), any(JobSearchCriteria.class), anySet(), anyLong()))
                .thenReturn(fanOut(
                        jobs,
                        List.of(status("REED", "SUCCESS", 12)),
                        true,
                        true,
                        true));
        passThroughMatching();

        var result = service.searchJobs("user-1", request("developer"));

        List<String> firstOrder = result.getJobs().stream()
                .map(Job::getTitle).toList();
        assertThat(firstOrder).hasSize(10);
        assertThat(firstOrder).isNotEqualTo(List.of(
                "Role 11", "Role 10", "Role 9", "Role 8", "Role 7",
                "Role 6", "Role 5", "Role 4", "Role 3", "Role 2"));
        assertThat(result.getJobs())
                .allMatch(job -> job.getMatchScore() == null,
                        "provider-supplied scores must not be presented as personal relevance");
        var repeated = service.searchJobs("user-1", request("developer"));
        assertThat(repeated.getJobs()).extracting(Job::getTitle)
                .containsExactlyElementsOf(firstOrder);
        assertThat(result.getFreshness().getResultSource())
                .isEqualTo("PROVIDER_RESPONSE");
        assertThat(repeated.getFreshness().getResultSource())
                .isEqualTo("JOB_SERVICE_CACHE");
        assertThat(result.getTotalResults()).isEqualTo(12);
        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getPageSize()).isEqualTo(10);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.getSort()).isEqualTo("MOST_RELEVANT");
        assertThat(result.getResultsByTargetRole())
                .singleElement()
                .satisfies(group -> {
                    assertThat(group.getTargetRole()).isEqualTo("developer");
                    assertThat(group.getJobs()).hasSize(10);
                });
    }

    @Test
    void fixedClockMakesRetrievalAndCacheAgeMetadataDeterministic() {
        MutableClock clock = new MutableClock(
                Instant.parse("2026-08-13T08:15:30Z"));
        JobSearchService clockedService = new JobSearchService(
                providerSearchCoordinator,
                deduplicationService,
                jobResultEnrichmentService,
                distanceCalculationService,
                matchingEnricher,
                resilience,
                10,
                new JobDiscoveryClassifier(clock),
                clock);
        Job freshJob = job("clocked-job", "Platform Developer", "REED");
        JobSourceReference source = new JobSourceReference();
        source.setProvider("REED");
        source.setExternalJobId("clocked-job");
        freshJob.setSources(List.of(source));
        ProviderResultStatus provider = status("REED", "SUCCESS", 1);
        provider.getDataProvenance().setRetrievedAtUtc(
                OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        provider.getDataProvenance().setServedAtUtc(
                OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        when(providerSearchCoordinator.search(
                eq("clock-owner"), any(JobSearchCriteria.class), anySet(), anyLong()))
                .thenReturn(fanOut(
                        List.of(freshJob),
                        List.of(provider),
                        true,
                        true,
                        true));
        passThroughMatching();

        ReedJobSearchResponse fresh = clockedService.searchJobs(
                "clock-owner", request("clocked platform developer"));
        clock.advanceSeconds(90);
        ReedJobSearchResponse cached = clockedService.searchJobs(
                "clock-owner", request("clocked platform developer"));

        assertThat(fresh.getJobs().get(0).getSources().get(0).getRetrievedAtUtc())
                .isEqualTo(OffsetDateTime.parse("2026-08-13T08:15:30Z"));
        assertThat(fresh.getFreshness().getServedAtUtc())
                .isEqualTo(OffsetDateTime.parse("2026-08-13T08:15:30Z"));
        assertThat(cached.getFreshness().getServedAtUtc())
                .isEqualTo(OffsetDateTime.parse("2026-08-13T08:17:00Z"));
        assertThat(cached.getFreshness().getMaximumCacheAgeSeconds())
                .isEqualTo(90L);
        assertThat(cached.getProviderResults())
                .singleElement()
                .satisfies(result -> {
                    assertThat(result.getDataProvenance().getResultSource())
                            .isEqualTo("JOB_SERVICE_CACHE");
                    assertThat(result.getDataProvenance().getRetrievedAtUtc())
                            .isEqualTo(OffsetDateTime.parse("2026-08-13T08:15:30Z"));
                    assertThat(result.getDataProvenance().getServedAtUtc())
                            .isEqualTo(OffsetDateTime.parse("2026-08-13T08:17:00Z"));
                    assertThat(result.getDataProvenance().getCacheAgeSeconds())
                            .isEqualTo(90L);
                });
        verify(providerSearchCoordinator, times(1))
                .search(eq("clock-owner"), any(), anySet(), anyLong());
    }

    @Test
    void sortsKnownSalaryValuesHighestFirstAndPlacesMissingValuesLast() {
        Job lower = job("lower", "Lower salary", "REED");
        lower.setSalary(new JobSalary(40_000, 50_000, "GBP", "YEAR"));
        Job missing = job("missing", "Salary not supplied", "ADZUNA");
        Job higher = job("higher", "Higher salary", "JSEARCH");
        higher.setSalary(new JobSalary(70_000, 90_000, "GBP", "YEAR"));
        when(providerSearchCoordinator.search(
                eq("user-1"), any(JobSearchCriteria.class), anySet(), anyLong()))
                .thenReturn(fanOut(
                        List.of(lower, missing, higher),
                        List.of(status("REED", "SUCCESS", 3)),
                        true,
                        true,
                        true));
        passThroughMatching();
        JobSearchRequest request = request("developer");
        request.setSort("HIGHEST_SALARY");

        var result = service.searchJobs("user-1", request);

        assertThat(result.getJobs())
                .extracting(Job::getTitle)
                .containsExactly(
                        "Higher salary",
                        "Lower salary",
                        "Salary not supplied");
    }

    @Test
    void pagesEachTargetRoleIndependentlyWithRoleScopedTotals() {
        when(providerSearchCoordinator.search(
                eq("user-1"), any(JobSearchCriteria.class), anySet(), anyLong()))
                .thenReturn(
                        fanOut(
                                List.of(
                                        job("developer-b", "B role", "REED"),
                                        job("developer-d", "D role", "REED")),
                                List.of(status("REED", "SUCCESS", 2)),
                                true,
                                true,
                                true),
                        fanOut(
                                List.of(
                                        job("tester-a", "A role", "ADZUNA"),
                                        job("tester-c", "C role", "ADZUNA")),
                                List.of(status("ADZUNA", "SUCCESS", 2)),
                                true,
                                true,
                                true));
        passThroughMatching();
        JobSearchRequest request = request("developer", "tester");
        request.setPage(2);
        request.setPageSize(1);
        request.setSort("job_title_az");

        var result = service.searchJobs("user-1", request);

        assertThat(result.getJobs())
                .extracting(Job::getTitle)
                .containsExactly("B role");
        assertThat(result.getTotalResults()).isEqualTo(4);
        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.getPageSize()).isEqualTo(1);
        assertThat(result.getTotalPages()).isEqualTo(4);
        assertThat(result.getSort()).isEqualTo("JOB_TITLE_AZ");
        assertThat(result.getResultsByTargetRole())
                .extracting(ReedJobSearchResponse.TargetRoleJobResults::getTargetRole)
                .containsExactly("developer", "tester");
        assertThat(result.getResultsByTargetRole().get(0))
                .satisfies(developer -> {
                    assertThat(developer.getJobs())
                            .extracting(Job::getTitle)
                            .containsExactly("D role");
                    assertThat(developer.getTotalResults()).isEqualTo(2);
                    assertThat(developer.getPage()).isEqualTo(2);
                    assertThat(developer.getPageSize()).isEqualTo(1);
                    assertThat(developer.getTotalPages()).isEqualTo(2);
                    assertThat(developer.getSearchStatus())
                            .isEqualTo("COMPLETE");
                    assertThat(developer.getMatchingStatus())
                            .isEqualTo("COMPLETE");
                    assertThat(developer.getProviderResults())
                            .extracting(ProviderResultStatus::getProvider)
                            .containsExactly("REED");
                });
        assertThat(result.getResultsByTargetRole().get(1))
                .satisfies(tester -> {
                    assertThat(tester.getJobs())
                            .extracting(Job::getTitle)
                            .containsExactly("C role");
                    assertThat(tester.getTotalResults()).isEqualTo(2);
                    assertThat(tester.getPage()).isEqualTo(2);
                    assertThat(tester.getPageSize()).isEqualTo(1);
                    assertThat(tester.getTotalPages()).isEqualTo(2);
                    assertThat(tester.getSearchStatus())
                            .isEqualTo("COMPLETE");
                    assertThat(tester.getProviderResults())
                            .extracting(ProviderResultStatus::getProvider)
                            .containsExactly("ADZUNA");
                });
    }

    @Test
    void preservesSuccessfulRoleWhenAnotherRoleProvidersAreUnavailable() {
        when(providerSearchCoordinator.search(
                eq("user-1"), any(JobSearchCriteria.class), anySet(), anyLong()))
                .thenReturn(
                        fanOut(
                                List.of(job(
                                        "developer-a",
                                        "Developer role",
                                        "REED")),
                                List.of(status("REED", "SUCCESS", 1)),
                                true,
                                true,
                                true),
                        fanOut(
                                List.of(),
                                List.of(status(
                                        "ADZUNA",
                                        "RATE_LIMITED",
                                        0)),
                                true,
                                false,
                                false));
        passThroughMatching();

        var result =
                service.searchJobs("user-1", request("developer", "tester"));

        assertThat(result.getSearchStatus()).isEqualTo("PARTIAL");
        assertThat(result.getTotalResults()).isEqualTo(1);
        assertThat(result.getResultsByTargetRole().get(0))
                .satisfies(developer -> {
                    assertThat(developer.getJobs()).hasSize(1);
                    assertThat(developer.getSearchStatus())
                            .isEqualTo("COMPLETE");
                    assertThat(developer.getTotalResults()).isEqualTo(1);
                });
        assertThat(result.getResultsByTargetRole().get(1))
                .satisfies(tester -> {
                    assertThat(tester.getJobs()).isEmpty();
                    assertThat(tester.getSearchStatus())
                            .isEqualTo("UNAVAILABLE");
                    assertThat(tester.getMatchingStatus())
                            .isEqualTo("NOT_RUN");
                    assertThat(tester.getTotalResults()).isZero();
                    assertThat(tester.getTotalPages()).isZero();
                    assertThat(tester.getProviderResults())
                            .extracting(
                                    ProviderResultStatus::getProvider,
                                    ProviderResultStatus::getStatus)
                            .containsExactly(
                                    org.assertj.core.groups.Tuple.tuple(
                                            "ADZUNA",
                                            "RATE_LIMITED"));
                });
    }

    @Test
    void reportsRoleScopedTimeoutWhenDeadlineStopsLaterRoleFanOut()
            throws Exception {
        JobSearchResilienceProperties shortRequest =
                new JobSearchResilienceProperties(
                        500,
                        250,
                        250,
                        100,
                        4,
                        8);
        JobSearchService shortDeadlineService = new JobSearchService(
                providerSearchCoordinator,
                deduplicationService,
                jobResultEnrichmentService,
                distanceCalculationService,
                matchingEnricher,
                shortRequest,
                10);
        when(providerSearchCoordinator.search(
                eq("user-1"), any(JobSearchCriteria.class), anySet(), anyLong()))
                .thenReturn(fanOut(
                        List.of(job("developer", "Developer role", "REED")),
                        List.of(status("REED", "SUCCESS", 1)),
                        true,
                        true,
                        true));
        when(matchingEnricher.enrich(
                eq("user-1"), any(), anyLong()))
                .thenAnswer(invocation -> {
                    Thread.sleep(550);
                    return new OptionalJobMatchingEnricher.MatchingOutcome(
                            invocation.getArgument(1),
                            "COMPLETE",
                            false);
                });

        var result = shortDeadlineService.searchJobs(
                "user-1",
                request("developer", "tester"));

        assertThat(result.getSearchStatus()).isEqualTo("PARTIAL");
        assertThat(result.getResultsByTargetRole())
                .extracting(
                        ReedJobSearchResponse.TargetRoleJobResults::getTargetRole)
                .containsExactly("developer", "tester");
        assertThat(result.getResultsByTargetRole().get(1))
                .satisfies(tester -> {
                    assertThat(tester.getJobs()).isEmpty();
                    assertThat(tester.getSearchStatus())
                            .isEqualTo("UNAVAILABLE");
                    assertThat(tester.getProviderResults())
                            .extracting(
                                    ProviderResultStatus::getProvider,
                                    ProviderResultStatus::getStatus)
                            .containsExactly(
                                    org.assertj.core.groups.Tuple.tuple(
                                            "REQUEST",
                                            "TIMED_OUT"));
                });
        verify(providerSearchCoordinator, times(1))
                .search(any(), any(JobSearchCriteria.class), anySet(), anyLong());
    }

    @Test
    void pagesOneCanonicalSnapshotWithoutDuplicatesAndEnrichesEveryPage() {
        Job duplicate = job("duplicate", "A role duplicate", "ADZUNA");
        duplicate.setUrl("https://example.com/a");
        duplicate.setSourceUrl("https://example.com/a");
        Job first = job("first", "A role", "REED");
        first.setUrl("https://example.com/a");
        first.setSourceUrl("https://example.com/a");
        Job second = job("second", "B role", "REED");
        Job third = job("third", "C role", "REED");
        when(providerSearchCoordinator.search(
                eq("user-1"), any(JobSearchCriteria.class), anySet(), anyLong()))
                .thenReturn(fanOut(
                        List.of(first, duplicate, second, third),
                        List.of(status("REED", "SUCCESS", 4)),
                        true,
                        true,
                        true));
        when(matchingEnricher.enrich(
                eq("user-1"), any(), anyLong()))
                .thenAnswer(invocation -> {
                    List<Job> jobs = invocation.getArgument(1);
                    jobs.forEach(job ->
                            job.setApplicationStatus("SAVED"));
                    return new OptionalJobMatchingEnricher.MatchingOutcome(
                            jobs,
                            "COMPLETE",
                            false);
                });
        JobSearchRequest firstPage = request("developer");
        firstPage.setPage(1);
        firstPage.setPageSize(2);
        firstPage.setSort("JOB_TITLE_AZ");
        JobSearchRequest secondPage = request("developer");
        secondPage.setPage(2);
        secondPage.setPageSize(2);
        secondPage.setSort("JOB_TITLE_AZ");

        var pageOne = service.searchJobs("user-1", firstPage);
        var pageTwo = service.searchJobs("user-1", secondPage);

        assertThat(pageOne.getTotalResults()).isEqualTo(3);
        assertThat(pageTwo.getTotalResults()).isEqualTo(3);
        assertThat(pageOne.getJobs()).hasSize(2);
        assertThat(pageTwo.getJobs()).hasSize(1);
        assertThat(pageOne.getJobs())
                .extracting(Job::getCanonicalJobId)
                .doesNotContainAnyElementsOf(
                        pageTwo.getJobs().stream()
                                .map(Job::getCanonicalJobId)
                                .toList());
        assertThat(pageOne.getJobs())
                .extracting(Job::getApplicationStatus)
                .containsOnly("SAVED");
        assertThat(pageTwo.getJobs())
                .extracting(Job::getApplicationStatus)
                .containsOnly("SAVED");
        verify(providerSearchCoordinator, times(1))
                .search(
                        eq("user-1"),
                        any(JobSearchCriteria.class),
                        anySet(),
                        anyLong());
        verify(matchingEnricher, times(2))
                .enrich(eq("user-1"), any(), anyLong());
    }

    @Test
    void returnsAnEmptyPageWithoutChangingAggregateTotals() {
        when(providerSearchCoordinator.search(
                eq("user-1"), any(JobSearchCriteria.class), anySet(), anyLong()))
                .thenReturn(fanOut(
                        List.of(job("only-job", "Only role", "REED")),
                        List.of(status("REED", "SUCCESS", 1)),
                        true,
                        true,
                        true));
        passThroughMatching();
        JobSearchRequest request = request("developer");
        request.setPage(3);
        request.setPageSize(10);

        var result = service.searchJobs("user-1", request);

        assertThat(result.getJobs()).isEmpty();
        assertThat(result.getTotalResults()).isEqualTo(1);
        assertThat(result.getTotalPages()).isEqualTo(1);
        assertThat(result.getResultsByTargetRole())
                .singleElement()
                .satisfies(group -> assertThat(group.getJobs()).isEmpty());
    }

    @Test
    void rejectsInvalidPagingAndSortBeforeCallingAProvider() {
        JobSearchRequest zeroPage = request("developer");
        zeroPage.setPage(0);
        JobSearchRequest excessivePage = request("developer");
        excessivePage.setPage(101);
        JobSearchRequest zeroSize = request("developer");
        zeroSize.setPageSize(0);
        JobSearchRequest excessiveSize = request("developer");
        excessiveSize.setPageSize(51);
        JobSearchRequest unsupportedSort = request("developer");
        unsupportedSort.setSort("RANDOM");

        assertThatThrownBy(() -> service.searchJobs("user-1", zeroPage))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("page must be between 1 and 100");
        assertThatThrownBy(() -> service.searchJobs("user-1", excessivePage))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("page must be between 1 and 100");
        assertThatThrownBy(() -> service.searchJobs("user-1", zeroSize))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("pageSize must be between 1 and 50");
        assertThatThrownBy(() -> service.searchJobs("user-1", excessiveSize))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("pageSize must be between 1 and 50");
        assertThatThrownBy(() -> service.searchJobs("user-1", unsupportedSort))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("sort must be one of");
        verifyNoInteractions(providerSearchCoordinator);
    }

    @Test
    void rejectsMoreThanTenDistinctRolesBeforeCallingAProvider() {
        JobSearchRequest request = request(
                "role-1", "role-2", "role-3", "role-4", "role-5", "role-6",
                "role-7", "role-8", "role-9", "role-10", "role-11");

        assertThatThrownBy(() -> service.searchJobs("user-1", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "aspirations.desiredRoles cannot contain more than 10 distinct roles");
        verifyNoInteractions(providerSearchCoordinator);
    }

    private void passThroughMatching() {
        when(matchingEnricher.enrich(any(), any(), anyLong()))
                .thenAnswer(invocation ->
                        new OptionalJobMatchingEnricher.MatchingOutcome(
                                invocation.getArgument(1),
                                "COMPLETE",
                                false));
    }

    private ProviderSearchCoordinator.ProviderFanOutResult fanOut(
            List<Job> jobs,
            List<ProviderResultStatus> statuses,
            boolean anyAttempted,
            boolean anySuccess,
            boolean complete) {
        return new ProviderSearchCoordinator.ProviderFanOutResult(
                jobs, statuses, anyAttempted, anySuccess, complete);
    }

    private ProviderResultStatus status(
            String provider,
            String status,
            int rawResultCount) {
        ProviderResultStatus result = new ProviderResultStatus(
                provider, status, rawResultCount, null);
        ProviderDataProvenance provenance = new ProviderDataProvenance();
        provenance.setProviderMode("FIXTURE");
        provenance.setDataOrigin("FIXTURE");
        provenance.setResultSource("PROVIDER_RESPONSE");
        provenance.setRetrievedAtUtc(java.time.OffsetDateTime.parse(
                "2026-08-12T12:00:00Z"));
        provenance.setServedAtUtc(java.time.OffsetDateTime.parse(
                "2026-08-12T12:00:00Z"));
        provenance.setCacheAgeSeconds(0L);
        result.setDataProvenance(provenance);
        return result;
    }

    private JobSearchRequest request(String... roles) {
        Aspirations aspirations = new Aspirations();
        aspirations.setDesiredRoles(List.of(roles));
        aspirations.setLocations(List.of("London"));

        JobSearchRequest request = new JobSearchRequest();
        request.setAspirations(aspirations);
        return request;
    }

    private JobSearchRequest requestWithHomeLocation(
            String role,
            double latitude,
            double longitude) {
        JobSearchRequest request = request(role);
        HomeLocation homeLocation = new HomeLocation();
        homeLocation.setLatitude(latitude);
        homeLocation.setLongitude(longitude);
        request.setHomeLocation(homeLocation);
        return request;
    }

    private Job job(String id, String title, String provider) {
        Job job = new Job();
        job.setId(id);
        job.setCanonicalJobId(id);
        job.setProvider(provider);
        job.setPrimarySource(provider);
        job.setExternalJobId(id);
        job.setTitle(title);
        job.setJobTitle(title);
        job.setCompany("Example Ltd");
        job.setCompanyName("Example Ltd");
        job.setLocation("London");
        job.setDescription("A job");
        job.setUrl("https://example.com/" + id);
        return job;
    }

    private Job jobWithCoordinates(
            String id,
            double latitude,
            double longitude) {
        Job job = job(id, "Developer", "REED");
        CanonicalLocation location = new CanonicalLocation();
        location.setDisplayName("London");
        location.setLatitude(BigDecimal.valueOf(latitude));
        location.setLongitude(BigDecimal.valueOf(longitude));
        job.setCanonicalLocation(location);
        return job;
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        void advanceSeconds(long seconds) {
            current = current.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
