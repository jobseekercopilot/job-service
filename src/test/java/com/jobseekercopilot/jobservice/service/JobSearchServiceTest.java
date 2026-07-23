package com.jobseekercopilot.jobservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobseekercopilot.jobservice.model.dto.Aspirations;
import com.jobseekercopilot.jobservice.model.dto.CanonicalLocation;
import com.jobseekercopilot.jobservice.model.dto.HomeLocation;
import com.jobseekercopilot.jobservice.model.dto.Job;
import com.jobseekercopilot.jobservice.model.dto.JobSearchRequest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobSearchServiceTest {

    private final JobProviderAdapter reedAdapter = mock(JobProviderAdapter.class);
    private final JobProviderAdapter adzunaAdapter = mock(JobProviderAdapter.class);
    private final JobMatchingClient jobMatchingClient = mock(JobMatchingClient.class);
    private final JobDeduplicationService deduplicationService = new JobDeduplicationService(true, 7, 0.90, 0.80);
    private final JobResultEnrichmentService jobResultEnrichmentService = new JobResultEnrichmentService(new SalaryNormalisationService());
    private final DistanceCalculationService distanceCalculationService = new DistanceCalculationService();
    private final JobSearchService service = new JobSearchService(
            List.of(reedAdapter, adzunaAdapter),
            deduplicationService,
            jobResultEnrichmentService,
            distanceCalculationService,
            jobMatchingClient,
            10);

    @Test
    void searchesEnabledProvidersAndReturnsProviderStatuses() {
        when(reedAdapter.provider()).thenReturn("REED");
        when(reedAdapter.isEnabled()).thenReturn(true);
        when(reedAdapter.search(eq("user-1"), org.mockito.ArgumentMatchers.any(JobSearchCriteria.class)))
                .thenReturn(List.of(job("reed-1", "Cleaning Operative", "REED")));

        when(adzunaAdapter.provider()).thenReturn("ADZUNA");
        when(adzunaAdapter.isEnabled()).thenReturn(true);
        when(adzunaAdapter.search(eq("user-1"), org.mockito.ArgumentMatchers.any(JobSearchCriteria.class)))
                .thenThrow(new RuntimeException("downstream unavailable"));

        when(jobMatchingClient.enrichJobs(eq("user-1"), anyList()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        var result = service.searchJobs("user-1", request("cleaning"));

        assertThat(result.getJobs()).hasSize(1);
        assertThat(result.getProviderResults())
                .extracting("provider", "status", "rawResultCount")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("REED", "SUCCESS", 1),
                        org.assertj.core.groups.Tuple.tuple("ADZUNA", "UNAVAILABLE", 0));
        verify(jobMatchingClient).enrichJobs(eq("user-1"), anyList());
    }

    @Test
    void addsDistanceBeforeJobMatchingEnrichment() {
        when(reedAdapter.provider()).thenReturn("REED");
        when(reedAdapter.isEnabled()).thenReturn(true);
        when(reedAdapter.search(eq("user-1"), org.mockito.ArgumentMatchers.any(JobSearchCriteria.class)))
                .thenReturn(List.of(jobWithCoordinates("reed-1", 51.5074, -0.1278)));

        when(adzunaAdapter.provider()).thenReturn("ADZUNA");
        when(adzunaAdapter.isEnabled()).thenReturn(false);

        when(jobMatchingClient.enrichJobs(eq("user-1"), anyList()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        service.searchJobs("user-1", requestWithHomeLocation("developer", 51.5010, -0.1416));

        verify(jobMatchingClient).enrichJobs(eq("user-1"), argThat(jobs ->
                jobs.size() == 1
                        && jobs.get(0).getDistanceMiles() != null
                        && jobs.get(0).getDistanceMiles() < 1.0));
    }

    private JobSearchRequest request(String... roles) {
        var aspirations = new Aspirations();
        aspirations.setDesiredRoles(List.of(roles));
        aspirations.setLocations(List.of("London"));

        var request = new JobSearchRequest();
        request.setAspirations(aspirations);
        return request;
    }

    private JobSearchRequest requestWithHomeLocation(String role, double latitude, double longitude) {
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

    private Job jobWithCoordinates(String id, double latitude, double longitude) {
        Job job = job(id, "Developer", "REED");
        CanonicalLocation location = new CanonicalLocation();
        location.setDisplayName("London");
        location.setLatitude(BigDecimal.valueOf(latitude));
        location.setLongitude(BigDecimal.valueOf(longitude));
        job.setCanonicalLocation(location);
        return job;
    }
}
