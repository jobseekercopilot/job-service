package com.jobseekercopilot.jobservice.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jobseekercopilot.jobservice.model.dto.Job;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

class JobMatchingClientTest {

    @Test
    void translatesResponseConversionFailuresToOptionalMatchingUnavailable() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        RestClientException conversionFailure = new RestClientException(
                "Unable to convert matching response");
        when(restTemplate.postForObject(anyString(), any(), any(Class.class)))
                .thenThrow(conversionFailure);
        JobMatchingClient client = new JobMatchingClient(
                restTemplate, "http://job-matching-service");

        assertThatThrownBy(() -> client.enrichJobs("user-1", List.of(new Job())))
                .isInstanceOf(JobMatchingClient.JobMatchingUnavailableException.class)
                .hasMessage("Job matching service is unavailable")
                .hasCause(conversionFailure);
    }
}
