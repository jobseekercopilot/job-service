package com.jobseekercopilot.jobservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jobseekercopilot.generated.adzunagateway.api.AdzunaJobsApi;
import com.jobseekercopilot.generated.adzunagateway.model.AdzunaJob;
import com.jobseekercopilot.generated.adzunagateway.model.AdzunaSearchRequest;
import com.jobseekercopilot.generated.adzunagateway.model.AdzunaSearchResponse;
import com.jobseekercopilot.jobservice.model.dto.CanonicalValueStatus;
import com.jobseekercopilot.jobservice.model.dto.Job;
import com.jobseekercopilot.jobservice.model.dto.JobSourceType;
import com.jobseekercopilot.jobservice.model.dto.SalaryPeriodCode;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AdzunaJobProviderAdapterTest {

    @Test
    void usesTheGeneratedContractAndPreservesCanonicalMapping() {
        AdzunaJobsApi adzunaJobsApi = mock(AdzunaJobsApi.class);
        ArgumentCaptor<AdzunaSearchRequest> requestCaptor =
                ArgumentCaptor.forClass(AdzunaSearchRequest.class);

        AdzunaJob providerJob = new AdzunaJob();
        providerJob.setExternalJobId("adzuna-42");
        providerJob.setTitle("Software Developer");
        providerJob.setCompanyName("Example Ltd");
        providerJob.setLocationDisplayName("Manchester");
        providerJob.setLocationAreas(List.of("UK", "North West", "Manchester"));
        providerJob.setSalaryMinimum(45_000);
        providerJob.setSalaryMaximum(55_000);
        providerJob.setSalaryPredicted(true);
        providerJob.setLatitude(new BigDecimal("53.4808"));
        providerJob.setLongitude(new BigDecimal("-2.2426"));
        providerJob.setPostedAt("2026-07-24T09:00:00Z");
        providerJob.setRedirectUrl("https://example.test/adzuna-42");

        AdzunaSearchResponse response = new AdzunaSearchResponse();
        response.setJobs(List.of(providerJob));
        when(adzunaJobsApi.search(requestCaptor.capture())).thenReturn(response);

        AdzunaJobProviderAdapter adapter = new AdzunaJobProviderAdapter(
                adzunaJobsApi,
                new PublisherNormalisationService(),
                true,
                25);

        List<Job> jobs = adapter.search(
                "user-1",
                new JobSearchCriteria(
                        null,
                        "Software Developer",
                        "Manchester",
                        30,
                        List.of("FULL_TIME"),
                        null,
                        null,
                        "GBP",
                        false));

        assertThat(requestCaptor.getValue()).satisfies(request -> {
            assertThat(request.getTargetRole()).isEqualTo("Software Developer");
            assertThat(request.getLocation()).isEqualTo("Manchester");
            assertThat(request.getDistanceMiles()).isEqualTo(30);
            assertThat(request.getPage()).isEqualTo(1);
            assertThat(request.getResultsPerPage()).isEqualTo(25);
        });
        assertThat(jobs).singleElement().satisfies(job -> {
            assertThat(job.getExternalJobId()).isEqualTo("adzuna-42");
            assertThat(job.getCanonicalSchemaVersion()).isEqualTo("2.0");
            assertThat(job.getCompanyName()).isEqualTo("Example Ltd");
            assertThat(job.getCanonicalLocation().getLatitude()).isEqualByComparingTo("53.4808");
            assertThat(job.getCanonicalLocation().getRawDisplayName())
                    .isEqualTo("Manchester");
            assertThat(job.getCanonicalLocation().getNormalisationStatus())
                    .isEqualTo(CanonicalValueStatus.RAW_ONLY);
            assertThat(job.getSalary().getMax()).isEqualTo(55_000);
            assertThat(job.getSalary().getRawMaximum())
                    .isEqualByComparingTo("55000");
            assertThat(job.getSalary().getPeriodCode())
                    .isEqualTo(SalaryPeriodCode.YEAR);
            assertThat(job.getSalary().getPredicted()).isTrue();
            assertThat(job.getPostedAtUtc()).isEqualTo(
                    java.time.OffsetDateTime.parse(
                            "2026-07-24T09:00:00Z"));
            assertThat(job.getSourceUrl()).isEqualTo("https://example.test/adzuna-42");
            assertThat(job.getSources()).singleElement().satisfies(source -> {
                assertThat(source.getProvider()).isEqualTo("ADZUNA");
                assertThat(source.getPublisher()).isEqualTo("Adzuna");
                assertThat(source.getRawPublisher()).isEqualTo("Adzuna");
                assertThat(source.getSourceType())
                        .isEqualTo(JobSourceType.AGGREGATOR);
                assertThat(source.getProviderPostedAtUtc())
                        .isEqualTo(job.getPostedAtUtc());
            });
            assertThat(job.getFieldProvenance())
                    .extracting("fieldName")
                    .contains("employmentType", "contractType", "postedAt");
        });
    }
}
