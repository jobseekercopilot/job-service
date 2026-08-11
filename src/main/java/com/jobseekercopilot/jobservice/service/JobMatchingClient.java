package com.jobseekercopilot.jobservice.service;

import com.jobseekercopilot.jobservice.model.dto.EnrichJobsRequest;
import com.jobseekercopilot.jobservice.model.dto.EnrichJobsResponse;
import com.jobseekercopilot.jobservice.model.dto.Job;
import com.jobseekercopilot.jobservice.model.dto.HomeLocation;
import com.jobseekercopilot.jobservice.model.dto.WorkPreferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Component
public class JobMatchingClient {

    private static final Logger log = LoggerFactory.getLogger(JobMatchingClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public JobMatchingClient(
            @Qualifier("jobMatchingRestTemplate") RestTemplate restTemplate,
            @Value("${services.job-matching-service.base-url:http://localhost:8097}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public List<Job> enrichJobs(String userId, List<Job> jobs) {
        return enrichJobs(userId, jobs, null, null);
    }

    public List<Job> enrichJobs(
            String userId,
            List<Job> jobs,
            HomeLocation homeLocation,
            WorkPreferences commutePreferences) {
        long startedAt = System.nanoTime();
        log.info("Calling job-matching-service path=/api/v1/job-matches/enrich jobsReceived={}",
                jobs == null ? 0 : jobs.size());
        try {
            EnrichJobsResponse response = restTemplate.postForObject(
                    baseUrl + "/api/v1/job-matches/enrich",
                    new EnrichJobsRequest(userId, jobs, homeLocation, commutePreferences),
                    EnrichJobsResponse.class);
            List<Job> enrichedJobs = response == null || response.getJobs() == null ? jobs : response.getJobs();
            log.info("job-matching-service returned status=200 enrichedCount={} durationMs={}",
                    enrichedJobs == null ? 0 : enrichedJobs.size(),
                    (System.nanoTime() - startedAt) / 1_000_000);
            return enrichedJobs;
        } catch (RestClientException ex) {
            log.warn("job-matching-service failed durationMs={} error={} cause={}",
                    (System.nanoTime() - startedAt) / 1_000_000,
                    ex.getClass().getSimpleName(), rootCauseName(ex));
            throw new JobMatchingUnavailableException("Job matching service is unavailable", ex);
        }
    }

    private String rootCauseName(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getClass().getSimpleName();
    }

    public static class JobMatchingUnavailableException extends RuntimeException {
        public JobMatchingUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
