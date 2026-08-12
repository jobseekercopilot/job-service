package com.jobseekercopilot.jobservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jobseekercopilot.jobservice.model.dto.AdvertiserType;
import com.jobseekercopilot.jobservice.model.dto.CommuteAssessment;
import com.jobseekercopilot.jobservice.model.dto.Job;
import com.jobseekercopilot.jobservice.model.dto.JobSkill;
import com.jobseekercopilot.jobservice.model.dto.JobSkillType;
import com.jobseekercopilot.jobservice.model.dto.JobSourceReference;
import com.jobseekercopilot.jobservice.model.dto.JobSourceType;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class JobMatchingClientTest {

    private static final String BASE_URL =
            "http://job-matching-service";
    private static final String ENRICH_URL =
            BASE_URL + "/api/v1/job-matches/enrich";

    @Test
    void ignoresReducedEchoAndMergesOnlyOwnedFieldsOntoCanonicalJob() {
        Job original = richJob("canonical-1");
        JobSourceReference originalSource = original.getSources().get(0);
        UUID applicationId = UUID.randomUUID();
        String response = """
                {
                  "userId": "user-1",
                  "jobs": [{
                    "id": "canonical-1",
                    "canonicalJobId": "canonical-1",
                    "provider": "REED",
                    "title": "Reduced echo must not replace provider title",
                    "sources": [{
                      "provider": "REED",
                      "externalJobId": "reed-1",
                      "providerPostedAt": "2026-08-12T10:00:00"
                    }],
                    "distanceMiles": 12.5,
                    "matchScore": 0.87,
                    "commuteAssessment": {
                      "status": "WITHIN_PREFERENCE",
                      "workplaceType": "HYBRID",
                      "modes": [],
                      "bestSuitableMode": null,
                      "explanationCode": "WITHIN_LIMIT",
                      "providerAttribution": "Google Maps"
                    },
                    "applicationStatus": "APPLIED",
                    "applicationId": "%s",
                    "cvDocumentId": "cv-version-1",
                    "coverLetterDocumentId": "cover-version-1",
                    "appliedAt": "2026-08-12T11:20:00+01:00",
                    "applicationUpdatedAt": "2026-08-12T10:30:00Z"
                  }]
                }
                """.formatted(applicationId);
        ClientFixture fixture = fixture(response);

        List<Job> result = fixture.client().enrichJobs(
                "user-1", List.of(original));

        fixture.server().verify();
        assertThat(result).containsExactly(original);
        assertThat(result.get(0)).isSameAs(original);
        assertThat(original.getTitle()).isEqualTo("Provider-owned title");
        assertThat(original.getAdvertiserName()).isEqualTo("Trusted recruiter");
        assertThat(original.getAdvertiserType())
                .isEqualTo(AdvertiserType.RECRUITER);
        assertThat(original.getPostedAtUtc())
                .isEqualTo("2026-08-10T09:15:00Z");
        assertThat(original.getSkills()).singleElement()
                .extracting(JobSkill::getName)
                .isEqualTo("Java");
        assertThat(original.getSources()).containsExactly(originalSource);
        assertThat(originalSource.getSourceType())
                .isEqualTo(JobSourceType.AGGREGATOR);
        assertThat(originalSource.getProviderPostedAtUtc())
                .isEqualTo("2026-08-10T09:15:00Z");

        assertThat(original.getDistanceMiles()).isEqualTo(12.5);
        assertThat(original.getMatchScore()).isEqualTo(0.87);
        assertThat(original.getCommuteAssessment().getStatus())
                .isEqualTo(CommuteAssessment.Status.WITHIN_PREFERENCE);
        assertThat(original.getApplicationStatus()).isEqualTo("APPLIED");
        assertThat(original.getApplicationId()).isEqualTo(applicationId);
        assertThat(original.getCvDocumentId()).isEqualTo("cv-version-1");
        assertThat(original.getCoverLetterDocumentId())
                .isEqualTo("cover-version-1");
        assertThat(original.getAppliedAt())
                .isEqualTo(OffsetDateTime.of(
                        2026, 8, 12, 10, 20, 0, 0, ZoneOffset.UTC));
        assertThat(original.getApplicationUpdatedAt())
                .isEqualTo("2026-08-12T10:30:00Z");
    }

    @Test
    void mapsReorderedResponseByCanonicalJobId() {
        Job first = job("canonical-1");
        Job second = job("canonical-2");
        String response = """
                {
                  "userId": "user-1",
                  "jobs": [
                    {
                      "canonicalJobId": "canonical-2",
                      "applicationStatus": "SAVED"
                    },
                    {
                      "canonicalJobId": "canonical-1",
                      "applicationStatus": "NEW"
                    }
                  ]
                }
                """;
        ClientFixture fixture = fixture(response);

        List<Job> result = fixture.client().enrichJobs(
                "user-1", List.of(first, second));

        fixture.server().verify();
        assertThat(result).containsExactly(first, second);
        assertThat(first.getApplicationStatus()).isEqualTo("NEW");
        assertThat(second.getApplicationStatus()).isEqualTo("SAVED");
    }

    @Test
    void rejectsDuplicateResponseCanonicalIdsBeforeMutatingJobs() {
        Job first = job("canonical-1");
        Job second = job("canonical-2");
        String response = """
                {
                  "userId": "user-1",
                  "jobs": [
                    {
                      "canonicalJobId": "canonical-1",
                      "applicationStatus": "SAVED"
                    },
                    {
                      "canonicalJobId": "canonical-1",
                      "applicationStatus": "APPLIED"
                    }
                  ]
                }
                """;
        ClientFixture fixture = fixture(response);

        assertThatThrownBy(() -> fixture.client().enrichJobs(
                "user-1", List.of(first, second)))
                .isInstanceOf(
                        JobMatchingClient.JobMatchingUnavailableException.class)
                .hasRootCauseMessage(
                        "Matching response contained duplicate canonical job identities");
        fixture.server().verify();
        assertThat(first.getApplicationStatus()).isNull();
        assertThat(second.getApplicationStatus()).isNull();
    }

    @Test
    void rejectsMissingResponseJobBeforeMutatingJobs() {
        Job first = job("canonical-1");
        Job second = job("canonical-2");
        String response = """
                {
                  "userId": "user-1",
                  "jobs": [{
                    "canonicalJobId": "canonical-1",
                    "applicationStatus": "SAVED"
                  }]
                }
                """;
        ClientFixture fixture = fixture(response);

        assertThatThrownBy(() -> fixture.client().enrichJobs(
                "user-1", List.of(first, second)))
                .isInstanceOf(
                        JobMatchingClient.JobMatchingUnavailableException.class)
                .hasRootCauseMessage(
                        "Matching response job count did not match request");
        fixture.server().verify();
        assertThat(first.getApplicationStatus()).isNull();
        assertThat(second.getApplicationStatus()).isNull();
    }

    @Test
    void rejectsUnknownResponseCanonicalIdBeforeMutatingJobs() {
        Job original = job("canonical-1");
        String response = """
                {
                  "userId": "user-1",
                  "jobs": [{
                    "canonicalJobId": "unknown-canonical-id",
                    "applicationStatus": "SAVED"
                  }]
                }
                """;
        ClientFixture fixture = fixture(response);

        assertThatThrownBy(() -> fixture.client().enrichJobs(
                "user-1", List.of(original)))
                .isInstanceOf(
                        JobMatchingClient.JobMatchingUnavailableException.class)
                .hasRootCauseMessage(
                        "Matching response identities did not match request");
        fixture.server().verify();
        assertThat(original.getApplicationStatus()).isNull();
    }

    @Test
    void rejectsMissingResponseCanonicalIdBeforeMutatingJobs() {
        Job original = job("canonical-1");
        String response = """
                {
                  "userId": "user-1",
                  "jobs": [{
                    "id": "canonical-1",
                    "applicationStatus": "SAVED"
                  }]
                }
                """;
        ClientFixture fixture = fixture(response);

        assertThatThrownBy(() -> fixture.client().enrichJobs(
                "user-1", List.of(original)))
                .isInstanceOf(
                        JobMatchingClient.JobMatchingUnavailableException.class)
                .hasRootCauseMessage(
                        "Matching response contained a missing canonical job identity");
        fixture.server().verify();
        assertThat(original.getApplicationStatus()).isNull();
    }

    private ClientFixture fixture(String response) {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
        RestTemplate restTemplate = new RestTemplate(List.of(
                new MappingJackson2HttpMessageConverter(objectMapper)));
        MockRestServiceServer server =
                MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(once(), requestTo(ENRICH_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        return new ClientFixture(
                new JobMatchingClient(restTemplate, BASE_URL), server);
    }

    private Job richJob(String canonicalJobId) {
        Job job = job(canonicalJobId);
        job.setTitle("Provider-owned title");
        job.setAdvertiserName("Trusted recruiter");
        job.setAdvertiserType(AdvertiserType.RECRUITER);
        job.setPostedAtUtc(OffsetDateTime.parse(
                "2026-08-10T09:15:00Z"));

        JobSkill skill = new JobSkill();
        skill.setName("Java");
        skill.setType(JobSkillType.REQUIRED);
        job.setSkills(List.of(skill));

        JobSourceReference source = new JobSourceReference();
        source.setProvider("REED");
        source.setExternalJobId("reed-1");
        source.setSourceType(JobSourceType.AGGREGATOR);
        source.setProviderPostedAtUtc(OffsetDateTime.parse(
                "2026-08-10T09:15:00Z"));
        job.setSources(List.of(source));
        return job;
    }

    private Job job(String canonicalJobId) {
        Job job = new Job();
        job.setId(canonicalJobId);
        job.setCanonicalJobId(canonicalJobId);
        return job;
    }

    private record ClientFixture(
            JobMatchingClient client,
            MockRestServiceServer server) {
    }
}
