package com.jobseekercopilot.jobservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "saved_jobs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SavedJob {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Column(name = "canonical_job_id", nullable = false, length = 128)
    private String canonicalJobId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SavedJobState state;

    @Column(name = "current_snapshot_id")
    private UUID currentSnapshotId;

    @Column(name = "current_snapshot_version", nullable = false)
    private long currentSnapshotVersion;

    @Column(name = "saved_at", nullable = false)
    private Instant savedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "unsaved_at")
    private Instant unsavedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    public static SavedJob create(
            UUID id,
            String userId,
            String canonicalJobId,
            Instant now) {
        SavedJob savedJob = new SavedJob();
        savedJob.id = id;
        savedJob.userId = userId;
        savedJob.canonicalJobId = canonicalJobId;
        savedJob.state = SavedJobState.ACTIVE;
        savedJob.savedAt = now;
        savedJob.updatedAt = now;
        return savedJob;
    }

    public void selectSnapshot(UUID snapshotId, long snapshotVersion, Instant now) {
        currentSnapshotId = snapshotId;
        currentSnapshotVersion = snapshotVersion;
        state = SavedJobState.ACTIVE;
        unsavedAt = null;
        updatedAt = now;
    }

    public void reactivate(Instant now) {
        state = SavedJobState.ACTIVE;
        savedAt = now;
        updatedAt = now;
        unsavedAt = null;
    }

    public void unsave(Instant now) {
        if (state == SavedJobState.UNSAVED) {
            return;
        }
        state = SavedJobState.UNSAVED;
        unsavedAt = now;
        updatedAt = now;
    }
}
