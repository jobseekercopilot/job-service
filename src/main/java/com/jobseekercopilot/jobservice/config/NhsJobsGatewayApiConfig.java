package com.jobseekercopilot.jobservice.config;

import com.jobseekercopilot.generated.nhsjobsgateway.api.DefaultApi;
import com.jobseekercopilot.generated.nhsjobsgateway.client.ApiClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class NhsJobsGatewayApiConfig {

    @Bean
    DefaultApi nhsJobsApi(
            @Value("${services.nhs-jobs-gateway.url:"
                    + "http://nhs-jobs-gateway:8104}")
                    String basePath,
            @Qualifier("providerRestTemplate")
                    RestTemplate restTemplate) {
        ApiClient apiClient = new ApiClient(restTemplate);
        apiClient.setBasePath(basePath);
        return new DefaultApi(apiClient);
    }
}
