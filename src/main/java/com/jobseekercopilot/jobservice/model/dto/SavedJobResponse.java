package com.jobseekercopilot.jobservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record SavedJobResponse(
        UUID savedJobId,
        String canonicalJobId,
        String canonicalSchemaVersion,
        long snapshotVersion,
        @Schema(pattern = "^sha256:[a-f0-9]{64}$")
        String contentVersion,
        @Schema(pattern = "^[a-f0-9]{64}$")
        String contentSha256,
        Instant capturedAt,
        Instant sourceRetrievedAt,
        @Schema(allowableValues = {"SNAPSHOT", "EXPIRED_SNAPSHOT"})
        String sourceState,
        Instant savedAt,
        Instant updatedAt,
        Job job) {
}
