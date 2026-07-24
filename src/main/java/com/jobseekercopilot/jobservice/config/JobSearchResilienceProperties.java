package com.jobseekercopilot.jobservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JobSearchResilienceProperties {

    private final long requestTimeoutMs;
    private final long providerTimeoutMs;
    private final long matchingTimeoutMs;
    private final long connectTimeoutMs;
    private final int executorThreads;
    private final int executorQueueCapacity;

    public JobSearchResilienceProperties(
            @Value("${job.search.resilience.request-timeout-ms:${JOB_SEARCH_REQUEST_TIMEOUT_MS:5000}}")
                    long requestTimeoutMs,
            @Value("${job.search.resilience.provider-timeout-ms:${JOB_SEARCH_PROVIDER_TIMEOUT_MS:2500}}")
                    long providerTimeoutMs,
            @Value("${job.search.resilience.matching-timeout-ms:${JOB_SEARCH_MATCHING_TIMEOUT_MS:1000}}")
                    long matchingTimeoutMs,
            @Value("${job.search.resilience.connect-timeout-ms:${JOB_SEARCH_CONNECT_TIMEOUT_MS:500}}")
                    long connectTimeoutMs,
            @Value("${job.search.resilience.executor-threads:${JOB_SEARCH_EXECUTOR_THREADS:8}}")
                    int executorThreads,
            @Value("${job.search.resilience.executor-queue-capacity:${JOB_SEARCH_EXECUTOR_QUEUE_CAPACITY:16}}")
                    int executorQueueCapacity) {
        if (requestTimeoutMs <= 0
                || providerTimeoutMs <= 0
                || matchingTimeoutMs <= 0
                || connectTimeoutMs <= 0
                || executorThreads <= 0
                || executorQueueCapacity < 0
                || providerTimeoutMs > requestTimeoutMs
                || matchingTimeoutMs > requestTimeoutMs) {
            throw new IllegalStateException("Job Search resilience configuration is invalid");
        }
        this.requestTimeoutMs = requestTimeoutMs;
        this.providerTimeoutMs = providerTimeoutMs;
        this.matchingTimeoutMs = matchingTimeoutMs;
        this.connectTimeoutMs = connectTimeoutMs;
        this.executorThreads = executorThreads;
        this.executorQueueCapacity = executorQueueCapacity;
    }

    public long getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    public long getProviderTimeoutMs() {
        return providerTimeoutMs;
    }

    public long getMatchingTimeoutMs() {
        return matchingTimeoutMs;
    }

    public long getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public int getExecutorThreads() {
        return executorThreads;
    }

    public int getExecutorQueueCapacity() {
        return executorQueueCapacity;
    }
}
