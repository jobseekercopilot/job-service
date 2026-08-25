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

    @Test
    void removesReportedClinicalTitlesFromSoftwareResults() {
        List<Job> unrelated = List.of(
                job("Psychological Wellbeing Practitioner (PWP)", "Deliver psychological interventions."),
                job("Speech and Language Therapy Assistant", "Support a clinical team."),
                job("Locum Consultant Haematologist", "Provide haematology care."),
                job("Speech and Language Therapist", "Provide patient therapy."),
                job("Health Visitor", "Deliver community health services."));
        Job relevant = job("Software Developer", "Build Java services.");

        var result = classifier.classifyAndFilter(
                "Software Developer", java.util.stream.Stream.concat(
                                unrelated.stream(), java.util.stream.Stream.of(relevant))
                        .toList());

        assertThat(result.jobs()).containsExactly(relevant);
        assertThat(result.summary().getExcludedOccupationMismatchCount())
                .isEqualTo(unrelated.size());
        assertThat(unrelated)
                .allSatisfy(job -> {
                    assertThat(job.getDiscoveryAssessment().getOccupationFamily())
                            .isEqualTo("NON_TECHNICAL");
                    assertThat(job.getDiscoveryAssessment().getTargetRoleAlignment())
                            .isEqualTo("MISMATCHED");
                });

        var healthcareTarget = classifier.classifyAndFilter(
                "Speech and Language Therapist",
                List.of(job("Speech and Language Therapy Assistant", "Support a clinical team.")));
        assertThat(healthcareTarget.jobs()).singleElement()
                .satisfies(job -> assertThat(job.getDiscoveryAssessment().getTargetRoleAlignment())
                        .isEqualTo("ALIGNED"));
    }

    @Test
    void conservativelySeparatesAdministrativeFinanceAndHealthcareWork() {
        Job software = job("Software Developer", "Build Java services");
        Job nurse = job("Community Staff Nurse", "Provide community nursing care");

        var administrative = classifier.classifyAndFilter(
                "Administrative Assistant", List.of(software, nurse));
        var finance = classifier.classifyAndFilter(
                "Accounts Assistant", List.of(software, nurse));
        var payroll = classifier.classifyAndFilter(
                "Payroll Administrator", List.of(software, nurse));

        assertThat(administrative.jobs()).isEmpty();
        assertThat(administrative.summary().getExcludedOccupationMismatchCount())
                .isEqualTo(2);
        assertThat(finance.jobs()).isEmpty();
        assertThat(finance.summary().getExcludedOccupationMismatchCount())
                .isEqualTo(2);
        assertThat(payroll.jobs()).isEmpty();
        assertThat(payroll.summary().getExcludedOccupationMismatchCount())
                .isEqualTo(2);
    }

    @Test
    void keepsKnownNonTechnicalDisciplinesAlignedWithoutGroupingThemTogether() {
        Job nurse = job("Community Staff Nurse", "Provide community nursing care");
        Job teacher = job("Secondary School Teacher", "Teach history classes");

        var nursing = classifier.classifyAndFilter(
                "Registered Nurse", List.of(nurse, teacher));

        assertThat(nursing.jobs()).containsExactly(nurse);
        assertThat(nursing.summary().getExcludedOccupationMismatchCount()).isOne();
        assertThat(nurse.getDiscoveryAssessment().getTargetRoleAlignment())
                .isEqualTo("ALIGNED");
    }

    @Test
    void keepsAlignedAdministrativeAndFinanceVacancies() {
        Job administrator = job("Administrative Assistant", "Support office administration");
        Job payroll = job("Payroll Administrator", "Process payroll records");

        var administrative = classifier.classifyAndFilter(
                "Administrative Assistant", List.of(administrator, payroll));
        assertThat(administrative.jobs()).containsExactly(administrator);
        assertThat(administrator.getDiscoveryAssessment().getTargetRoleAlignment())
                .isEqualTo("ALIGNED");

        var finance = classifier.classifyAndFilter(
                "Accounts Assistant", List.of(administrator, payroll));
        assertThat(finance.jobs()).containsExactly(payroll);
        assertThat(payroll.getDiscoveryAssessment().getTargetRoleAlignment())
                .isEqualTo("ALIGNED");
    }

    @Test
    void recognisesProgrammeSupportAsProjectWork() {
        Job software = job("Software Developer", "Build Java services");

        var result = classifier.classifyAndFilter(
                "Programme Support Officer", List.of(software));

        assertThat(result.jobs()).isEmpty();
        assertThat(result.summary().getExcludedOccupationMismatchCount()).isOne();
        assertThat(software.getDiscoveryAssessment().getTargetRoleAlignment())
                .isEqualTo("MISMATCHED");
        assertThat(software.getDiscoveryAssessment().getAlgorithmVersion())
                .isEqualTo("DISCOVERY_RULES_V3");
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
