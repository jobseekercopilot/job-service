package com.jobseekercopilot.jobservice.config;

import com.jobseekercopilot.generated.reedgateway.api.ReedJobsApi;
import com.jobseekercopilot.generated.reedgateway.client.ApiClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class ReedGatewayApiConfig {

    @Bean
    ReedJobsApi reedJobsApi(
            @Value("${services.reed-gateway.url:http://localhost:8087}")
                    String basePath,
            @Qualifier("providerRestTemplate") RestTemplate restTemplate) {
        ApiClient apiClient = new ApiClient(restTemplate);
        apiClient.setBasePath(basePath);
        return new ReedJobsApi(apiClient);
    }
}
