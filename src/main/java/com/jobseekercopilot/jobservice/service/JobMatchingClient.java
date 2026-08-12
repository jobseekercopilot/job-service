package com.jobseekercopilot.jobservice.service;

import com.jobseekercopilot.jobservice.model.dto.EnrichJobsRequest;
import com.jobseekercopilot.jobservice.model.dto.EnrichJobsResponse;
import com.jobseekercopilot.jobservice.model.dto.HomeLocation;
import com.jobseekercopilot.jobservice.model.dto.Job;
import com.jobseekercopilot.jobservice.model.dto.MatchingJobEnrichment;
import com.jobseekercopilot.jobservice.model.dto.WorkPreferences;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

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
            List<Job> enrichedJobs = mergeEnrichment(jobs, response);
            log.info("job-matching-service returned status=200 enrichedCount={} durationMs={}",
                    enrichedJobs == null ? 0 : enrichedJobs.size(),
                    (System.nanoTime() - startedAt) / 1_000_000);
            return enrichedJobs;
        } catch (RestClientException | InvalidMatchingResponseException ex) {
            Throwable root = ex;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            log.warn("job-matching-service failed durationMs={} error={} rootCause={}",
                    (System.nanoTime() - startedAt) / 1_000_000,
                    ex.getClass().getSimpleName(), root.getClass().getSimpleName());
            throw new JobMatchingUnavailableException("Job matching service is unavailable", ex);
        }
    }

    private List<Job> mergeEnrichment(
            List<Job> originalJobs,
            EnrichJobsResponse response) {
        if (response == null || response.getJobs() == null) {
            throw new InvalidMatchingResponseException(
                    "Matching response did not contain jobs");
        }
        List<Job> safeOriginalJobs = originalJobs == null
                ? List.of()
                : originalJobs;
        if (response.getJobs().size() != safeOriginalJobs.size()) {
            throw new InvalidMatchingResponseException(
                    "Matching response job count did not match request");
        }

        Map<String, Job> originalByCanonicalId = new LinkedHashMap<>();
        for (Job job : safeOriginalJobs) {
            String canonicalJobId = canonicalJobId(job);
            if (originalByCanonicalId.putIfAbsent(canonicalJobId, job) != null) {
                throw new InvalidMatchingResponseException(
                        "Matching request contained duplicate canonical job identities");
            }
        }

        Map<String, MatchingJobEnrichment> enrichmentByCanonicalId =
                new LinkedHashMap<>();
        for (MatchingJobEnrichment enrichment : response.getJobs()) {
            String canonicalJobId = canonicalJobId(enrichment);
            if (enrichmentByCanonicalId.putIfAbsent(
                    canonicalJobId, enrichment) != null) {
                throw new InvalidMatchingResponseException(
                        "Matching response contained duplicate canonical job identities");
            }
        }
        if (!originalByCanonicalId.keySet().equals(
                enrichmentByCanonicalId.keySet())) {
            throw new InvalidMatchingResponseException(
                    "Matching response identities did not match request");
        }

        for (Map.Entry<String, Job> entry : originalByCanonicalId.entrySet()) {
            applyEnrichment(
                    entry.getValue(),
                    enrichmentByCanonicalId.get(entry.getKey()));
        }
        return safeOriginalJobs;
    }

    private String canonicalJobId(Job job) {
        if (job == null
                || job.getCanonicalJobId() == null
                || job.getCanonicalJobId().isBlank()) {
            throw new InvalidMatchingResponseException(
                    "Matching request contained a missing canonical job identity");
        }
        return job.getCanonicalJobId();
    }

    private String canonicalJobId(MatchingJobEnrichment enrichment) {
        if (enrichment == null
                || enrichment.getCanonicalJobId() == null
                || enrichment.getCanonicalJobId().isBlank()) {
            throw new InvalidMatchingResponseException(
                    "Matching response contained a missing canonical job identity");
        }
        return enrichment.getCanonicalJobId();
    }

    private void applyEnrichment(
            Job job,
            MatchingJobEnrichment enrichment) {
        if (enrichment.getDistanceMiles() != null) {
            job.setDistanceMiles(enrichment.getDistanceMiles());
        }
        if (enrichment.getMatchScore() != null) {
            job.setMatchScore(enrichment.getMatchScore());
        }
        job.setCommuteAssessment(enrichment.getCommuteAssessment());
        job.setApplicationStatus(enrichment.getApplicationStatus());
        job.setApplicationId(enrichment.getApplicationId());
        job.setCvDocumentId(enrichment.getCvDocumentId());
        job.setCoverLetterDocumentId(
                enrichment.getCoverLetterDocumentId());
        job.setAppliedAt(enrichment.getAppliedAt());
        job.setApplicationUpdatedAt(
                enrichment.getApplicationUpdatedAt());
    }

    private static final class InvalidMatchingResponseException
            extends RuntimeException {
        private InvalidMatchingResponseException(String message) {
            super(message);
        }
    }

    public static class JobMatchingUnavailableException extends RuntimeException {
        public JobMatchingUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
