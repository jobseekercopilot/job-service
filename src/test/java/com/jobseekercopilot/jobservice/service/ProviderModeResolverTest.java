package com.jobseekercopilot.jobservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class ProviderModeResolverTest {
    @Test
    void reportsFixtureModeExactlyAndCachesTheGatewayProbe() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server =
                MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(once(), requestTo("http://reed/internal/provider-mode"))
                .andRespond(withSuccess("""
                        {
                          "gateway":"reed-gateway",
                          "mode":"FIXTURE",
                          "datasetId":"basic-fixture",
                          "datasetVersion":"2026-08-01",
                          "scenario":"default",
                          "externalCallsEnabled":false
                        }
                        """, MediaType.APPLICATION_JSON));
        ProviderModeResolver resolver = new ProviderModeResolver(
                restTemplate,
                "http://reed",
                "http://adzuna",
                "http://jsearch",
                "http://nhs",
                "http://apprenticeships",
                fixedClock());

        var first = resolver.resolve("REED");
        var second = resolver.resolve("reed");

        server.verify();
        assertThat(first.mode()).isEqualTo("FIXTURE");
        assertThat(first.dataOrigin()).isEqualTo("FIXTURE");
        assertThat(first.datasetId()).isEqualTo("basic-fixture");
        assertThat(first.externalCallsEnabled()).isFalse();
        assertThat(second).isEqualTo(first);
    }

    @Test
    void readsModeContractsForNhsJobsAndApprenticeships() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server =
                MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(once(), requestTo("http://nhs/internal/provider-mode"))
                .andRespond(withSuccess("""
                        {"mode":"LIVE","externalCallsEnabled":true}
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("http://apprenticeships/internal/provider-mode"))
                .andRespond(withSuccess("""
                        {"mode":"FIXTURE","datasetId":"vacancies-2026-08","externalCallsEnabled":false}
                        """, MediaType.APPLICATION_JSON));
        ProviderModeResolver resolver = new ProviderModeResolver(
                restTemplate,
                "http://reed",
                "http://adzuna",
                "http://jsearch",
                "http://nhs",
                "http://apprenticeships",
                fixedClock());

        var nhs = resolver.resolve("NHS_JOBS");
        var apprenticeships = resolver.resolve("APPRENTICESHIPS");

        server.verify();
        assertThat(nhs.mode()).isEqualTo("LIVE");
        assertThat(nhs.dataOrigin()).isEqualTo("LIVE_PROVIDER");
        assertThat(nhs.externalCallsEnabled()).isTrue();
        assertThat(apprenticeships.mode()).isEqualTo("FIXTURE");
        assertThat(apprenticeships.datasetId()).isEqualTo("vacancies-2026-08");
        assertThat(apprenticeships.externalCallsEnabled()).isFalse();
    }

    @Test
    void doesNotGuessModesForUnknownProviders() {
        ProviderModeResolver resolver = new ProviderModeResolver(
                new RestTemplate(),
                "http://reed",
                "http://adzuna",
                "http://jsearch",
                "http://nhs",
                "http://apprenticeships",
                fixedClock());

        var result = resolver.resolve("OTHER");

        assertThat(result.mode()).isEqualTo("UNKNOWN");
        assertThat(result.dataOrigin()).isEqualTo("UNKNOWN");
        assertThat(result.externalCallsEnabled()).isNull();
    }

    @Test
    void normalisesUnexpectedGatewayModesToThePublicUnknownEnum() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server =
                MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(once(), requestTo("http://reed/internal/provider-mode"))
                .andRespond(withSuccess("""
                        {"mode":"DEMO_READY","externalCallsEnabled":false}
                        """, MediaType.APPLICATION_JSON));
        ProviderModeResolver resolver = new ProviderModeResolver(
                restTemplate,
                "http://reed",
                "http://adzuna",
                "http://jsearch",
                "http://nhs",
                "http://apprenticeships",
                fixedClock());

        var result = resolver.resolve("REED");

        server.verify();
        assertThat(result.mode()).isEqualTo("UNKNOWN");
        assertThat(result.dataOrigin()).isEqualTo("UNKNOWN");
        assertThat(result.externalCallsEnabled()).isFalse();
    }

    private Clock fixedClock() {
        return Clock.fixed(
                Instant.parse("2026-08-13T08:15:30Z"),
                ZoneOffset.UTC);
    }
}
