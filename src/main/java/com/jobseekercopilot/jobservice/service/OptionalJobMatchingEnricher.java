package com.jobseekercopilot.jobservice.service;

import com.jobseekercopilot.jobservice.config.JobSearchResilienceProperties;
import com.jobseekercopilot.jobservice.model.dto.Job;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
                    () -> jobMatchingClient.enrichJobs(userId, providerJobs)));
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
                    result == null ? providerJobs : result,
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

    record MatchingOutcome(
            List<Job> jobs,
            String status,
            boolean degraded) {
    }
}
