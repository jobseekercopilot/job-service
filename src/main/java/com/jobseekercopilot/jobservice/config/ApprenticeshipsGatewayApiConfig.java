package com.jobseekercopilot.jobservice.config;

import com.jobseekercopilot.generated.apprenticeshipsgateway.api.ApprenticeshipsApi;
import com.jobseekercopilot.generated.apprenticeshipsgateway.client.ApiClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class ApprenticeshipsGatewayApiConfig {
    @Bean ApprenticeshipsApi apprenticeshipsApi(@Value("${services.apprenticeships-gateway.url:http://apprenticeships-gateway:8105}") String basePath,
            @Qualifier("providerRestTemplate") RestTemplate restTemplate) {
        ApiClient client = new ApiClient(restTemplate); client.setBasePath(basePath); return new ApprenticeshipsApi(client);
    }
}
