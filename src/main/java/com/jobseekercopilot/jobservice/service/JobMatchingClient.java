package com.jobseekercopilot.jobservice.service;

import com.jobseekercopilot.jobservice.model.dto.EnrichJobsRequest;
import com.jobseekercopilot.jobservice.model.dto.EnrichJobsResponse;
import com.jobseekercopilot.jobservice.model.dto.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Component
public class JobMatchingClient {

    private static final Logger log = LoggerFactory.getLogger(JobMatchingClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public JobMatchingClient(
            RestTemplate restTemplate,
            @Value("${services.job-matching-service.base-url:http://localhost:8097}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public List<Job> enrichJobs(String userId, List<Job> jobs) {
        long startedAt = System.nanoTime();
        log.info("Calling job-matching-service path=/api/v1/job-matches/enrich jobsReceived={}",
                jobs == null ? 0 : jobs.size());
        try {
            EnrichJobsResponse response = restTemplate.postForObject(
                    baseUrl + "/api/v1/job-matches/enrich",
                    new EnrichJobsRequest(userId, jobs),
                    EnrichJobsResponse.class);
            List<Job> enrichedJobs = response == null || response.getJobs() == null ? jobs : response.getJobs();
            log.info("job-matching-service returned status=200 enrichedCount={} durationMs={}",
                    enrichedJobs == null ? 0 : enrichedJobs.size(),
                    (System.nanoTime() - startedAt) / 1_000_000);
            return enrichedJobs;
        } catch (ResourceAccessException | RestClientResponseException ex) {
            log.warn("job-matching-service failed durationMs={} error={}",
                    (System.nanoTime() - startedAt) / 1_000_000,
                    ex.getClass().getSimpleName());
            throw new JobMatchingUnavailableException("Job matching service is unavailable", ex);
        }
    }

    public static class JobMatchingUnavailableException extends RuntimeException {
        public JobMatchingUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
