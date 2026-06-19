package com.jobseekercopilot.jobservice.model.dto;

import java.util.List;

public class ReedJobSearchResponse {
    private List<Job> jobs;
    private Integer totalResults;
    private Integer page;
    private Integer pageSize;

    public ReedJobSearchResponse() {
    }

    public ReedJobSearchResponse(List<Job> jobs, Integer totalResults, Integer page, Integer pageSize) {
        this.jobs = jobs;
        this.totalResults = totalResults;
        this.page = page;
        this.pageSize = pageSize;
    }

    public List<Job> getJobs() {
        return jobs;
    }

    public void setJobs(List<Job> jobs) {
        this.jobs = jobs;
    }

    public Integer getTotalResults() {
        return totalResults;
    }

    public void setTotalResults(Integer totalResults) {
        this.totalResults = totalResults;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }
}