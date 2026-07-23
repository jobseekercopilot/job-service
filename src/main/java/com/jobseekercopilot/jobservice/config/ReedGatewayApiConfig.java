package com.jobseekercopilot.jobservice.config;

import com.jobseekercopilot.generated.reedgateway.api.ReedJobsApi;
import com.jobseekercopilot.generated.reedgateway.client.ApiClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReedGatewayApiConfig {

    @Bean
    ReedJobsApi reedJobsApi(
            @Value("${services.reed-gateway.url:http://localhost:8087}") String basePath) {
        ApiClient apiClient = new ApiClient();
        apiClient.setBasePath(basePath);
        return new ReedJobsApi(apiClient);
    }
}
