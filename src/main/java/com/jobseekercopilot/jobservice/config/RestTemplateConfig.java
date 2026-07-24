package com.jobseekercopilot.jobservice.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.client.RestTemplateBuilder;
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
