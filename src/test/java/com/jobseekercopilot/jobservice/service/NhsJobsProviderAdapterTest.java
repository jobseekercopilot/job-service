package com.jobseekercopilot.jobservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jobseekercopilot.generated.nhsjobsgateway.api.NhsJobsApi;
import com.jobseekercopilot.generated.nhsjobsgateway.model.NhsJob;
import com.jobseekercopilot.generated.nhsjobsgateway.model.NhsJobsSearchRequest;
import com.jobseekercopilot.generated.nhsjobsgateway.model.NhsJobsSearchResponse;
import com.jobseekercopilot.jobservice.model.dto.JobSourceType;
import com.jobseekercopilot.jobservice.model.dto.JobSpecialistType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NhsJobsProviderAdapterTest {
    @Test
    void mapsOfficialNhsFieldsAndWorkingPatternPreferences() {
        NhsJobsApi api = mock(NhsJobsApi.class);
        ArgumentCaptor<NhsJobsSearchRequest> request = ArgumentCaptor.forClass(NhsJobsSearchRequest.class);
        NhsJob source = new NhsJob();
        source.setExternalJobId("123"); source.setReference("C123"); source.setTitle("Staff Nurse");
        source.setEmployer("Example NHS Trust"); source.setDescription("Patient care");
        source.setLocations(List.of("Leeds, LS1 1AA", "Bradford, BD1 1AA"));
        source.setSalaryMinimum(new BigDecimal("30000")); source.setSalaryMaximum(new BigDecimal("36000"));
        source.setSalaryCurrency("GBP"); source.setSalaryPeriod("YEAR"); source.setContractType("Permanent");
        source.setPostedAt("2026-08-01T09:00:00Z"); source.setClosingDate("2026-08-31");
        source.setSourceUrl("https://www.jobs.nhs.uk/candidate/jobadvert/C123");
        NhsJobsSearchResponse response = new NhsJobsSearchResponse(); response.setJobs(List.of(source));
        when(api.search(request.capture())).thenReturn(response);

        var jobs = new NhsJobsProviderAdapter(api, true, 50).search("user", new JobSearchCriteria(
                null, "nurse", "Leeds", 25, List.of("FULL_TIME", "CONTRACT"), null, null, "GBP", false));

        assertThat(request.getValue().getWorkingPatterns()).containsExactly("Full time");
        assertThat(request.getValue().getContractTypes()).isNullOrEmpty();
        assertThat(jobs).singleElement().satisfies(job -> {
            assertThat(job.getSpecialistType()).isEqualTo(JobSpecialistType.NHS);
            assertThat(job.getLocations()).hasSize(2);
            assertThat(job.getCanonicalLocation().getPostcode()).isEqualTo("LS1 1AA");
            assertThat(job.getSources()).singleElement().satisfies(sourceReference -> {
                assertThat(sourceReference.getSourceType()).isEqualTo(JobSourceType.OFFICIAL_PROVIDER);
                assertThat(sourceReference.getPublisher()).isEqualTo("NHS Jobs");
            });
        });
    }
}
