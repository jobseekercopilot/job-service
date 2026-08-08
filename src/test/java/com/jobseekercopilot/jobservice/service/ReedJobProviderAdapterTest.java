package com.jobseekercopilot.jobservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jobseekercopilot.generated.reedgateway.api.ReedJobsApi;
import com.jobseekercopilot.generated.reedgateway.model.ExternalJob;
import com.jobseekercopilot.generated.reedgateway.model.ExternalSalary;
import com.jobseekercopilot.generated.reedgateway.model.ExternalSearchResponse;
import com.jobseekercopilot.jobservice.model.dto.AdvertiserType;
import com.jobseekercopilot.jobservice.model.dto.CanonicalValueStatus;
import com.jobseekercopilot.jobservice.model.dto.Job;
import com.jobseekercopilot.jobservice.model.dto.JobDescriptionCompleteness;
import com.jobseekercopilot.jobservice.model.dto.JobSourceType;
import com.jobseekercopilot.jobservice.model.dto.SalaryPeriodCode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReedJobProviderAdapterTest {

    @Test
    void hydratesASelectedJobWithTheCompleteProviderDescription() {
        ReedJobsApi reedJobsApi = mock(ReedJobsApi.class);
        ExternalJob details = new ExternalJob();
        details.setId("reed-42");
        details.setTitle("Software Developer");
        details.setCompany("Example Ltd");
        details.setDescription("Complete provider job description");
        when(reedJobsApi.jobDetails("reed-42", "user-1"))
                .thenReturn(details);
        ReedJobProviderAdapter adapter = new ReedJobProviderAdapter(
                reedJobsApi,
                new PublisherNormalisationService(),
                true,
                1,
                25);

        Optional<Job> job = adapter.details("user-1", "reed-42");

        assertThat(job).hasValueSatisfying(value -> {
            assertThat(value.getDescription())
                    .isEqualTo("Complete provider job description");
            assertThat(value.getDescriptionCompleteness())
                    .isEqualTo(JobDescriptionCompleteness.FULL);
            assertThat(value.getFieldProvenance())
                    .extracting("fieldName")
                    .contains("description");
        });
    }

    @Test
    void returnsEmptyJobsForHealthyZeroResultGatewayResponse() {
        ReedJobsApi reedJobsApi = mock(ReedJobsApi.class);
        ExternalSearchResponse response = new ExternalSearchResponse();
        response.setJobs(List.of());
        response.setTotalResults(0);
        when(reedJobsApi.externalSearch(any(), eq("user-1")))
                .thenReturn(response);
        ReedJobProviderAdapter adapter = new ReedJobProviderAdapter(
                reedJobsApi,
                new PublisherNormalisationService(),
                true,
                1,
                25);

        List<Job> jobs = adapter.search(
                "user-1",
                new JobSearchCriteria(
                        null,
                        "Software Developer",
                        "London",
                        25,
                        List.of("FULL_TIME"),
                        null,
                        null,
                        "GBP",
                        false));

        assertThat(jobs).isEmpty();
    }

    @Test
    void mapsPinnedProviderFieldsIntoVersionedCanonicalEvidence() {
        ReedJobsApi reedJobsApi = mock(ReedJobsApi.class);
        ExternalSalary providerSalary = new ExternalSalary();
        providerSalary.setMin(40_000);
        providerSalary.setMax(50_000);
        providerSalary.setCurrency("GBP");
        ExternalJob providerJob = new ExternalJob();
        providerJob.setId("reed-42");
        providerJob.setTitle("Software Developer");
        providerJob.setCompany("Example Ltd");
        providerJob.setLocation("London");
        providerJob.setSalary(providerSalary);
        providerJob.setEmploymentType("Permanent");
        providerJob.setPostedDate("2026-07-24T09:00:00Z");
        providerJob.setDescription("Build useful software");
        providerJob.setUrl("https://example.test/reed-42");
        ExternalSearchResponse response = new ExternalSearchResponse();
        response.setJobs(List.of(providerJob));
        when(reedJobsApi.externalSearch(any(), eq("user-1")))
                .thenReturn(response);
        ReedJobProviderAdapter adapter = new ReedJobProviderAdapter(
                reedJobsApi,
                new PublisherNormalisationService(),
                true,
                1,
                25);

        List<Job> jobs = adapter.search(
                "user-1",
                new JobSearchCriteria(
                        null,
                        "Software Developer",
                        "London",
                        25,
                        List.of("FULL_TIME"),
                        null,
                        null,
                        "GBP",
                        false));

        assertThat(jobs).singleElement().satisfies(job -> {
            assertThat(job.getCanonicalSchemaVersion()).isEqualTo("2.0");
            assertThat(job.getAdvertiserName()).isEqualTo("Example Ltd");
            assertThat(job.getAdvertiserType()).isEqualTo(AdvertiserType.UNKNOWN);
            assertThat(job.getDescriptionCompleteness())
                    .isEqualTo(JobDescriptionCompleteness.PREVIEW);
            assertThat(job.getPostedAtUtc()).isEqualTo(
                    OffsetDateTime.parse("2026-07-24T09:00:00Z"));
            assertThat(job.getCanonicalLocation().getRawDisplayName())
                    .isEqualTo("London");
            assertThat(job.getCanonicalLocation().getNormalisationStatus())
                    .isEqualTo(CanonicalValueStatus.RAW_ONLY);
            assertThat(job.getSalary().getRawMinimum())
                    .isEqualByComparingTo("40000");
            assertThat(job.getSalary().getPeriodCode())
                    .isEqualTo(SalaryPeriodCode.YEAR);
            assertThat(job.getSalary().getSourceProvider())
                    .isEqualTo("REED");
            assertThat(job.getSources()).singleElement().satisfies(source -> {
                assertThat(source.getRawPublisher())
                        .isEqualTo("Reed.co.uk");
                assertThat(source.getSourceType())
                        .isEqualTo(JobSourceType.JOB_BOARD);
                assertThat(source.getProviderPostedAtRaw())
                        .isEqualTo("2026-07-24T09:00:00Z");
                assertThat(source.getProviderPostedAtUtc())
                        .isEqualTo(job.getPostedAtUtc());
            });
            assertThat(job.getFieldProvenance())
                    .extracting("fieldName", "status")
                    .contains(
                            org.assertj.core.groups.Tuple.tuple(
                                    "employmentType",
                                    CanonicalValueStatus.RAW_ONLY),
                            org.assertj.core.groups.Tuple.tuple(
                                    "postedAt",
                                    CanonicalValueStatus.NORMALISED));
        });
    }
}
