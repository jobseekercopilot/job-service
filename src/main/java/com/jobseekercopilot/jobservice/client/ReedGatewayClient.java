package com.jobseekercopilot.jobservice.client;

import com.jobseekercopilot.jobservice.model.dto.Job;
import com.jobseekercopilot.jobservice.model.dto.ReedJobSearchRequest;
import com.jobseekercopilot.jobservice.model.dto.ReedJobSearchResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Component
public class ReedGatewayClient {

    private final RestTemplate restTemplate;
    private final String reedGatewayUrl;

    public ReedGatewayClient(RestTemplate restTemplate,
                             @Value("${services.reed-gateway.url:http://localhost:8083}") String reedGatewayUrl) {
        this.restTemplate = restTemplate;
        this.reedGatewayUrl = reedGatewayUrl;
    }

    public ReedJobSearchResponse searchJobs(String userId, ReedJobSearchRequest request) {
        String url = reedGatewayUrl + "/api/jobs/external-search";

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", userId);

        HttpEntity<ReedJobSearchRequest> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<ReedJobSearchResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    ReedJobSearchResponse.class
            );

            return response.getBody();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 400) {
                throw new IllegalArgumentException("Invalid request to reed-gateway: " + e.getResponseBodyAsString());
            } else if (e.getStatusCode().value() == 401) {
                throw new IllegalArgumentException("Unauthorized: Missing or invalid X-User-Id");
            } else if (e.getStatusCode().value() == 422) {
                return new ReedJobSearchResponse(List.of(), 0, request.getPage() != null ? request.getPage() : 1, request.getPageSize() != null ? request.getPageSize() : 20);
            }
            throw new RuntimeException("Client error from reed-gateway: " + e.getStatusCode());
        } catch (HttpServerErrorException e) {
            if (e.getStatusCode().value() == 503) {
                throw new ServiceUnavailableException("Reed gateway is temporarily unavailable");
            }
            throw new RuntimeException("Server error from reed-gateway: " + e.getStatusCode());
        } catch (ResourceAccessException e) {
            throw new ServiceUnavailableException("Reed gateway is unreachable", e);
        }
    }

    public static class ServiceUnavailableException extends RuntimeException {
        public ServiceUnavailableException(String message) {
            super(message);
        }

        public ServiceUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}