package com.jobseekercopilot.jobservice.controller;

import com.jobseekercopilot.jobservice.model.dto.ErrorResponse;
import com.jobseekercopilot.jobservice.model.dto.Job;
import com.jobseekercopilot.jobservice.model.dto.JobSearchRequest;
import com.jobseekercopilot.jobservice.model.dto.ReedJobSearchResponse;
import com.jobseekercopilot.jobservice.service.JobSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
                          "Requires a signed end-user Bearer access token. The verified JWT subject " +
                          "is the only search identity.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ReedJobSearchResponse> searchJobs(
            @AuthenticationPrincipal Jwt accessToken,
            @Parameter(description = "Job search criteria based on user profile") @RequestBody JobSearchRequest request) {
        ReedJobSearchResponse response = jobSearchService.searchJobs(accessToken.getSubject(), request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{provider}/{externalJobId}")
    @Operation(
            summary = "Get provider job details",
            description = "Hydrates one selected job from its provider before document generation.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<Job> getJobDetails(
            @AuthenticationPrincipal Jwt accessToken,
            @PathVariable String provider,
            @PathVariable String externalJobId) {
        return ResponseEntity.ok(jobSearchService.getJobDetails(
                accessToken.getSubject(),
                provider,
                externalJobId));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        ErrorResponse error = new ErrorResponse("INVALID_REQUEST", ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(JobSearchService.JobNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<ErrorResponse> handleNotFound(
            JobSearchService.JobNotFoundException ex) {
        ErrorResponse error = new ErrorResponse("JOB_NOT_FOUND", ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(JobSearchService.DownstreamServiceUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ResponseEntity<ErrorResponse> handleServiceUnavailable(JobSearchService.DownstreamServiceUnavailableException ex) {
        ErrorResponse error = new ErrorResponse("SERVICE_UNAVAILABLE", "Job search service is temporarily unavailable");
        return new ResponseEntity<>(error, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<ErrorResponse> handleGenericError(Exception ex) {
        ErrorResponse error = new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred");
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
