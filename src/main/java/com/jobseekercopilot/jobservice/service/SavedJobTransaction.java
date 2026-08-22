package com.jobseekercopilot.jobservice.service;

import com.jobseekercopilot.jobservice.entity.SavedJob;
import com.jobseekercopilot.jobservice.entity.SavedJobSnapshot;
import com.jobseekercopilot.jobservice.entity.SavedJobState;
import com.jobseekercopilot.jobservice.repository.SavedJobRepository;
import com.jobseekercopilot.jobservice.repository.SavedJobSnapshotRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SavedJobTransaction {

    private final SavedJobRepository savedJobRepository;
    private final SavedJobSnapshotRepository snapshotRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PersistenceResult save(
            UUID savedJobId,
            UUID snapshotId,
            String userId,
            String canonicalJobId,
            String canonicalSchemaVersion,
            String contentSha256,
            String snapshotJson,
            Instant capturedAt,
            Instant sourceRetrievedAt) {
        SavedJob savedJob = savedJobRepository
                .findForUpdate(userId, canonicalJobId)
                .orElse(null);
        boolean created = savedJob == null;
        if (created) {
            savedJob = SavedJob.create(
                    savedJobId,
                    userId,
                    canonicalJobId,
                    capturedAt);
            savedJobRepository.saveAndFlush(savedJob);
        }

        boolean wasUnsaved = savedJob.getState() == SavedJobState.UNSAVED;
        SavedJobSnapshot snapshot = snapshotRepository
                .findBySavedJobIdAndContentSha256(
                        savedJob.getId(), contentSha256)
                .orElse(null);
        boolean newSnapshot = snapshot == null;
        if (newSnapshot) {
            long version = snapshotRepository
                    .findFirstBySavedJobIdOrderBySnapshotVersionDesc(
                            savedJob.getId())
                    .map(existing -> existing.getSnapshotVersion() + 1)
                    .orElse(1L);
            snapshot = SavedJobSnapshot.create(
                    snapshotId,
                    savedJob.getId(),
                    version,
                    canonicalSchemaVersion,
                    contentSha256,
                    snapshotJson,
                    capturedAt,
                    sourceRetrievedAt);
            snapshotRepository.saveAndFlush(snapshot);
        }

        boolean sameCurrentSnapshot =
                snapshot.getId().equals(savedJob.getCurrentSnapshotId());
        SavedJobSaveOutcome outcome;
        if (created) {
            outcome = SavedJobSaveOutcome.CREATED;
        } else if (wasUnsaved) {
            outcome = SavedJobSaveOutcome.REACTIVATED;
            savedJob.reactivate(capturedAt);
        } else if (sameCurrentSnapshot) {
            outcome = SavedJobSaveOutcome.REPLAYED;
        } else {
            outcome = SavedJobSaveOutcome.UPDATED;
        }

        if (!sameCurrentSnapshot) {
            savedJob.selectSnapshot(
                    snapshot.getId(),
                    snapshot.getSnapshotVersion(),
                    capturedAt);
        }
        savedJobRepository.save(savedJob);
        return new PersistenceResult(outcome, savedJob, snapshot);
    }

    @Transactional(readOnly = true)
    public PersistenceResult findActive(String userId, UUID savedJobId) {
        SavedJob savedJob = savedJobRepository
                .findByIdAndUserIdAndState(
                        savedJobId,
                        userId,
                        SavedJobState.ACTIVE)
                .orElseThrow(SavedJobNotFoundException::new);
        return current(savedJob);
    }

    @Transactional(readOnly = true)
    public Page<PersistenceResult> findActive(
            String userId,
            int page,
            int size) {
        Page<SavedJob> savedJobs = savedJobRepository.findByUserIdAndState(
                userId,
                SavedJobState.ACTIVE,
                PageRequest.of(
                        page,
                        size,
                        Sort.by(Sort.Direction.DESC, "updatedAt")
                                .and(Sort.by("id"))));
        return savedJobs.map(this::current);
    }

    @Transactional
    public void unsave(String userId, UUID savedJobId, Instant now) {
        savedJobRepository.findForUpdateByIdAndUserId(savedJobId, userId)
                .ifPresent(savedJob -> {
                    savedJob.unsave(now);
                    savedJobRepository.save(savedJob);
                });
    }

    private PersistenceResult current(SavedJob savedJob) {
        if (savedJob.getCurrentSnapshotId() == null) {
            throw new IllegalStateException(
                    "Saved job has no current immutable snapshot.");
        }
        SavedJobSnapshot snapshot = snapshotRepository
                .findByIdAndSavedJobId(
                        savedJob.getCurrentSnapshotId(),
                        savedJob.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Saved job current snapshot is missing."));
        return new PersistenceResult(
                SavedJobSaveOutcome.REPLAYED,
                savedJob,
                snapshot);
    }

    public record PersistenceResult(
            SavedJobSaveOutcome outcome,
            SavedJob savedJob,
            SavedJobSnapshot snapshot) {
    }
}
