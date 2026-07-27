package com.jobseekercopilot.jobservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "saved_job_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SavedJobSnapshot {

    @Id
    private UUID id;

    @Column(name = "saved_job_id", nullable = false)
    private UUID savedJobId;

    @Column(name = "snapshot_version", nullable = false)
    private long snapshotVersion;

    @Column(name = "canonical_schema_version", nullable = false, length = 32)
    private String canonicalSchemaVersion;

    @Column(name = "content_sha256", nullable = false, length = 64)
    private String contentSha256;

    @Column(name = "content_version", nullable = false, length = 72)
    private String contentVersion;

    @Column(name = "snapshot_json", nullable = false, columnDefinition = "TEXT")
    private String snapshotJson;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @Column(name = "source_retrieved_at")
    private Instant sourceRetrievedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static SavedJobSnapshot create(
            UUID id,
            UUID savedJobId,
            long snapshotVersion,
            String canonicalSchemaVersion,
            String contentSha256,
            String snapshotJson,
            Instant capturedAt,
            Instant sourceRetrievedAt) {
        SavedJobSnapshot snapshot = new SavedJobSnapshot();
        snapshot.id = id;
        snapshot.savedJobId = savedJobId;
        snapshot.snapshotVersion = snapshotVersion;
        snapshot.canonicalSchemaVersion = canonicalSchemaVersion;
        snapshot.contentSha256 = contentSha256;
        snapshot.contentVersion = "sha256:" + contentSha256;
        snapshot.snapshotJson = snapshotJson;
        snapshot.capturedAt = capturedAt;
        snapshot.sourceRetrievedAt = sourceRetrievedAt;
        snapshot.createdAt = capturedAt;
        return snapshot;
    }
}
