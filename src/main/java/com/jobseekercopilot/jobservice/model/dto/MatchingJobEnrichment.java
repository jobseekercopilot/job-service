package com.jobseekercopilot.jobservice.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Consumer-owned projection of the fields Job Matching is allowed to enrich.
 *
 * <p>The matching service currently echoes a reduced representation of each
 * job. Keeping that representation out of the canonical {@link Job} model
 * prevents an internal response from replacing provider-owned job data.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MatchingJobEnrichment {
    private String canonicalJobId;
    private Double distanceMiles;
    private Double matchScore;
    private CommuteAssessment commuteAssessment;
    private String applicationStatus;
    private UUID applicationId;
    private String cvDocumentId;
    private String coverLetterDocumentId;
    @JsonDeserialize(using = UtcApplicationTimestampDeserializer.class)
    private OffsetDateTime appliedAt;
    @JsonDeserialize(using = UtcApplicationTimestampDeserializer.class)
    private OffsetDateTime applicationUpdatedAt;

    public String getCanonicalJobId() {
        return canonicalJobId;
    }

    public void setCanonicalJobId(String canonicalJobId) {
        this.canonicalJobId = canonicalJobId;
    }

    public Double getDistanceMiles() {
        return distanceMiles;
    }

    public void setDistanceMiles(Double distanceMiles) {
        this.distanceMiles = distanceMiles;
    }

    public Double getMatchScore() {
        return matchScore;
    }

    public void setMatchScore(Double matchScore) {
        this.matchScore = matchScore;
    }

    public CommuteAssessment getCommuteAssessment() {
        return commuteAssessment;
    }

    public void setCommuteAssessment(CommuteAssessment commuteAssessment) {
        this.commuteAssessment = commuteAssessment;
    }

    public String getApplicationStatus() {
        return applicationStatus;
    }

    public void setApplicationStatus(String applicationStatus) {
        this.applicationStatus = applicationStatus;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(UUID applicationId) {
        this.applicationId = applicationId;
    }

    public String getCvDocumentId() {
        return cvDocumentId;
    }

    public void setCvDocumentId(String cvDocumentId) {
        this.cvDocumentId = cvDocumentId;
    }

    public String getCoverLetterDocumentId() {
        return coverLetterDocumentId;
    }

    public void setCoverLetterDocumentId(String coverLetterDocumentId) {
        this.coverLetterDocumentId = coverLetterDocumentId;
    }

    public OffsetDateTime getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(OffsetDateTime appliedAt) {
        this.appliedAt = appliedAt;
    }

    public OffsetDateTime getApplicationUpdatedAt() {
        return applicationUpdatedAt;
    }

    public void setApplicationUpdatedAt(
            OffsetDateTime applicationUpdatedAt) {
        this.applicationUpdatedAt = applicationUpdatedAt;
    }
}
