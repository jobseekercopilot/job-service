package com.jobseekercopilot.jobservice.service;

import com.jobseekercopilot.jobservice.client.ReedGatewayClient;
import com.jobseekercopilot.jobservice.model.dto.Aspirations;
import com.jobseekercopilot.jobservice.model.dto.JobSearchRequest;
import com.jobseekercopilot.jobservice.model.dto.ReedJobSearchRequest;
import com.jobseekercopilot.jobservice.model.dto.ReedJobSearchResponse;
import com.jobseekercopilot.jobservice.model.dto.SalaryExpectation;
import com.jobseekercopilot.jobservice.model.dto.WorkPreferences;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class JobSearchService {

    private static final int DEFAULT_DISTANCE = 25;
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_DISTANCE = 100;

    private final ReedGatewayClient reedGatewayClient;

    public JobSearchService(ReedGatewayClient reedGatewayClient) {
        this.reedGatewayClient = reedGatewayClient;
    }

    public ReedJobSearchResponse searchJobs(String userId, JobSearchRequest request) {
        validateRequest(request);

        ReedJobSearchRequest reedRequest = transformToReedRequest(request);

        return reedGatewayClient.searchJobs(userId, reedRequest);
    }

    private void validateRequest(JobSearchRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }

        Aspirations aspirations = request.getAspirations();
        if (aspirations == null) {
            throw new IllegalArgumentException("Missing required field: aspirations");
        }

        if (aspirations.getDesiredRoles() == null || aspirations.getDesiredRoles().isEmpty()) {
            throw new IllegalArgumentException("Missing required field: aspirations.desiredRoles");
        }

        if (aspirations.getLocations() == null || aspirations.getLocations().isEmpty()) {
            throw new IllegalArgumentException("Missing required field: aspirations.locations");
        }

        WorkPreferences workPreferences = request.getWorkPreferences();
        if (workPreferences != null && workPreferences.getEmploymentType() != null) {
            for (String employmentType : workPreferences.getEmploymentType()) {
                validateEmploymentType(employmentType);
            }
        }

        if (aspirations.getSalaryExpectation() != null) {
            SalaryExpectation salary = aspirations.getSalaryExpectation();
            if (salary.getMin() != null && salary.getMax() != null && salary.getMin() > salary.getMax()) {
                throw new IllegalArgumentException("Invalid salary range: min cannot be greater than max");
            }
        }
    }

    private void validateEmploymentType(String employmentType) {
        if (employmentType == null || employmentType.isBlank()) {
            return;
        }
        String normalized = employmentType.trim().toUpperCase();
        boolean valid = switch (normalized) {
            case "FULL_TIME", "PART_TIME", "CONTRACT", "TEMPORARY" -> true;
            default -> false;
        };
        if (!valid) {
            throw new IllegalArgumentException("Invalid employment type: " + employmentType);
        }
    }

    private ReedJobSearchRequest transformToReedRequest(JobSearchRequest request) {
        ReedJobSearchRequest reedRequest = new ReedJobSearchRequest();

        Aspirations aspirations = request.getAspirations();
        WorkPreferences workPreferences = request.getWorkPreferences();

        List<String> keywords = new ArrayList<>(aspirations.getDesiredRoles());
        if (keywords.isEmpty()) {
            throw new IllegalArgumentException("At least one desired role is required");
        }
        reedRequest.setKeywords(keywords);

        if (aspirations.getLocations() != null && !aspirations.getLocations().isEmpty()) {
            reedRequest.setLocation(aspirations.getLocations().get(0));
        } else {
            throw new IllegalArgumentException("At least one location is required");
        }

        reedRequest.setDistance(DEFAULT_DISTANCE);

        if (workPreferences != null && workPreferences.getEmploymentType() != null) {
            reedRequest.setEmploymentType(workPreferences.getEmploymentType());
        }

        if (aspirations.getSalaryExpectation() != null) {
            SalaryExpectation salary = aspirations.getSalaryExpectation();
            reedRequest.setSalaryMin(salary.getMin());
            reedRequest.setSalaryMax(salary.getMax());
            reedRequest.setCurrency(salary.getCurrency());
        }

        reedRequest.setPage(DEFAULT_PAGE);
        reedRequest.setPageSize(DEFAULT_PAGE_SIZE);

        return reedRequest;
    }
}