package com.jobseekercopilot.jobservice.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    @Qualifier("providerRestTemplate")
    public RestTemplate providerRestTemplate(
            RestTemplateBuilder builder,
            JobSearchResilienceProperties properties) {
        return builder
                .setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(properties.getProviderTimeoutMs()))
                .build();
    }

    @Bean
    @Qualifier("providerModeRestTemplate")
    public RestTemplate providerModeRestTemplate(
            RestTemplateBuilder builder,
            @Value("${job.search.resilience.provider-mode-timeout-ms:${JOB_SEARCH_PROVIDER_MODE_TIMEOUT_MS:250}}")
                    long timeoutMs) {
        if (timeoutMs <= 0 || timeoutMs > 1000) {
            throw new IllegalStateException(
                    "Provider mode timeout must be between 1 and 1000 ms");
        }
        Duration timeout = Duration.ofMillis(timeoutMs);
        return builder
                .setConnectTimeout(timeout)
                .setReadTimeout(timeout)
                .build();
    }

    @Bean
    @Qualifier("jobMatchingRestTemplate")
    public RestTemplate jobMatchingRestTemplate(
            RestTemplateBuilder builder,
            JobSearchResilienceProperties properties) {
        return builder
                .setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(properties.getMatchingTimeoutMs()))
                .build();
    }
}
