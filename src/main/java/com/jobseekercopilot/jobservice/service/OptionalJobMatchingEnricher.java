package com.jobseekercopilot.jobservice.service;

import com.jobseekercopilot.jobservice.config.JobSearchResilienceProperties;
import com.jobseekercopilot.jobservice.model.dto.Job;
import com.jobseekercopilot.jobservice.model.dto.HomeLocation;
import com.jobseekercopilot.jobservice.model.dto.WorkPreferences;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class OptionalJobMatchingEnricher {

    private static final Logger log =
            LoggerFactory.getLogger(OptionalJobMatchingEnricher.class);

    private final JobMatchingClient jobMatchingClient;
    private final ThreadPoolExecutor executor;
    private final JobSearchResilienceProperties resilience;

    public OptionalJobMatchingEnricher(
            JobMatchingClient jobMatchingClient,
            @Qualifier("jobSearchExecutor") ThreadPoolExecutor executor,
            JobSearchResilienceProperties resilience) {
        this.jobMatchingClient = jobMatchingClient;
        this.executor = executor;
        this.resilience = resilience;
    }

    MatchingOutcome enrich(
            String userId,
            List<Job> providerJobs,
            long requestDeadlineNanos) {
        return enrichInternal(
                providerJobs,
                requestDeadlineNanos,
                () -> jobMatchingClient.enrichJobs(userId, providerJobs));
    }

    MatchingOutcome enrich(
            String userId,
            List<Job> providerJobs,
            long requestDeadlineNanos,
            HomeLocation homeLocation,
            WorkPreferences commutePreferences) {
        return enrichInternal(
                providerJobs,
                requestDeadlineNanos,
                () -> jobMatchingClient.enrichJobs(
                        userId, providerJobs, homeLocation, commutePreferences));
    }

    private MatchingOutcome enrichInternal(
            List<Job> providerJobs,
            long requestDeadlineNanos,
            Supplier<List<Job>> task) {
        if (providerJobs.isEmpty()) {
            return new MatchingOutcome(providerJobs, "NOT_RUN", false);
        }

        long remainingRequestNanos = requestDeadlineNanos - System.nanoTime();
        if (remainingRequestNanos <= 0) {
            return degraded(providerJobs, "TIMED_OUT");
        }

        Future<List<Job>> future;
        try {
            future = executor.submit(DownstreamTaskContext.withCurrentMdc(
                    task::get));
        } catch (RejectedExecutionException exception) {
            log.warn("Job Matching skipped because downstream executor is saturated");
            return degraded(providerJobs, "SATURATED");
        }

        long matchingBudgetNanos =
                TimeUnit.MILLISECONDS.toNanos(resilience.getMatchingTimeoutMs());
        long waitNanos = Math.min(remainingRequestNanos, matchingBudgetNanos);
        try {
            List<Job> result = future.get(waitNanos, TimeUnit.NANOSECONDS);
            return new MatchingOutcome(
                    mergeOwnedEnrichment(providerJobs, result),
                    "COMPLETE",
                    false);
        } catch (TimeoutException exception) {
            future.cancel(true);
            log.warn("Job Matching timed out; returning provider results");
            return degraded(providerJobs, "TIMED_OUT");
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            log.warn("Job Matching interrupted; returning provider results");
            return degraded(providerJobs, "UNAVAILABLE");
        } catch (ExecutionException exception) {
            log.warn("Job Matching unavailable; returning provider results error={}",
                    exception.getCause() == null
                            ? exception.getClass().getSimpleName()
                            : exception.getCause().getClass().getSimpleName());
            return degraded(providerJobs, "UNAVAILABLE");
        }
    }

    private MatchingOutcome degraded(List<Job> providerJobs, String status) {
        return new MatchingOutcome(providerJobs, status, true);
    }

    private List<Job> mergeOwnedEnrichment(
            List<Job> providerJobs,
            List<Job> matchingJobs) {
        if (matchingJobs == null || matchingJobs.isEmpty()) {
            return providerJobs;
        }
        Map<String, Job> matchingByIdentity = new LinkedHashMap<>();
        matchingJobs.forEach(job -> {
            identities(job).forEach(key ->
                    matchingByIdentity.putIfAbsent(key, job));
        });
        providerJobs.forEach(providerJob -> {
            Job matchingJob = identities(providerJob).stream()
                    .map(matchingByIdentity::get)
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            if (matchingJob != null) {
                providerJob.setMatchScore(matchingJob.getMatchScore());
                providerJob.setApplicationStatus(
                        matchingJob.getApplicationStatus());
                providerJob.setApplicationId(
                        matchingJob.getApplicationId());
                providerJob.setCvDocumentId(
                        matchingJob.getCvDocumentId());
                providerJob.setCoverLetterDocumentId(
                        matchingJob.getCoverLetterDocumentId());
                providerJob.setAppliedAt(matchingJob.getAppliedAt());
                providerJob.setApplicationUpdatedAt(
                        matchingJob.getApplicationUpdatedAt());
                providerJob.setCommuteAssessment(matchingJob.getCommuteAssessment());
            }
        });
        return providerJobs;
    }

    private List<String> identities(Job job) {
        if (job == null) {
            return List.of();
        }
        List<String> identities = new java.util.ArrayList<>();
        if (job.getCanonicalJobId() != null
                && !job.getCanonicalJobId().isBlank()) {
            identities.add("canonical:" + job.getCanonicalJobId());
        }
        if (job.getId() != null && !job.getId().isBlank()) {
            identities.add("id:" + job.getId());
        }
        if (job.getProvider() != null
                && !job.getProvider().isBlank()
                && job.getExternalJobId() != null
                && !job.getExternalJobId().isBlank()) {
            identities.add("source:" + job.getProvider()
                    + ":" + job.getExternalJobId());
        }
        return identities;
    }

    record MatchingOutcome(
            List<Job> jobs,
            String status,
            boolean degraded) {
    }
}
