package com.jobseekercopilot.jobservice.config;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JobSearchExecutionConfig {

    @Bean(name = "jobSearchExecutor", destroyMethod = "shutdown")
    ThreadPoolExecutor jobSearchExecutor(JobSearchResilienceProperties properties) {
        int threads = properties.getExecutorThreads();
        BlockingQueue<Runnable> queue = properties.getExecutorQueueCapacity() == 0
                ? new SynchronousQueue<>()
                : new ArrayBlockingQueue<>(properties.getExecutorQueueCapacity());
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory threadFactory = task -> {
            Thread thread = new Thread(
                    task,
                    "job-search-downstream-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(
                threads,
                threads,
                0,
                TimeUnit.MILLISECONDS,
                queue,
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy());
    }
}
