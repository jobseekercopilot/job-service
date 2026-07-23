package com.jobseekercopilot.jobservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jobseekercopilot.jobservice.model.dto.Aspirations;
import com.jobseekercopilot.jobservice.model.dto.JSearchSearchRequest;
import com.jobseekercopilot.jobservice.model.dto.JSearchSearchResponse;
import com.jobseekercopilot.jobservice.model.dto.JobSearchRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.RestTemplate;

class JSearchJobProviderAdapterTest {

    @Test
    void usesNonPostcodeProfileLocationWhenPrimaryLocationIsPostcode() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        JSearchSearchResponse response = new JSearchSearchResponse();
        response.setJobs(List.of());
        ArgumentCaptor<JSearchSearchRequest> requestCaptor = ArgumentCaptor.forClass(JSearchSearchRequest.class);
        when(restTemplate.postForObject(
                eq("http://jsearch/api/v1/jsearch/jobs/search"),
                requestCaptor.capture(),
                eq(JSearchSearchResponse.class)))
                .thenReturn(response);

        JSearchJobProviderAdapter adapter = new JSearchJobProviderAdapter(
                restTemplate,
                new PublisherNormalisationService(),
                "http://jsearch",
                true);

        adapter.search("user-1", criteria(List.of("UB3 4QZ", "Hillingdon, London")));

        assertThat(requestCaptor.getValue().getLocation()).isEqualTo("Hillingdon, London");
    }

    private JobSearchCriteria criteria(List<String> locations) {
        Aspirations aspirations = new Aspirations();
        aspirations.setDesiredRoles(List.of("Software Developer"));
        aspirations.setLocations(locations);

        JobSearchRequest request = new JobSearchRequest();
        request.setAspirations(aspirations);

        return new JobSearchCriteria(
                request,
                "Software Developer",
                locations.get(0),
                25,
                List.of("FULL_TIME"),
                null,
                null,
                "GBP",
                false);
    }
}
