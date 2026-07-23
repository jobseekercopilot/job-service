package com.jobseekercopilot.jobservice.model.dto;

import java.util.List;

public class EnrichJobsRequest {
    private String userId;
    private List<Job> jobs;

    public EnrichJobsRequest() {
    }

    public EnrichJobsRequest(String userId, List<Job> jobs) {
        this.userId = userId;
        this.jobs = jobs;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public List<Job> getJobs() {
        return jobs;
    }

    public void setJobs(List<Job> jobs) {
        this.jobs = jobs;
    }
}
