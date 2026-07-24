package com.jobseekercopilot.jobservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jobseekercopilot.generated.jsearchgateway.api.JSearchJobsApi;
import com.jobseekercopilot.generated.jsearchgateway.model.JSearchApplyOption;
import com.jobseekercopilot.generated.jsearchgateway.model.JSearchJob;
import com.jobseekercopilot.generated.jsearchgateway.model.JSearchSearchRequest;
import com.jobseekercopilot.generated.jsearchgateway.model.JSearchSearchResponse;
import com.jobseekercopilot.jobservice.model.dto.Aspirations;
import com.jobseekercopilot.jobservice.model.dto.Job;
import com.jobseekercopilot.jobservice.model.dto.JobSearchRequest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class JSearchJobProviderAdapterTest {

    @Test
    void usesNonPostcodeProfileLocationWhenPrimaryLocationIsPostcode() {
        JSearchJobsApi jSearchJobsApi = mock(JSearchJobsApi.class);
        JSearchSearchResponse response = new JSearchSearchResponse();
        response.setJobs(List.of());
        ArgumentCaptor<JSearchSearchRequest> requestCaptor = ArgumentCaptor.forClass(JSearchSearchRequest.class);
        when(jSearchJobsApi.search(requestCaptor.capture())).thenReturn(response);

        JSearchJobProviderAdapter adapter = new JSearchJobProviderAdapter(
                jSearchJobsApi,
                new PublisherNormalisationService(),
                true);

        adapter.search("user-1", criteria(List.of("UB3 4QZ", "Hillingdon, London")));

        assertThat(requestCaptor.getValue().getLocation()).isEqualTo("Hillingdon, London");
    }

    @Test
    void mapsTheGeneratedProviderResponseToCanonicalJobs() {
        JSearchJobsApi jSearchJobsApi = mock(JSearchJobsApi.class);
        JSearchApplyOption directOption = new JSearchApplyOption();
        directOption.setPublisher("Employer");
        directOption.setApplyUrl("https://example.test/apply");
        directOption.setDirect(true);

        JSearchJob providerJob = new JSearchJob();
        providerJob.setExternalJobId("jsearch-42");
        providerJob.setTitle("Software Developer");
        providerJob.setCompanyName("Example Ltd");
        providerJob.setPublisher("JSearch");
        providerJob.setLocationDisplayName("London");
        providerJob.setCity("London");
        providerJob.setCountry("UK");
        providerJob.setSalaryMinimum(50_000);
        providerJob.setSalaryMaximum(60_000);
        providerJob.setSalaryCurrency("GBP");
        providerJob.setSalaryPeriod("YEAR");
        providerJob.setLatitude(new BigDecimal("51.5074"));
        providerJob.setLongitude(new BigDecimal("-0.1278"));
        providerJob.setPostedAt("2026-07-24T09:00:00Z");
        providerJob.setPrimaryApplyUrl("https://example.test/listing");
        providerJob.setApplyOptions(List.of(directOption));

        JSearchSearchResponse response = new JSearchSearchResponse();
        response.setJobs(List.of(providerJob));
        when(jSearchJobsApi.search(org.mockito.ArgumentMatchers.any(JSearchSearchRequest.class)))
                .thenReturn(response);

        JSearchJobProviderAdapter adapter = new JSearchJobProviderAdapter(
                jSearchJobsApi,
                new PublisherNormalisationService(),
                true);

        List<Job> jobs = adapter.search("user-1", criteria(List.of("London")));

        assertThat(jobs).singleElement().satisfies(job -> {
            assertThat(job.getExternalJobId()).isEqualTo("jsearch-42");
            assertThat(job.getCompanyName()).isEqualTo("Example Ltd");
            assertThat(job.getCanonicalLocation().getLatitude()).isEqualByComparingTo("51.5074");
            assertThat(job.getSalary().getMin()).isEqualTo(50_000);
            assertThat(job.getSourceUrl()).isEqualTo("https://example.test/apply");
            assertThat(job.getSources()).singleElement().satisfies(source -> {
                assertThat(source.getPublisher()).isEqualTo("Employer Site");
                assertThat(source.getDirectApply()).isTrue();
            });
        });
    }

    private JobSearchCriteria criteria(List<String> locations) {
        Aspirations aspirations = new Aspirations();
        aspirations.setDesiredRoles(List.of("Software Developer"));
        aspirations.setLocations(locations);

        JobSearchRequest request = new JobSearchRequest();
        request.setAspirations(aspirations);

        return new JobSearchCriteria(
                request,
                "Software Developer",
                locations.get(0),
                25,
                List.of("FULL_TIME"),
                null,
                null,
                "GBP",
                false);
    }
}
