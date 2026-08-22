package com.jobseekercopilot.jobservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Counts explaining deterministic vacancy exclusions before paging.")
public class SearchQualitySummary {
    private int assessedJobCount;
    private int eligibleJobCount;
    private int excludedExpiredCount;
    private int excludedPaidTrainingCount;
    private int excludedOccupationMismatchCount;

    public int getAssessedJobCount() { return assessedJobCount; }
    public void setAssessedJobCount(int assessedJobCount) { this.assessedJobCount = assessedJobCount; }
    public int getEligibleJobCount() { return eligibleJobCount; }
    public void setEligibleJobCount(int eligibleJobCount) { this.eligibleJobCount = eligibleJobCount; }
    public int getExcludedExpiredCount() { return excludedExpiredCount; }
    public void setExcludedExpiredCount(int excludedExpiredCount) { this.excludedExpiredCount = excludedExpiredCount; }
    public int getExcludedPaidTrainingCount() { return excludedPaidTrainingCount; }
    public void setExcludedPaidTrainingCount(int excludedPaidTrainingCount) { this.excludedPaidTrainingCount = excludedPaidTrainingCount; }
    public int getExcludedOccupationMismatchCount() { return excludedOccupationMismatchCount; }
    public void setExcludedOccupationMismatchCount(int excludedOccupationMismatchCount) { this.excludedOccupationMismatchCount = excludedOccupationMismatchCount; }
}
