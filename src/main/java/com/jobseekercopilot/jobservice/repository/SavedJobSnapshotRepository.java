package com.jobseekercopilot.jobservice.repository;

import com.jobseekercopilot.jobservice.entity.SavedJobSnapshot;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedJobSnapshotRepository
        extends JpaRepository<SavedJobSnapshot, UUID> {

    Optional<SavedJobSnapshot> findBySavedJobIdAndContentSha256(
            UUID savedJobId,
            String contentSha256);

    Optional<SavedJobSnapshot> findByIdAndSavedJobId(
            UUID id,
            UUID savedJobId);

    Optional<SavedJobSnapshot> findFirstBySavedJobIdOrderBySnapshotVersionDesc(
            UUID savedJobId);

    long countBySavedJobId(UUID savedJobId);
}
