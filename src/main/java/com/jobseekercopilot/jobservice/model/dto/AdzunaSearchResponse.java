package com.jobseekercopilot.jobservice.model.dto;

import java.util.List;

public class AdzunaSearchResponse {
    private String provider;
    private Integer totalAvailable;
    private Integer page;
    private Integer resultsPerPage;
    private List<AdzunaJob> jobs;

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public Integer getTotalAvailable() { return totalAvailable; }
    public void setTotalAvailable(Integer totalAvailable) { this.totalAvailable = totalAvailable; }
    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }
    public Integer getResultsPerPage() { return resultsPerPage; }
    public void setResultsPerPage(Integer resultsPerPage) { this.resultsPerPage = resultsPerPage; }
    public List<AdzunaJob> getJobs() { return jobs; }
    public void setJobs(List<AdzunaJob> jobs) { this.jobs = jobs; }
}
