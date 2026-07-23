package com.jobseekercopilot.jobservice.model.dto;

import java.util.List;

public class EnrichJobsResponse {
    private String userId;
    private List<Job> jobs;

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
