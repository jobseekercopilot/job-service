package com.jobseekercopilot.jobservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobseekercopilot.jobservice.model.dto.CanonicalLocation;
import com.jobseekercopilot.jobservice.model.dto.Job;
import com.jobseekercopilot.jobservice.model.dto.WorkplaceTypeCode;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class JobDeduplicationServiceDistanceTest {
    private final JobDeduplicationService service = new JobDeduplicationService(true, 7, 0.90, 0.80);

    @Test
    void mergedDuplicateKeepsBestAvailableCoordinates() {
        Job primary = job("primary");
        primary.setCanonicalLocation(location(null, null));
        Job duplicate = job("duplicate");
        duplicate.setCanonicalLocation(location(51.5074, -0.1278));

        Job merged = service.mergeDuplicateJobs(primary, duplicate);

        assertThat(merged.getCanonicalLocation().getLatitude()).isEqualByComparingTo("51.5074");
        assertThat(merged.getCanonicalLocation().getLongitude()).isEqualByComparingTo("-0.1278");
    }

    @Test
    void mergedDuplicateKeepsExplicitWorkplaceType() {
        Job primary = job("primary");
        primary.setWorkplaceType(WorkplaceTypeCode.UNKNOWN);
        Job duplicate = job("duplicate");
        duplicate.setWorkplaceType(WorkplaceTypeCode.HYBRID);

        Job merged = service.mergeDuplicateJobs(primary, duplicate);

        assertThat(merged.getWorkplaceType()).isEqualTo(WorkplaceTypeCode.HYBRID);
    }

    private Job job(String id) {
        Job job = new Job();
        job.setId(id);
        job.setTitle("Developer");
        job.setCompanyName("Example Ltd");
        job.setLocation("London");
        job.setPostedDate("2026-07-01");
        job.setDescription("Build useful software.");
        job.setUrl("https://example.com/" + id);
        return job;
    }

    private CanonicalLocation location(Double latitude, Double longitude) {
        CanonicalLocation location = new CanonicalLocation();
        location.setDisplayName("London");
        if (latitude != null) {
            location.setLatitude(BigDecimal.valueOf(latitude));
        }
        if (longitude != null) {
            location.setLongitude(BigDecimal.valueOf(longitude));
        }
        return location;
    }
}
