package com.jobseekercopilot.jobservice.config;

import com.jobseekercopilot.generated.adzunagateway.api.AdzunaJobsApi;
import com.jobseekercopilot.generated.adzunagateway.client.ApiClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AdzunaGatewayApiConfig {

    @Bean
    AdzunaJobsApi adzunaJobsApi(
            @Value("${services.adzuna-gateway.url:http://adzuna-gateway:8101}")
                    String basePath,
            @Qualifier("providerRestTemplate") RestTemplate restTemplate) {
        ApiClient apiClient = new ApiClient(restTemplate);
        apiClient.setBasePath(basePath);
        return new AdzunaJobsApi(apiClient);
    }
}
