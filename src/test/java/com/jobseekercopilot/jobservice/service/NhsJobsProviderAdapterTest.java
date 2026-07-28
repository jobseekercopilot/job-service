package com.jobseekercopilot.jobservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jobseekercopilot.generated.nhsjobsgateway.api.DefaultApi;
import com.jobseekercopilot.generated.nhsjobsgateway.model.CanonicalJob;
import com.jobseekercopilot.generated.nhsjobsgateway.model.NhsJobsSearchResponse;
import com.jobseekercopilot.generated.nhsjobsgateway.model.ProviderAttribution;
import com.jobseekercopilot.jobservice.model.dto.CanonicalValueStatus;
import com.jobseekercopilot.jobservice.model.dto.Job;
import com.jobseekercopilot.jobservice.model.dto.JobSourceType;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class NhsJobsProviderAdapterTest {

    @Test
    void mapsFixtureJobWithAttributionAndSafeLinks() {
        DefaultApi api = mock(DefaultApi.class);
        ProviderAttribution attribution = new ProviderAttribution()
                .label("Vacancy source: NHS Jobs")
                .sourceUrl(URI.create("https://www.jobs.nhs.uk/"))
                .licenceUrl(URI.create(
                        "https://www.nationalarchives.gov.uk/"
                                + "doc/open-government-licence/version/3/"))
                .disclaimer(
                        "NHS Jobs does not endorse Job Seeker Copilot.");
        CanonicalJob providerJob = new CanonicalJob()
                .externalJobId("C9855-FIXTURE-001")
                .reference("9855-FIXTURE-001")
                .title("Community Staff Nurse")
                .employer(
                        "Northshire Community NHS Foundation Trust")
                .description("Support a synthetic nursing team.")
                .locations(List.of("Northshire, NS1 2AB"))
                .salaryText("£31,049 to £37,796 a year")
                .contractType("Permanent")
                .postedAt("2026-07-20")
                .closesAt("2026-08-20")
                .source("NHS Jobs (synthetic fixture)")
                .sourceUrl(URI.create(
                        "https://fixtures.jobseekercopilot.test/"
                                + "nhs-jobs/jobadvert/C9855-FIXTURE-001"))
                .applicationUrl(URI.create(
                        "https://fixtures.jobseekercopilot.test/"
                                + "nhs-jobs/jobadvert/C9855-FIXTURE-001"));
        NhsJobsSearchResponse response = new NhsJobsSearchResponse()
                .provider(
                        NhsJobsSearchResponse.ProviderEnum.NHS_JOBS)
                .status(
                        NhsJobsSearchResponse.StatusEnum.AVAILABLE)
                .totalResults(1)
                .totalPages(1)
                .page(1)
                .resultsPerPage(50)
                .attribution(attribution)
                .jobs(List.of(providerJob));
        when(api.searchNhsJobs(any())).thenReturn(response);
        NhsJobsProviderAdapter adapter =
                new NhsJobsProviderAdapter(api, true, 50);

        List<Job> jobs = adapter.search(
                "user-1",
                criteria("nurse", "Northshire"));

        assertThat(jobs).singleElement().satisfies(job -> {
            assertThat(job.getProvider()).isEqualTo("NHS_JOBS");
            assertThat(job.getPrimarySource())
                    .isEqualTo("NHS_JOBS");
            assertThat(job.getSalaryText())
                    .isEqualTo("£31,049 to £37,796 a year");
            assertThat(job.getPostedAtUtc()).isEqualTo(
                    OffsetDateTime.parse(
                            "2026-07-20T00:00:00Z"));
            assertThat(job.getApplicationDeadlineAtUtc())
                    .isEqualTo(OffsetDateTime.parse(
                            "2026-08-20T00:00:00Z"));
            assertThat(job.getCanonicalLocation()
                    .getNormalisationStatus())
                    .isEqualTo(CanonicalValueStatus.RAW_ONLY);
            assertThat(job.getSources()).singleElement()
                    .satisfies(source -> {
                        assertThat(source.getPublisher())
                                .isEqualTo("NHS Jobs");
                        assertThat(source.getSourceType())
                                .isEqualTo(JobSourceType.JOB_BOARD);
                        assertThat(source.getListingUrl())
                                .startsWith("https://fixtures.");
                        assertThat(source.getAttributionLabel())
                                .isEqualTo(
                                        "Vacancy source: NHS Jobs");
                        assertThat(source.getAttributionSourceUrl())
                                .isEqualTo(
                                        "https://www.jobs.nhs.uk/");
                        assertThat(source.getLicenceUrl())
                                .contains(
                                        "open-government-licence");
                        assertThat(source.getDisclaimer())
                                .contains("does not endorse");
                    });
        });
    }

    @Test
    void returnsNoJobsWhenGatewayReportsDisabled() {
        DefaultApi api = mock(DefaultApi.class);
        NhsJobsSearchResponse response =
                new NhsJobsSearchResponse()
                        .status(
                                NhsJobsSearchResponse.StatusEnum.DISABLED)
                        .jobs(List.of());
        when(api.searchNhsJobs(any())).thenReturn(response);
        NhsJobsProviderAdapter adapter =
                new NhsJobsProviderAdapter(api, true, 50);

        JobProviderAdapter.ProviderSearchOutcome outcome =
                adapter.searchWithStatus(
                "user-1",
                criteria("nurse", "Northshire"));

        assertThat(outcome.status()).isEqualTo("DISABLED");
        assertThat(outcome.jobs()).isEmpty();
    }

    private JobSearchCriteria criteria(
            String role,
            String location) {
        return new JobSearchCriteria(
                null,
                role,
                location,
                25,
                List.of("Permanent"),
                null,
                null,
                "GBP",
                false);
    }
}
