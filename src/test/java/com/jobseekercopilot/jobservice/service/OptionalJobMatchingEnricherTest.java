package com.jobseekercopilot.jobservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jobseekercopilot.jobservice.config.JobSearchResilienceProperties;
import com.jobseekercopilot.jobservice.logging.CorrelationIdFilter;
import com.jobseekercopilot.jobservice.model.dto.Job;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class OptionalJobMatchingEnricherTest {

    private final JobMatchingClient client = mock(JobMatchingClient.class);
    private ThreadPoolExecutor executor;

    @AfterEach
    void shutdownExecutor() {
        MDC.clear();
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void returnsEnrichedJobsAndPropagatesCorrelationContext() {
        executor = executor(1, 1);
        AtomicReference<String> downstreamCorrelation = new AtomicReference<>();
        Job enriched = job("enriched");
        when(client.enrichJobs(eq("user-1"), anyList()))
                .thenAnswer(invocation -> {
                    downstreamCorrelation.set(
                            MDC.get(CorrelationIdFilter.MDC_KEY));
                    return List.of(enriched);
                });
        OptionalJobMatchingEnricher enricher = enricher(300);

        MDC.put(CorrelationIdFilter.MDC_KEY, "matching-correlation");
        var outcome = enricher.enrich(
                "user-1", List.of(job("provider")), deadlineAfter(500));

        assertThat(outcome.status()).isEqualTo("COMPLETE");
        assertThat(outcome.degraded()).isFalse();
        assertThat(outcome.jobs()).containsExactly(enriched);
        assertThat(downstreamCorrelation).hasValue("matching-correlation");
    }

    @Test
    void returnsProviderJobsWhenMatchingFails() {
        executor = executor(1, 1);
        Job providerJob = job("provider");
        when(client.enrichJobs(eq("user-1"), anyList()))
                .thenThrow(new JobMatchingClient.JobMatchingUnavailableException(
                        "unavailable",
                        new IllegalStateException("downstream detail")));

        var outcome = enricher(300).enrich(
                "user-1", List.of(providerJob), deadlineAfter(500));

        assertThat(outcome.status()).isEqualTo("UNAVAILABLE");
        assertThat(outcome.degraded()).isTrue();
        assertThat(outcome.jobs()).containsExactly(providerJob);
    }

    @Test
    void cancelsTimedOutMatchingAndReturnsProviderJobs() throws Exception {
        executor = executor(1, 1);
        CountDownLatch interrupted = new CountDownLatch(1);
        Job providerJob = job("provider");
        when(client.enrichJobs(eq("user-1"), anyList()))
                .thenAnswer(invocation -> {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException exception) {
                        interrupted.countDown();
                        Thread.currentThread().interrupt();
                    }
                    return invocation.getArgument(1);
                });

        long startedAt = System.nanoTime();
        var outcome = enricher(75).enrich(
                "user-1", List.of(providerJob), deadlineAfter(500));
        long durationMs = TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - startedAt);

        assertThat(durationMs).isLessThan(400);
        assertThat(interrupted.await(500, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(outcome.status()).isEqualTo("TIMED_OUT");
        assertThat(outcome.degraded()).isTrue();
        assertThat(outcome.jobs()).containsExactly(providerJob);
    }

    @Test
    void degradesImmediatelyWhenBoundedExecutorIsSaturated()
            throws Exception {
        executor = new ThreadPoolExecutor(
                1,
                1,
                0,
                TimeUnit.MILLISECONDS,
                new SynchronousQueue<>(),
                new ThreadPoolExecutor.AbortPolicy());
        CountDownLatch occupied = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        executor.submit(() -> {
            occupied.countDown();
            release.await();
            return null;
        });
        assertThat(occupied.await(500, TimeUnit.MILLISECONDS)).isTrue();
        Job providerJob = job("provider");

        var outcome = enricher(300).enrich(
                "user-1", List.of(providerJob), deadlineAfter(500));
        release.countDown();

        assertThat(outcome.status()).isEqualTo("SATURATED");
        assertThat(outcome.degraded()).isTrue();
        assertThat(outcome.jobs()).containsExactly(providerJob);
    }

    private OptionalJobMatchingEnricher enricher(long matchingTimeoutMs) {
        JobSearchResilienceProperties properties =
                new JobSearchResilienceProperties(
                        500,
                        300,
                        matchingTimeoutMs,
                        50,
                        1,
                        Math.max(0, executor.getQueue().remainingCapacity()));
        return new OptionalJobMatchingEnricher(client, executor, properties);
    }

    private ThreadPoolExecutor executor(int threads, int queueCapacity) {
        return new ThreadPoolExecutor(
                threads,
                threads,
                0,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                new ThreadPoolExecutor.AbortPolicy());
    }

    private Job job(String id) {
        Job job = new Job();
        job.setId(id);
        return job;
    }

    private long deadlineAfter(long milliseconds) {
        return System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(milliseconds);
    }
}
