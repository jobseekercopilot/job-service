package com.jobseekercopilot.jobservice.model.dto;

import java.util.List;

public class EnrichJobsResponse {
    private String userId;
    private List<MatchingJobEnrichment> jobs;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public List<MatchingJobEnrichment> getJobs() {
        return jobs;
    }

    public void setJobs(List<MatchingJobEnrichment> jobs) {
        this.jobs = jobs;
    }
}
