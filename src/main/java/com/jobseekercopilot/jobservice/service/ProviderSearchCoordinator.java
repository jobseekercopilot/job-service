package com.jobseekercopilot.jobservice.service;

import com.jobseekercopilot.jobservice.config.JobSearchResilienceProperties;
import com.jobseekercopilot.jobservice.model.dto.Job;
import com.jobseekercopilot.jobservice.model.dto.ProviderResultStatus;
import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

@Component
public class ProviderSearchCoordinator {
    private static final int MAX_RESULTS_PER_PROVIDER = 100;

    private static final Logger log =
            LoggerFactory.getLogger(ProviderSearchCoordinator.class);

    private final List<JobProviderAdapter> providerAdapters;
    private final ThreadPoolExecutor executor;
    private final JobSearchResilienceProperties resilience;

    public ProviderSearchCoordinator(
            List<JobProviderAdapter> providerAdapters,
            @Qualifier("jobSearchExecutor") ThreadPoolExecutor executor,
            JobSearchResilienceProperties resilience) {
        this.providerAdapters = providerAdapters;
        this.executor = executor;
        this.resilience = resilience;
    }

    ProviderFanOutResult search(
            String userId,
            JobSearchCriteria criteria,
            Set<String> selectedProviders,
            long requestDeadlineNanos) {
        List<ProviderWork> work = new ArrayList<>();

        for (JobProviderAdapter adapter : providerAdapters) {
            String provider = adapter.provider();
            if (!selectedProviders.isEmpty() && !selectedProviders.contains(provider)) {
                log.debug("Provider {} skipped by request selection", provider);
                continue;
            }
            if (!adapter.isEnabled()) {
                log.info("Provider {} disabled", provider);
                work.add(ProviderWork.immediate(new ProviderResultStatus(
                        provider,
                        "DISABLED",
                        0,
                        null)));
                continue;
            }

            long submittedAt = System.nanoTime();
            try {
                Future<List<Job>> future = executor.submit(
                        DownstreamTaskContext.withCurrentMdc(
                                () -> adapter.search(userId, criteria)));
                work.add(ProviderWork.submitted(provider, submittedAt, future));
            } catch (RejectedExecutionException exception) {
                log.warn("Provider {} rejected by bounded executor", provider);
                work.add(ProviderWork.immediate(new ProviderResultStatus(
                        provider,
                        "SATURATED",
                        0,
                        "Provider capacity unavailable")));
            }
        }

        List<Job> rawJobs = new ArrayList<>();
        List<ProviderResultStatus> statuses = new ArrayList<>();
        boolean anyAttempted = false;
        boolean anySuccess = false;
        boolean complete = true;

        try {
            for (ProviderWork providerWork : work) {
                if (providerWork.immediateStatus() != null) {
                    ProviderResultStatus status = providerWork.immediateStatus();
                    statuses.add(status);
                    if (!"DISABLED".equals(status.getStatus())) {
                        anyAttempted = true;
                        complete = false;
                    }
                    continue;
                }

                anyAttempted = true;
                ProviderCallResult result = await(providerWork, requestDeadlineNanos);
                statuses.add(result.status());
                rawJobs.addAll(result.jobs());
                if ("SUCCESS".equals(result.status().getStatus())) {
                    anySuccess = true;
                } else {
                    complete = false;
                }
            }
        } catch (ProviderCoordinationException exception) {
            work.stream()
                    .filter(providerWork -> providerWork.future() != null)
                    .map(ProviderWork::future)
                    .filter(future -> !future.isDone())
                    .forEach(future -> future.cancel(true));
            throw exception;
        }

        return new ProviderFanOutResult(
                List.copyOf(rawJobs),
                List.copyOf(statuses),
                anyAttempted,
                anySuccess,
                complete);
    }

