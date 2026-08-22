package com.jobseekercopilot.jobservice.model.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class CanonicalJobModelTest {

    private final ObjectMapper objectMapper =
            new ObjectMapper()
                    .findAndRegisterModules()
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void minimalModelSerializesExplicitUnknownsAndEmptyCollections()
            throws Exception {
        Job job = new Job();

        ObjectNode json = (ObjectNode) objectMapper.readTree(
                objectMapper.writeValueAsString(job));

        assertThat(json.get("canonicalSchemaVersion").asText())
                .isEqualTo("2.0");
        assertThat(json.get("employmentTypeCode").asText())
                .isEqualTo("UNKNOWN");
        assertThat(json.get("contractTypeCode").asText())
                .isEqualTo("UNKNOWN");
        assertThat(json.get("workplaceType").asText())
                .isEqualTo("UNKNOWN");
        assertThat(json.withArray("sources")).isEmpty();
        assertThat(json.withArray("skills")).isEmpty();
        assertThat(json.withArray("fieldProvenance")).isEmpty();
        assertThat(json.path("experience").path("level").asText())
                .isEqualTo("UNKNOWN");
        assertThat(json.path("experience")
                .path("normalisationStatus").asText())
                .isEqualTo("NOT_PROVIDED");
    }

    @Test
    void fullModelRoundTripPreservesPrecisionTimezoneAndProvenance()
            throws Exception {
        OffsetDateTime postedAt =
                OffsetDateTime.parse("2026-07-24T09:00:00+01:00");
        Job job = new Job();
        job.setCanonicalJobId("job_123");
        job.setPrimarySource("JSEARCH");
        job.setExternalJobId("provider-123");
        job.setEmploymentType("FULLTIME");
        job.setEmploymentTypeCode(EmploymentTypeCode.FULL_TIME);
        job.setContractTypeCode(ContractTypeCode.PERMANENT);
        job.setWorkplaceType(WorkplaceTypeCode.HYBRID);
        job.setPostedAt("2026-07-24T09:00:00+01:00");
        job.setPostedAtUtc(postedAt);

        CanonicalLocation location = new CanonicalLocation();
        location.setRawDisplayName("City of London, UK");
        location.setRawCity("London");
        location.setRawCountry("UK");
        location.setDisplayName("London");
        location.setCountryCode("GB");
        location.setLatitude(new BigDecimal("51.507351"));
        location.setLongitude(new BigDecimal("-0.127758"));
        location.setSourceProvider("JSEARCH");
        location.setNormalisationStatus(
                CanonicalValueStatus.NORMALISED);
        location.setNormalisationConfidence(new BigDecimal("0.98"));
        job.setCanonicalLocation(location);

        JobSalary salary = new JobSalary();
        salary.setRawMinimum(new BigDecimal("19.375"));
        salary.setRawMaximum(new BigDecimal("25.125"));
        salary.setRawCurrency("gbp");
        salary.setRawPeriod("hourly");
        salary.setMinimum(new BigDecimal("19.375"));
        salary.setMaximum(new BigDecimal("25.125"));
        salary.setCurrencyCode("GBP");
        salary.setPeriodCode(SalaryPeriodCode.HOUR);
        salary.setNormalisationStatus(
                CanonicalValueStatus.NORMALISED);
        salary.setNormalisationConfidence(new BigDecimal("1.0"));
        salary.setNormalisationMethod("salary-v2/hourly");
        salary.setSourceProvider("JSEARCH");
        job.setSalary(salary);

        JobSourceReference source = new JobSourceReference();
        source.setProvider("JSEARCH");
        source.setExternalJobId("provider-123");
        source.setRawPublisher("Example Employer");
        source.setPublisher("Example Employer");
        source.setSourceType(JobSourceType.EMPLOYER);
        source.setListingUrl("https://jobs.example.test/123");
        source.setApplyUrl("https://jobs.example.test/123/apply");
        source.setProviderPostedAt(postedAt);
        source.setProviderPostedAtRaw(
                "2026-07-24T09:00:00+01:00");
        source.setProviderPostedAtUtc(postedAt);
        job.setSources(List.of(source));

        JobSkill skill = new JobSkill();
        skill.setRawName("Spring Boot");
        skill.setName("Spring Boot");
        skill.setType(JobSkillType.REQUIRED);
        skill.setNormalisationStatus(
                CanonicalValueStatus.NORMALISED);
        skill.setNormalisationConfidence(BigDecimal.ONE);
        skill.setSourceProvider("JSEARCH");
        job.setSkills(List.of(skill));

        JobExperience experience = new JobExperience();
        experience.setRawValue("3+ years");
        experience.setLevel(ExperienceLevelCode.MID);
        experience.setMinimumYears(new BigDecimal("3"));
        experience.setNormalisationStatus(
                CanonicalValueStatus.NORMALISED);
        experience.setNormalisationConfidence(
                new BigDecimal("0.95"));
        experience.setSourceProvider("JSEARCH");
        job.setExperience(experience);

        JobFieldProvenance provenance = new JobFieldProvenance();
        provenance.setFieldName("workplaceType");
        provenance.setSourceProvider("JSEARCH");
        provenance.setSourceExternalJobId("provider-123");
        provenance.setRawValue("hybrid");
        provenance.setNormalisedValue("HYBRID");
        provenance.setStatus(CanonicalValueStatus.NORMALISED);
        provenance.setConfidence(BigDecimal.ONE);
        provenance.setRuleVersion("workplace-v2");
        job.setFieldProvenance(List.of(provenance));

        Job roundTripped = objectMapper.readValue(
                objectMapper.writeValueAsBytes(job),
                Job.class);

        assertThat(roundTripped.getCanonicalSchemaVersion())
                .isEqualTo("2.0");
        assertThat(roundTripped.getPostedAtUtc()).isEqualTo(postedAt);
        assertThat(roundTripped.getSources()).singleElement()
                .extracting(JobSourceReference::getProviderPostedAt)
                .isEqualTo(postedAt.withOffsetSameInstant(ZoneOffset.UTC));
        assertThat(roundTripped.getCanonicalLocation().getLatitude())
                .isEqualByComparingTo("51.507351");
        assertThat(roundTripped.getSalary().getRawMinimum())
                .isEqualByComparingTo("19.375");
        assertThat(roundTripped.getSalary().getPeriodCode())
                .isEqualTo(SalaryPeriodCode.HOUR);
        assertThat(roundTripped.getSkills()).singleElement()
                .extracting(JobSkill::getType)
                .isEqualTo(JobSkillType.REQUIRED);
        assertThat(roundTripped.getExperience().getMinimumYears())
                .isEqualByComparingTo("3");
        assertThat(roundTripped.getFieldProvenance()).singleElement()
                .extracting(JobFieldProvenance::getRuleVersion)
                .isEqualTo("workplace-v2");
    }

    @Test
    void legacyUtcApplicationTimestampsSerializeWithContractRequiredOffset()
            throws Exception {
        Job job = objectMapper.readValue(
                """
                {
                  "applicationStatus": "APPLIED",
                  "applicationId": "1d4fe4de-dc83-4bdc-b66e-951f54c2e5a8",
                  "appliedAt": "2026-07-29T21:23:45.703864",
                  "applicationUpdatedAt": "2026-07-29T22:23:45.703864"
                }
                """,
                Job.class);

        assertThat(job.getAppliedAt())
                .isEqualTo(OffsetDateTime.parse(
                        "2026-07-29T21:23:45.703864Z"));
        assertThat(job.getApplicationUpdatedAt())
                .isEqualTo(OffsetDateTime.parse(
                        "2026-07-29T22:23:45.703864Z"));

        ObjectNode json = objectMapper.valueToTree(job);
        assertThat(json.path("applicationStatus").asText())
                .isEqualTo("APPLIED");
        assertThat(json.path("applicationId").asText())
                .isEqualTo("1d4fe4de-dc83-4bdc-b66e-951f54c2e5a8");
        assertThat(json.path("appliedAt").asText())
                .isEqualTo("2026-07-29T21:23:45.703864Z");
        assertThat(json.path("applicationUpdatedAt").asText())
                .isEqualTo("2026-07-29T22:23:45.703864Z");
    }

    @Test
    void applicationTimestampOffsetsAreNormalisedToUtc()
            throws Exception {
        Job job = objectMapper.readValue(
                """
                {
                  "appliedAt": "2026-07-29T23:23:45.703864+01:00"
                }
                """,
                Job.class);

        assertThat(job.getAppliedAt())
                .isEqualTo(OffsetDateTime.parse(
                        "2026-07-29T22:23:45.703864Z"));
    }

    @Test
    void malformedApplicationTimestampsAreRejected() {
        assertThatThrownBy(() -> objectMapper.readValue(
                """
                {"applicationUpdatedAt": "not-a-timestamp"}
                """,
                Job.class))
                .isInstanceOf(JsonProcessingException.class);
        assertThatThrownBy(() -> objectMapper.readValue(
                """
                {"applicationUpdatedAt": 1782813600}
                """,
                Job.class))
                .isInstanceOf(JsonProcessingException.class);
    }

    @Test
    void legacyV1PayloadRemainsReadableWithoutInventingValues()
            throws Exception {
        String legacyJson = """
                {
                  "id": "legacy-1",
                  "canonicalJobId": "legacy-1",
                  "provider": "REED",
                  "primarySource": "REED",
                  "externalJobId": "reed-1",
                  "title": "Developer",
                  "company": "Example Ltd",
                  "location": "London",
                  "employmentType": "permanent",
                  "postedAt": "2026-07-24",
                  "remote": false,
                  "sources": []
                }
                """;

        Job job = objectMapper.readValue(legacyJson, Job.class);

        assertThat(job.getId()).isEqualTo("legacy-1");
        assertThat(job.getEmploymentType()).isEqualTo("permanent");
        assertThat(job.getPostedAt()).isEqualTo("2026-07-24");
        assertThat(job.getRemote()).isFalse();
        assertThat(job.getCanonicalSchemaVersion()).isEqualTo("2.0");
        assertThat(job.getEmploymentTypeCode())
                .isEqualTo(EmploymentTypeCode.UNKNOWN);
        assertThat(job.getWorkplaceType())
                .isEqualTo(WorkplaceTypeCode.UNKNOWN);
        assertThat(job.getPostedAtUtc()).isNull();
        assertThat(job.getSkills()).isEmpty();
        assertThat(objectMapper.writeValueAsString(job))
                .contains("\"id\":\"legacy-1\"")
                .contains("\"canonicalSchemaVersion\":\"2.0\"");
    }

    @Test
    void optionalAndExplicitNullFieldsAlwaysFailSafeToUnknowns()
            throws Exception {
        Random random = new Random(404L);
        for (int iteration = 0; iteration < 100; iteration++) {
            ObjectNode payload = objectMapper.createObjectNode();
            if (random.nextBoolean()) {
                payload.put("title", "Role " + iteration);
            }
            if (random.nextBoolean()) {
                payload.putNull("canonicalSchemaVersion");
            }
            if (random.nextBoolean()) {
                payload.putNull("employmentTypeCode");
            }
            if (random.nextBoolean()) {
                payload.putNull("contractTypeCode");
            }
            if (random.nextBoolean()) {
                payload.putNull("workplaceType");
            }
            if (random.nextBoolean()) {
                payload.putNull("skills");
            }
            if (random.nextBoolean()) {
                payload.putNull("sources");
            }
            if (random.nextBoolean()) {
                payload.putNull("fieldProvenance");
            }
            if (random.nextBoolean()) {
                payload.putNull("experience");
            }

            Job job = objectMapper.treeToValue(payload, Job.class);

            assertThat(job.getEmploymentTypeCode())
                    .isEqualTo(EmploymentTypeCode.UNKNOWN);
            assertThat(job.getCanonicalSchemaVersion()).isEqualTo("2.0");
            assertThat(job.getContractTypeCode())
                    .isEqualTo(ContractTypeCode.UNKNOWN);
            assertThat(job.getWorkplaceType())
                    .isEqualTo(WorkplaceTypeCode.UNKNOWN);
            assertThat(job.getSkills()).isNotNull();
            assertThat(job.getSources()).isNotNull();
            assertThat(job.getFieldProvenance()).isNotNull();
            assertThat(job.getExperience()).isNotNull();
            assertThat(job.getExperience().getLevel())
                    .isEqualTo(ExperienceLevelCode.UNKNOWN);
        }
    }
}
