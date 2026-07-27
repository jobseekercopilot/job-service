package com.jobseekercopilot.jobservice.controller;

import com.jobseekercopilot.jobservice.model.dto.ErrorResponse;
import com.jobseekercopilot.jobservice.model.dto.Job;
import com.jobseekercopilot.jobservice.model.dto.SavedJobPageResponse;
import com.jobseekercopilot.jobservice.model.dto.SavedJobResponse;
import com.jobseekercopilot.jobservice.service.SavedJobNotFoundException;
import com.jobseekercopilot.jobservice.service.SavedJobSaveOutcome;
import com.jobseekercopilot.jobservice.service.SavedJobSaveResult;
import com.jobseekercopilot.jobservice.service.SavedJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs/saved")
@Tag(
        name = "Saved Jobs",
        description = "Owner-scoped immutable canonical job snapshots")
@SecurityRequirement(name = "bearerAuth")
public class SavedJobController {

    private static final String OUTCOME_HEADER = "X-Saved-Job-Outcome";

    private final SavedJobService savedJobService;

    public SavedJobController(SavedJobService savedJobService) {
        this.savedJobService = savedJobService;
    }

    @PostMapping
    @Operation(
            summary = "Save a canonical job snapshot",
            description = """
                    Creates or reactivates one owner-scoped saved job. Replaying
                    identical canonical content returns the same immutable
                    snapshot; changed source content creates a new snapshot
                    version without deleting earlier provenance.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Saved job created"),
            @ApiResponse(
                    responseCode = "200",
                    description = "Existing saved job replayed, updated or reactivated"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid canonical snapshot",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SavedJobResponse> save(
            @AuthenticationPrincipal Jwt accessToken,
            @RequestBody Job job) {
        SavedJobSaveResult result =
                savedJobService.save(accessToken.getSubject(), job);
        return ResponseEntity
                .status(result.outcome() == SavedJobSaveOutcome.CREATED
                        ? HttpStatus.CREATED
                        : HttpStatus.OK)
                .header(OUTCOME_HEADER, result.outcome().name())
                .body(result.savedJob());
    }

    @GetMapping
    @Operation(summary = "List the current user's active saved jobs")
    public SavedJobPageResponse list(
            @AuthenticationPrincipal Jwt accessToken,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return savedJobService.list(
                accessToken.getSubject(),
                page,
                size);
    }

    @GetMapping("/{savedJobId}")
    @Operation(
            summary = "Retrieve an immutable owner-scoped saved job snapshot",
            description = """
                    Returns the current immutable canonical/source snapshot.
                    Missing, unsaved and other-user identifiers all return the
                    same not-found response.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Saved job found"),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Saved job not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public SavedJobResponse get(
            @AuthenticationPrincipal Jwt accessToken,
            @PathVariable UUID savedJobId) {
        return savedJobService.get(
                accessToken.getSubject(),
                savedJobId);
    }

    @DeleteMapping("/{savedJobId}")
    @Operation(
            summary = "Unsave a job",
            description = """
                    Idempotently removes the saved job from the owner's active
                    list while retaining immutable snapshot provenance for
                    recovery, audit and supported historical references.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Job is not active"),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> unsave(
            @AuthenticationPrincipal Jwt accessToken,
            @PathVariable UUID savedJobId) {
        savedJobService.unsave(
                accessToken.getSubject(),
                savedJobId);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> invalid(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(
                new ErrorResponse("INVALID_SAVED_JOB", exception.getMessage()));
    }

    @ExceptionHandler(SavedJobNotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                new ErrorResponse("SAVED_JOB_NOT_FOUND", "Saved job was not found."));
    }
}
