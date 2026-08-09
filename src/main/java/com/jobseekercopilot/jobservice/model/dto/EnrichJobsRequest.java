package com.jobseekercopilot.jobservice.model.dto;

import java.util.List;

public class EnrichJobsRequest {
    private String userId;
    private List<Job> jobs;
    private HomeLocation homeLocation;
    private WorkPreferences commutePreferences;

    public EnrichJobsRequest() {
    }

    public EnrichJobsRequest(String userId, List<Job> jobs) {
        this(userId, jobs, null, null);
    }

    public EnrichJobsRequest(
            String userId,
            List<Job> jobs,
            HomeLocation homeLocation,
            WorkPreferences commutePreferences) {
        this.userId = userId;
        this.jobs = jobs;
        this.homeLocation = homeLocation;
        this.commutePreferences = commutePreferences;
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

    public HomeLocation getHomeLocation() { return homeLocation; }
    public void setHomeLocation(HomeLocation homeLocation) { this.homeLocation = homeLocation; }
    public WorkPreferences getCommutePreferences() { return commutePreferences; }
    public void setCommutePreferences(WorkPreferences commutePreferences) { this.commutePreferences = commutePreferences; }
}
