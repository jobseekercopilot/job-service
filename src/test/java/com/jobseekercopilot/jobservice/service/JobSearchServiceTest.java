package com.jobseekercopilot.jobservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobseekercopilot.jobservice.config.JobSearchResilienceProperties;
import com.jobseekercopilot.jobservice.model.dto.Aspirations;
import com.jobseekercopilot.jobservice.model.dto.CanonicalLocation;
import com.jobseekercopilot.jobservice.model.dto.HomeLocation;
import com.jobseekercopilot.jobservice.model.dto.Job;
import com.jobseekercopilot.jobservice.model.dto.JobSearchRequest;
import com.jobseekercopilot.jobservice.model.dto.JobSkill;
import com.jobseekercopilot.jobservice.model.dto.JobSkillType;
import com.jobseekercopilot.jobservice.model.dto.ProviderResultStatus;
import java.math.BigDecimal;
import java.util.List;
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
        return new ProviderResultStatus(provider, status, rawResultCount, null);
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
}
