package com.jobseekercopilot.jobservice.repository;

import com.jobseekercopilot.jobservice.entity.SavedJob;
import com.jobseekercopilot.jobservice.entity.SavedJobState;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SavedJobRepository extends JpaRepository<SavedJob, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select savedJob
            from SavedJob savedJob
            where savedJob.userId = :userId
              and savedJob.canonicalJobId = :canonicalJobId
            """)
    Optional<SavedJob> findForUpdate(
            @Param("userId") String userId,
            @Param("canonicalJobId") String canonicalJobId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select savedJob
            from SavedJob savedJob
            where savedJob.id = :id
              and savedJob.userId = :userId
            """)
    Optional<SavedJob> findForUpdateByIdAndUserId(
            @Param("id") UUID id,
            @Param("userId") String userId);

    Optional<SavedJob> findByIdAndUserIdAndState(
            UUID id,
            String userId,
            SavedJobState state);

    Page<SavedJob> findByUserIdAndState(
            String userId,
            SavedJobState state,
            Pageable pageable);

    long countByUserIdAndCanonicalJobId(
            String userId,
            String canonicalJobId);
}
