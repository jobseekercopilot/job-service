package com.jobseekercopilot.jobservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobseekercopilot.jobservice.model.dto.Job;
import com.jobseekercopilot.jobservice.model.dto.JobSourceReference;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobDiscoveryClassifierTest {
    private final JobDiscoveryClassifier classifier = new JobDiscoveryClassifier(
            Clock.fixed(Instant.parse("2026-08-12T12:00:00Z"), ZoneOffset.UTC));

    @Test
    void removesKnownExpiredVacanciesAndExplainsTheCount() {
        Job expired = job("Junior Software Engineer", "Build Java services");
        expired.setExpiresAtUtc(OffsetDateTime.parse("2026-08-11T23:59:59Z"));

        var result = classifier.classifyAndFilter(
                "Junior Software Engineer", List.of(expired));

        assertThat(result.jobs()).isEmpty();
        assertThat(result.summary().getExcludedExpiredCount()).isOne();
        assertThat(expired.getDiscoveryAssessment().getExclusionReasons())
                .containsExactly("KNOWN_EXPIRED_OR_CLOSED");
    }

    @Test
    void usesSourceExpiryWhenCanonicalExpiryIsMissing() {
        Job expired = job("Software Developer", "Build software");
        JobSourceReference source = new JobSourceReference();
        source.setProviderExpiresAtUtc(
                OffsetDateTime.parse("2026-08-01T00:00:00Z"));
        expired.setSources(List.of(source));

        var result = classifier.classifyAndFilter(
                "Software Developer", List.of(expired));

        assertThat(result.jobs()).isEmpty();
        assertThat(result.summary().getExcludedExpiredCount()).isOne();
    }

    @Test
    void removesFeeBasedTrainingButDoesNotMistakeEmployerTrainingForAJobFee() {
        Job feeScheme = job(
                "Software Developer Career Programme",
                "A training provider offers a course fee with finance available and job placement support.");
        Job realVacancy = job(
                "Junior Software Developer",
                "Permanent salaried vacancy. Full training is provided by the employer.");

        var result = classifier.classifyAndFilter(
                "Junior Software Developer", List.of(feeScheme, realVacancy));

        assertThat(result.jobs()).containsExactly(realVacancy);
        assertThat(result.summary().getExcludedPaidTrainingCount()).isOne();
        assertThat(realVacancy.getDiscoveryAssessment().getEngagementType())
                .isEqualTo("VACANCY");
    }

    @Test
    void removesHighConfidenceOccupationMismatchButKeepsSeniorRolesVisible() {
        Job retail = job("Retail Sales Assistant", "Serve shop customers");
        Job senior = job("Senior Software Engineer", "7 years experience building Java software");

        var result = classifier.classifyAndFilter(
                "Junior Software Engineer", List.of(retail, senior));

        assertThat(result.jobs()).containsExactly(senior);
        assertThat(result.summary().getExcludedOccupationMismatchCount()).isOne();
        assertThat(senior.getDiscoveryAssessment().getSeniority())
                .isEqualTo("SENIOR");
        assertThat(senior.getDiscoveryAssessment().isExcluded()).isFalse();
        assertThat(senior.getExperience().getMinimumYears())
                .isEqualByComparingTo("7");
    }

    private Job job(String title, String description) {
        Job job = new Job();
        job.setTitle(title);
        job.setJobTitle(title);
        job.setDescription(description);
        job.setPrimarySource("FIXTURE");
        return job;
    }
}
