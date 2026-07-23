package com.jobseekercopilot.jobservice.model.dto;

import java.util.List;

public class ReedJobSearchResponse {
    private List<Job> jobs;
    private List<TargetRoleJobResults> resultsByTargetRole;
    private Integer totalResults;
    private Integer page;
    private Integer pageSize;
    private List<ProviderResultStatus> providerResults;

    public ReedJobSearchResponse() {
    }

    public ReedJobSearchResponse(List<Job> jobs, Integer totalResults, Integer page, Integer pageSize) {
        this.jobs = jobs;
        this.totalResults = totalResults;
        this.page = page;
        this.pageSize = pageSize;
    }

    public ReedJobSearchResponse(List<Job> jobs, List<TargetRoleJobResults> resultsByTargetRole,
                                 Integer totalResults, Integer page, Integer pageSize) {
        this.jobs = jobs;
        this.resultsByTargetRole = resultsByTargetRole;
        this.totalResults = totalResults;
        this.page = page;
        this.pageSize = pageSize;
    }

    public ReedJobSearchResponse(List<Job> jobs, List<TargetRoleJobResults> resultsByTargetRole,
                                 Integer totalResults, Integer page, Integer pageSize,
                                 List<ProviderResultStatus> providerResults) {
        this(jobs, resultsByTargetRole, totalResults, page, pageSize);
        this.providerResults = providerResults;
    }

    public List<Job> getJobs() {
        return jobs;
    }

    public void setJobs(List<Job> jobs) {
        this.jobs = jobs;
    }

    public List<TargetRoleJobResults> getResultsByTargetRole() {
        return resultsByTargetRole;
    }

    public void setResultsByTargetRole(List<TargetRoleJobResults> resultsByTargetRole) {
        this.resultsByTargetRole = resultsByTargetRole;
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

    public List<ProviderResultStatus> getProviderResults() {
        return providerResults;
    }

    public void setProviderResults(List<ProviderResultStatus> providerResults) {
        this.providerResults = providerResults;
    }

    public static class TargetRoleJobResults {
        private String targetRole;
        private List<Job> jobs;

        public TargetRoleJobResults() {
        }

        public TargetRoleJobResults(String targetRole, List<Job> jobs) {
            this.targetRole = targetRole;
            this.jobs = jobs;
        }

        public String getTargetRole() {
            return targetRole;
        }

        public void setTargetRole(String targetRole) {
            this.targetRole = targetRole;
        }

        public List<Job> getJobs() {
            return jobs;
        }

        public void setJobs(List<Job> jobs) {
            this.jobs = jobs;
        }
    }
}
