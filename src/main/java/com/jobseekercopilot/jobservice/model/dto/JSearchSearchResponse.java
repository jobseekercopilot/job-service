package com.jobseekercopilot.jobservice.model.dto;

import java.util.List;

public class JSearchSearchResponse {
    private String provider;
    private String cursor;
    private List<JSearchJob> jobs;

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getCursor() { return cursor; }
    public void setCursor(String cursor) { this.cursor = cursor; }
    public List<JSearchJob> getJobs() { return jobs; }
    public void setJobs(List<JSearchJob> jobs) { this.jobs = jobs; }
}
