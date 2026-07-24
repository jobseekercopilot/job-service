package com.jobseekercopilot.jobservice.config;

import com.jobseekercopilot.generated.jsearchgateway.api.JSearchJobsApi;
import com.jobseekercopilot.generated.jsearchgateway.client.ApiClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JSearchGatewayApiConfig {

    @Bean
    JSearchJobsApi jSearchJobsApi(
            @Value("${services.jsearch-gateway.url:http://jsearch-gateway:8102}") String basePath) {
        ApiClient apiClient = new ApiClient();
        apiClient.setBasePath(basePath);
        return new JSearchJobsApi(apiClient);
    }
}
