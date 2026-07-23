package com.jobseekercopilot.jobservice.controller;

import com.jobseekercopilot.jobservice.model.dto.ErrorResponse;
import com.jobseekercopilot.jobservice.model.dto.JobSearchRequest;
import com.jobseekercopilot.jobservice.model.dto.ReedJobSearchResponse;
import com.jobseekercopilot.jobservice.service.JobMatchingClient;
import com.jobseekercopilot.jobservice.service.JobSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api/jobs")
@Tag(name = "Job Search", description = "Endpoints for searching job listings")
public class JobSearchController {

    private final JobSearchService jobSearchService;

    public JobSearchController(JobSearchService jobSearchService) {
        this.jobSearchService = jobSearchService;
    }

    @PostMapping("/search")
    @Operation(
            summary = "Search for jobs",
            description = "Searches for jobs based on user aspirations and work preferences. " +
                          "Requires X-User-Id header for authentication. " +
                          "This endpoint validates the request, transforms it to the external API format, " +
                          "and returns matching job listings."
    )
    public ResponseEntity<ReedJobSearchResponse> searchJobs(
            @Parameter(description = "User ID for authentication", example = "user-123") @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Parameter(description = "Job search criteria based on user profile") @RequestBody JobSearchRequest request) {

        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(null);
        }

        ReedJobSearchResponse response = jobSearchService.searchJobs(userId, request);
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        ErrorResponse error = new ErrorResponse("INVALID_REQUEST", ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(JobSearchService.DownstreamServiceUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ResponseEntity<ErrorResponse> handleServiceUnavailable(JobSearchService.DownstreamServiceUnavailableException ex) {
        ErrorResponse error = new ErrorResponse("SERVICE_UNAVAILABLE", "Job search service is temporarily unavailable");
        return new ResponseEntity<>(error, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(JobMatchingClient.JobMatchingUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ResponseEntity<ErrorResponse> handleJobMatchingUnavailable(JobMatchingClient.JobMatchingUnavailableException ex) {
        ErrorResponse error = new ErrorResponse("SERVICE_UNAVAILABLE", "Job matching service is temporarily unavailable");
        return new ResponseEntity<>(error, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<ErrorResponse> handleGenericError(Exception ex) {
        ErrorResponse error = new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred");
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
