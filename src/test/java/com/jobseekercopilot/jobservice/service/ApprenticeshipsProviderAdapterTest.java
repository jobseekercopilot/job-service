package com.jobseekercopilot.jobservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jobseekercopilot.generated.apprenticeshipsgateway.api.ApprenticeshipsApi;
import com.jobseekercopilot.generated.apprenticeshipsgateway.model.ApprenticeshipAddress;
import com.jobseekercopilot.generated.apprenticeshipsgateway.model.ApprenticeshipVacancy;
import com.jobseekercopilot.generated.apprenticeshipsgateway.model.ApprenticeshipsSearchRequest;
import com.jobseekercopilot.generated.apprenticeshipsgateway.model.ApprenticeshipsSearchResponse;
import com.jobseekercopilot.jobservice.model.dto.JobSpecialistType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ApprenticeshipsProviderAdapterTest {
    @Test
    void preservesSpecialistTrainingDataAndSelectsTheNearestAdvertisedLocation() {
        ApprenticeshipsApi api = mock(ApprenticeshipsApi.class);
        ArgumentCaptor<ApprenticeshipsSearchRequest> request = ArgumentCaptor.forClass(ApprenticeshipsSearchRequest.class);
        ApprenticeshipAddress leeds = address("Leeds Hub", "LS1 2AB", "53.8008", "-1.5491");
        ApprenticeshipAddress bradford = address("Bradford Office", "BD1 1AA", "53.7950", "-1.7594");
        ApprenticeshipVacancy source = new ApprenticeshipVacancy();
        source.setVacancyReference("VAC1000001"); source.setTitle("Software Developer Apprentice");
        source.setDescription("Preview"); source.setFullDescription("Full apprenticeship description");
        source.setEmployerName("Example Digital Ltd"); source.setProviderName("Example Training Provider");
        source.setPostedDate("2026-08-01T09:00:00Z"); source.setClosingDate("2026-09-01"); source.setStartDate("2026-10-01");
        source.setWageAmount(new BigDecimal("15000")); source.setWageUnit("Annually"); source.setHoursPerWeek(new BigDecimal("37.5"));
        source.setExpectedDuration("18 months"); source.setAddresses(List.of(leeds, bradford));
        source.setCourseTitle("Software developer (level 4)"); source.setCourseLevel(4); source.setApprenticeshipLevel("Higher");
        source.setSkills(List.of("Problem solving")); source.setQualifications(List.of("GCSE English — grade 4"));
        source.setVacancyUrl("https://www.findapprenticeship.service.gov.uk/apprenticeship/VAC1000001");
        source.setApplicationUrl("https://www.findapprenticeship.service.gov.uk/apprenticeship/VAC1000001");
        ApprenticeshipsSearchResponse response = new ApprenticeshipsSearchResponse(); response.setJobs(List.of(source));
        when(api.search(request.capture())).thenReturn(response);

        var jobRequest = new com.jobseekercopilot.jobservice.model.dto.JobSearchRequest();
        var home = new com.jobseekercopilot.jobservice.model.dto.HomeLocation();
        home.setLatitude(53.7950); home.setLongitude(-1.7594); jobRequest.setHomeLocation(home);
        var jobs = new ApprenticeshipsProviderAdapter(api, new DistanceCalculationService(), true, 50)
                .search("user", new JobSearchCriteria(jobRequest, "developer", "Bradford", 20, List.of(), null, null, "GBP", false));

        assertThat(request.getValue().getLatitude()).isEqualByComparingTo("53.795");
        assertThat(jobs).singleElement().satisfies(job -> {
            assertThat(job.getSpecialistType()).isEqualTo(JobSpecialistType.APPRENTICESHIP);
            assertThat(job.getLocations()).hasSize(2);
            assertThat(job.getCanonicalLocation().getPostcode()).isEqualTo("BD1 1AA");
            assertThat(job.getDescription()).isEqualTo("Full apprenticeship description");
            assertThat(job.getApprenticeshipDetails().getCourseTitle()).isEqualTo("Software developer (level 4)");
            assertThat(job.getApprenticeshipDetails().getTrainingProvider()).isEqualTo("Example Training Provider");
            assertThat(job.getSkills()).extracting("name").containsExactly("Problem solving");
        });
    }

    private ApprenticeshipAddress address(String line, String postcode, String latitude, String longitude) {
        ApprenticeshipAddress result = new ApprenticeshipAddress(); result.setAddressLine1(line); result.setPostcode(postcode);
        result.setLatitude(new BigDecimal(latitude)); result.setLongitude(new BigDecimal(longitude)); return result;
    }
}
