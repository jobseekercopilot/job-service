package com.jobseekercopilot.jobservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.ThreadPoolExecutor;
import org.junit.jupiter.api.Test;

class JobSearchResilienceConfigTest {

    @Test
    void rejectsInvalidTimeoutAndCapacityConfiguration() {
        assertThatThrownBy(() -> properties(0, 100, 50, 50, 1, 1))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> properties(100, 101, 50, 50, 1, 1))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> properties(100, 50, 101, 50, 1, 1))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> properties(100, 50, 50, 50, 0, 1))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> properties(100, 50, 50, 50, 1, -1))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void createsFixedBoundedDaemonExecutor() {
        JobSearchResilienceProperties properties =
                properties(500, 300, 100, 50, 2, 3);
        ThreadPoolExecutor executor =
                new JobSearchExecutionConfig().jobSearchExecutor(properties);
        try {
            assertThat(executor.getCorePoolSize()).isEqualTo(2);
            assertThat(executor.getMaximumPoolSize()).isEqualTo(2);
            assertThat(executor.getQueue().remainingCapacity()).isEqualTo(3);
            var thread = executor.getThreadFactory().newThread(() -> {
            });
            assertThat(thread.isDaemon()).isTrue();
            assertThat(thread.getName()).startsWith("job-search-downstream-");
        } finally {
            executor.shutdownNow();
        }
    }

    private JobSearchResilienceProperties properties(
            long request,
            long provider,
            long matching,
            long connect,
            int threads,
            int queue) {
        return new JobSearchResilienceProperties(
                request, provider, matching, connect, threads, queue);
    }
}
