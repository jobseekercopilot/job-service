package com.jobseekercopilot.jobservice.service;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jobseekercopilot.jobservice.config.JobSearchResilienceProperties;
import com.jobseekercopilot.jobservice.logging.CorrelationIdFilter;
import com.jobseekercopilot.jobservice.model.dto.Job;
import com.jobseekercopilot.jobservice.model.dto.JobSearchRequest;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

class ProviderSearchCoordinatorTest {

    private ThreadPoolExecutor executor;

    @AfterEach
    void shutdownExecutor() {
        MDC.clear();
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void runsProvidersConcurrentlyInStableOrderAndPropagatesCorrelationContext()
            throws Exception {
        executor = executor(2, 2);
        CountDownLatch bothStarted = new CountDownLatch(2);
        AtomicReference<String> reedCorrelation = new AtomicReference<>();
        AtomicReference<String> adzunaCorrelation = new AtomicReference<>();
        JobProviderAdapter reed = adapter("REED", () -> {
            reedCorrelation.set(MDC.get(CorrelationIdFilter.MDC_KEY));
            bothStarted.countDown();
            if (!bothStarted.await(500, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Providers did not run concurrently");
            }
            return List.of(job("reed-1", "REED"));
        });
        JobProviderAdapter adzuna = adapter("ADZUNA", () -> {
            adzunaCorrelation.set(MDC.get(CorrelationIdFilter.MDC_KEY));
            bothStarted.countDown();
            if (!bothStarted.await(500, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Providers did not run concurrently");
            }
            return List.of(job("adzuna-1", "ADZUNA"));
        });
        ProviderSearchCoordinator coordinator = coordinator(
                List.of(reed, adzuna), 1000, 700);

        MDC.put(CorrelationIdFilter.MDC_KEY, "search-07-correlation");
        var result = coordinator.search(
                "user-1", criteria(), Set.of(), deadlineAfter(1000));

        assertThat(result.jobs())
                .extracting(Job::getId)
                .containsExactly("reed-1", "adzuna-1");
        assertThat(result.providerResults())
                .extracting("provider", "status")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("REED", "SUCCESS"),
                        org.assertj.core.groups.Tuple.tuple("ADZUNA", "SUCCESS"));
        assertThat(reedCorrelation).hasValue("search-07-correlation");
        assertThat(adzunaCorrelation).hasValue("search-07-correlation");
        assertThat(result.providerResults().get(0).getDataProvenance().getRetrievedAtUtc())
                .isEqualTo(java.time.OffsetDateTime.parse("2026-08-13T08:15:30Z"));
        assertThat(result.providerResults().get(0).getDataProvenance().getServedAtUtc())
                .isEqualTo(java.time.OffsetDateTime.parse("2026-08-13T08:15:30Z"));
        assertThat(result.complete()).isTrue();
    }

    @Test
    void treatsHealthyEmptyProviderAsSuccessfulCompleteSearch() {
        executor = executor(1, 1);
        JobProviderAdapter reed = adapter("REED", List::of);
        ProviderSearchCoordinator coordinator = coordinator(
                List.of(reed), 500, 300);

        var result = coordinator.search(
                "user-1", criteria(), Set.of(), deadlineAfter(500));

        assertThat(result.jobs()).isEmpty();
        assertThat(result.providerResults())
                .singleElement()
                .satisfies(providerResult -> {
                    assertThat(providerResult.getProvider()).isEqualTo("REED");
                    assertThat(providerResult.getStatus()).isEqualTo("SUCCESS");
                    assertThat(providerResult.getRawResultCount()).isZero();
                    assertThat(providerResult.getErrorMessage()).isNull();
                });
        assertThat(result.anyAttempted()).isTrue();
        assertThat(result.anySuccess()).isTrue();
        assertThat(result.complete()).isTrue();
    }

    @Test
    void capsEachProviderWindowWhilePreservingItsRawResultCount() {
        executor = executor(1, 1);
        JobProviderAdapter reed = adapter(
                "REED",
                () -> java.util.stream.IntStream.range(0, 125)
                        .mapToObj(index -> job("reed-" + index, "REED"))
                        .toList());
        ProviderSearchCoordinator coordinator = coordinator(
                List.of(reed), 500, 300);

        var result = coordinator.search(
                "user-1", criteria(), Set.of(), deadlineAfter(500));

        assertThat(result.jobs()).hasSize(100);
        assertThat(result.jobs().get(0).getId()).isEqualTo("reed-0");
        assertThat(result.jobs().get(99).getId()).isEqualTo("reed-99");
        assertThat(result.providerResults())
                .singleElement()
                .satisfies(providerResult -> {
                    assertThat(providerResult.getStatus()).isEqualTo("SUCCESS");
                    assertThat(providerResult.getRawResultCount()).isEqualTo(125);
                });
    }

    @Test
    void cancelsSlowProviderWithoutDiscardingHealthyResults() throws Exception {
        executor = executor(2, 2);
        CountDownLatch interrupted = new CountDownLatch(1);
        JobProviderAdapter slow = adapter("REED", () -> {
            try {
                Thread.sleep(5000);
                return List.of();
            } catch (InterruptedException exception) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
                return List.of();
            }
        });
        JobProviderAdapter healthy = adapter(
                "ADZUNA",
                () -> List.of(job("adzuna-1", "ADZUNA")));
        ProviderSearchCoordinator coordinator = coordinator(
                List.of(slow, healthy), 500, 100);

        long startedAt = System.nanoTime();
        var result = coordinator.search(
                "user-1", criteria(), Set.of(), deadlineAfter(500));
        long durationMs = TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - startedAt);

        assertThat(durationMs).isLessThan(450);
        assertThat(interrupted.await(500, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(result.jobs()).extracting(Job::getId).containsExactly("adzuna-1");
        assertThat(result.providerResults())
                .extracting("provider", "status")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("REED", "TIMED_OUT"),
                        org.assertj.core.groups.Tuple.tuple("ADZUNA", "SUCCESS"));
        assertThat(result.anySuccess()).isTrue();
        assertThat(result.complete()).isFalse();
    }

    @Test
    void rejectsExcessWorkAtTheBoundedCapacityBoundary() {
        executor = new ThreadPoolExecutor(
                1,
                1,
                0,
                TimeUnit.MILLISECONDS,
                new SynchronousQueue<>(),
                new ThreadPoolExecutor.AbortPolicy());
        JobProviderAdapter first = adapter("REED", () -> {
            Thread.sleep(75);
            return List.of(job("reed-1", "REED"));
        });
        JobProviderAdapter excess = adapter(
                "ADZUNA",
                () -> List.of(job("adzuna-1", "ADZUNA")));
        ProviderSearchCoordinator coordinator = coordinator(
                List.of(first, excess), 600, 400);

        var result = coordinator.search(
                "user-1", criteria(), Set.of(), deadlineAfter(600));

        assertThat(result.providerResults())
                .extracting("provider", "status")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("REED", "SUCCESS"),
                        org.assertj.core.groups.Tuple.tuple("ADZUNA", "SATURATED"));
        assertThat(result.jobs()).extracting(Job::getId).containsExactly("reed-1");
        assertThat(result.complete()).isFalse();
    }