    private ProviderCallResult await(
            ProviderWork work,
            long requestDeadlineNanos) {
        long providerDeadline = work.submittedAtNanos()
                + TimeUnit.MILLISECONDS.toNanos(resilience.getProviderTimeoutMs());
        long deadline = Math.min(providerDeadline, requestDeadlineNanos);
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0 && !work.future().isDone()) {
            work.future().cancel(true);
            return failed(work.provider(), "TIMED_OUT", "Provider timed out");
        }

        try {
            List<Job> jobs = work.future().isDone()
                    ? work.future().get()
                    : work.future().get(remaining, TimeUnit.NANOSECONDS);
            List<Job> providerJobs = jobs == null ? List.of() : jobs;
            List<Job> safeJobs = providerJobs.stream()
                    .limit(MAX_RESULTS_PER_PROVIDER)
                    .toList();
            log.info("Provider {} returned rawCount={} acceptedCount={}",
                    work.provider(),
                    providerJobs.size(),
                    safeJobs.size());
            return new ProviderCallResult(
                    safeJobs,
                    new ProviderResultStatus(
                            work.provider(),
                            "SUCCESS",
                            providerJobs.size(),
                            null));
        } catch (TimeoutException exception) {
            work.future().cancel(true);
            log.warn("Provider {} timed out", work.provider());
            return failed(work.provider(), "TIMED_OUT", "Provider timed out");
        } catch (InterruptedException exception) {
            work.future().cancel(true);
            Thread.currentThread().interrupt();
            throw new ProviderCoordinationException(
                    "Provider coordination interrupted",
                    exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            ProviderResultStatus status = classifyFailure(work.provider(), cause);
            log.warn("Provider {} failed category={} error={}",
                    work.provider(),
                    status.getStatus(),
                    cause.getClass().getSimpleName());
            return new ProviderCallResult(List.of(), status);
        }
    }

    private ProviderResultStatus classifyFailure(String provider, Throwable cause) {
        Throwable current = cause;
        while (current != null) {
            if (current instanceof SocketTimeoutException
                    || current instanceof HttpConnectTimeoutException) {
                return status(provider, "TIMED_OUT", "Provider timed out");
            }
            if (current instanceof RestClientResponseException responseException) {
                if (responseException.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    return status(
                            provider,
                            "RATE_LIMITED",
                            "Provider rate limit reached");
                }
                if (responseException.getStatusCode() == HttpStatus.UNAUTHORIZED
                        || responseException.getStatusCode() == HttpStatus.FORBIDDEN) {
                    return status(
                            provider,
                            "CONFIGURATION_ERROR",
                            "Provider configuration rejected");
                }
                if (responseException.getStatusCode().is4xxClientError()) {
                    return status(
                            provider,
                            "REJECTED",
                            "Provider request rejected");
                }
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return status(
                provider,
                "UNAVAILABLE",
                "Provider temporarily unavailable");
    }

    private ProviderCallResult failed(
            String provider,
            String category,
            String message) {
        return new ProviderCallResult(
                List.of(),
                status(provider, category, message));
    }

    private ProviderResultStatus status(
            String provider,
            String category,
            String message) {
        return new ProviderResultStatus(provider, category, 0, message);
    }

    record ProviderFanOutResult(
            List<Job> jobs,
            List<ProviderResultStatus> providerResults,
            boolean anyAttempted,
            boolean anySuccess,
            boolean complete) {
    }

    private record ProviderCallResult(
            List<Job> jobs,
            ProviderResultStatus status) {
    }

    private record ProviderWork(
            String provider,
            long submittedAtNanos,
            Future<List<Job>> future,
            ProviderResultStatus immediateStatus) {

        static ProviderWork submitted(
                String provider,
                long submittedAtNanos,
                Future<List<Job>> future) {
            return new ProviderWork(provider, submittedAtNanos, future, null);
        }

        static ProviderWork immediate(ProviderResultStatus status) {
            return new ProviderWork(status.getProvider(), 0, null, status);
        }
    }

    static class ProviderCoordinationException extends RuntimeException {
        ProviderCoordinationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
