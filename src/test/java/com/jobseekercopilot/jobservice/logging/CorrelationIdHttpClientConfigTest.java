package com.jobseekercopilot.jobservice.logging;

import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class CorrelationIdHttpClientConfigTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void propagatesCorrelationIdToRestTemplateRequests() {
        RestTemplate restTemplate = new RestTemplate();
        new CorrelationIdHttpClientConfig()
                .correlationIdRestTemplateCustomizer()
                .customize(restTemplate);
        MockRestServiceServer server =
                MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(once(), requestTo("https://provider.example/jobs"))
                .andExpect(header(
                        CorrelationIdFilter.HEADER_NAME,
                        "outbound-correlation"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        MDC.put(CorrelationIdFilter.MDC_KEY, "outbound-correlation");

        restTemplate.getForObject("https://provider.example/jobs", String.class);

        server.verify();
    }
}