    @Test
    void exposesStableFailureTaxonomyWithoutRawDownstreamMessages() {
        executor = executor(5, 5);
        JobProviderAdapter rateLimited = failingAdapter(
                "RATE",
                HttpClientErrorException.create(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "secret rate response",
                        HttpHeaders.EMPTY,
                        new byte[0],
                        UTF_8));
        JobProviderAdapter configuration = failingAdapter(
                "AUTH",
                HttpClientErrorException.create(
                        HttpStatus.UNAUTHORIZED,
                        "secret auth response",
                        HttpHeaders.EMPTY,
                        new byte[0],
                        UTF_8));
        JobProviderAdapter rejected = failingAdapter(
                "REJECTED",
                HttpClientErrorException.create(
                        HttpStatus.BAD_REQUEST,
                        "secret request response",
                        HttpHeaders.EMPTY,
                        new byte[0],
                        UTF_8));
        JobProviderAdapter timedOut = failingAdapter(
                "TIMEOUT",
                new ResourceAccessException(
                        "secret socket detail",
                        new SocketTimeoutException("secret timeout detail")));
        JobProviderAdapter unavailable = failingAdapter(
                "DOWN",
                new IllegalStateException("secret provider detail"));
        ProviderSearchCoordinator coordinator = coordinator(
                List.of(rateLimited, configuration, rejected, timedOut, unavailable),
                500,
                300);

        var result = coordinator.search(
                "user-1", criteria(), Set.of(), deadlineAfter(500));

        assertThat(result.providerResults())
                .extracting("provider", "status", "errorMessage")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "RATE", "RATE_LIMITED", "Provider rate limit reached"),
                        org.assertj.core.groups.Tuple.tuple(
                                "AUTH", "CONFIGURATION_ERROR", "Provider configuration rejected"),
                        org.assertj.core.groups.Tuple.tuple(
                                "REJECTED", "REJECTED", "Provider request rejected"),
                        org.assertj.core.groups.Tuple.tuple(
                                "TIMEOUT", "TIMED_OUT", "Provider timed out"),
                        org.assertj.core.groups.Tuple.tuple(
                                "DOWN", "UNAVAILABLE", "Provider temporarily unavailable"));
        assertThat(result.providerResults())
                .extracting(resultStatus -> resultStatus.getErrorMessage())
                .allMatch(message -> !message.contains("secret"));
    }

    @Test
    void boundsExecutorThreadsAndQueueUnderConcurrentSearchLoad()
            throws Exception {
        executor = executor(4, 4);
        CountDownLatch callersReady = new CountDownLatch(12);
        JobProviderAdapter provider = adapter("REED", () -> {
            Thread.sleep(75);
            return List.of(job("reed-1", "REED"));
        });
        ProviderSearchCoordinator coordinator = coordinator(
                List.of(provider), 1000, 700);
        ExecutorService callers = Executors.newFixedThreadPool(12);
        try {
            List<Callable<ProviderSearchCoordinator.ProviderFanOutResult>> calls =
                    java.util.stream.IntStream.range(0, 12)
                            .<Callable<ProviderSearchCoordinator.ProviderFanOutResult>>mapToObj(index -> () -> {
                                callersReady.countDown();
                                if (!callersReady.await(500, TimeUnit.MILLISECONDS)) {
                                    throw new IllegalStateException("Load callers did not start together");
                                }
                                return coordinator.search(
                                        "user-" + index,
                                        criteria(),
                                        Set.of(),
                                        deadlineAfter(1000));
                            })
                            .toList();

            List<Future<ProviderSearchCoordinator.ProviderFanOutResult>> futures =
                    callers.invokeAll(calls);
            List<ProviderSearchCoordinator.ProviderFanOutResult> results =
                    futures.stream().map(future -> {
                        try {
                            return future.get();
                        } catch (Exception exception) {
                            throw new AssertionError(exception);
                        }
                    }).toList();

            assertThat(results)
                    .flatExtracting(ProviderSearchCoordinator.ProviderFanOutResult::providerResults)
                    .extracting(resultStatus -> resultStatus.getStatus())
                    .containsOnly("SUCCESS", "SATURATED")
                    .contains("SUCCESS", "SATURATED");
            assertThat(executor.getLargestPoolSize()).isEqualTo(4);
            assertThat(executor.getQueue().size()).isZero();
        } finally {
            callers.shutdownNow();
        }
    }

    private ProviderSearchCoordinator coordinator(
            List<JobProviderAdapter> adapters,
            long requestTimeoutMs,
            long providerTimeoutMs) {
        JobSearchResilienceProperties properties =
                new JobSearchResilienceProperties(
                        requestTimeoutMs,
                        providerTimeoutMs,
                        Math.min(100, requestTimeoutMs),
                        50,
                        Math.max(1, executor.getMaximumPoolSize()),
                        Math.max(0, executor.getQueue().remainingCapacity()));
        ProviderModeResolver modeResolver = mock(ProviderModeResolver.class);
        when(modeResolver.resolve(anyString()))
                .thenReturn(ProviderModeResolver.ProviderModeSnapshot.unknown());
        return new ProviderSearchCoordinator(
                adapters,
                executor,
                properties,
                modeResolver,
                Clock.fixed(
                        Instant.parse("2026-08-13T08:15:30Z"),
                        ZoneOffset.UTC));
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

    private JobProviderAdapter adapter(String provider, SearchCall searchCall) {
        return new JobProviderAdapter() {
            @Override
            public String provider() {
                return provider;
            }

            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public List<Job> search(String userId, JobSearchCriteria criteria) {
                try {
                    return searchCall.search();
                } catch (RuntimeException exception) {
                    throw exception;
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            }
        };
    }

    private JobProviderAdapter failingAdapter(
            String provider,
            RuntimeException failure) {
        return adapter(provider, () -> {
            throw failure;
        });
    }

    private JobSearchCriteria criteria() {
        return new JobSearchCriteria(
                new JobSearchRequest(),
                "Developer",
                "London",
                25,
                List.of(),
                null,
                null,
                "GBP",
                false);
    }

    private Job job(String id, String provider) {
        Job job = new Job();
        job.setId(id);
        job.setProvider(provider);
        return job;
    }

    private long deadlineAfter(long milliseconds) {
        return System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(milliseconds);
    }

    @FunctionalInterface
    private interface SearchCall {
        List<Job> search() throws Exception;
    }
}
