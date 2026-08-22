package com.jobseekercopilot.jobservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Claimant-declared qualification fact; institution and grade are not needed for ranking and are omitted.")
public class CandidateQualification {
    private String qualificationName;
    private String status;
    private String dateAchieved;
    private String expectedCompletion;

    public String getQualificationName() { return qualificationName; }
    public void setQualificationName(String qualificationName) { this.qualificationName = qualificationName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDateAchieved() { return dateAchieved; }
    public void setDateAchieved(String dateAchieved) { this.dateAchieved = dateAchieved; }
    public String getExpectedCompletion() { return expectedCompletion; }
    public void setExpectedCompletion(String expectedCompletion) { this.expectedCompletion = expectedCompletion; }
}
