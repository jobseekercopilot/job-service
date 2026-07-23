package com.jobseekercopilot.jobservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobseekercopilot.jobservice.model.dto.Aspirations;
import com.jobseekercopilot.jobservice.model.dto.Job;
import com.jobseekercopilot.jobservice.model.dto.JobSearchRequest;
import com.jobseekercopilot.jobservice.model.dto.WorkPreferences;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobResultEnrichmentServiceTest {
    private final JobResultEnrichmentService service = new JobResultEnrichmentService(new SalaryNormalisationService());

    @Test
    void preservesExistingDistanceWhenEnrichingOtherFields() {
        Job job = new Job();
        job.setDistanceMiles(3.25);

        service.enrich(criteria(), List.of(job));

        assertThat(job.getDistanceMiles()).isEqualTo(3.25);
    }

    private JobSearchCriteria criteria() {
        WorkPreferences workPreferences = new WorkPreferences();

        Aspirations aspirations = new Aspirations();
        aspirations.setDesiredRoles(List.of("Developer"));
        aspirations.setLocations(List.of("London"));

        JobSearchRequest request = new JobSearchRequest();
        request.setAspirations(aspirations);
        request.setWorkPreferences(workPreferences);

        return new JobSearchCriteria(request, "Developer", "London", 25, List.of(), null, null, "GBP", false);
    }
}
