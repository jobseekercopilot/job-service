package com.jobseekercopilot.jobservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public class ReedJobSearchResponse {
    private List<Job> jobs;
    private List<TargetRoleJobResults> resultsByTargetRole;
    @Schema(description = "Number of role/job rows in the bounded aggregate compatibility window before paging.")
    private Integer totalResults;
    @Schema(description = "One-based aggregate compatibility page returned.", minimum = "1", maximum = "100")
    private Integer page;
    @Schema(description = "Maximum jobs returned in the aggregate compatibility page.", minimum = "1", maximum = "50")
    private Integer pageSize;
    @Schema(description = "Pages available in the bounded aggregate compatibility window.")
    private Integer totalPages;
    @Schema(
            allowableValues = {
                    "MOST_RELEVANT",
                    "CLOSEST",
                    "HIGHEST_SALARY",
                    "NEWEST_POSTED",
                    "OLDEST_POSTED",
                    "COMPANY_AZ",
                    "JOB_TITLE_AZ"
            }
    )
    private String sort;
    private List<ProviderResultStatus> providerResults;
    @Schema(allowableValues = {"COMPLETE", "PARTIAL"})
    private String searchStatus;
    @Schema(allowableValues = {
            "COMPLETE",
            "NOT_RUN",
            "UNAVAILABLE",
            "TIMED_OUT",
            "SATURATED"
    })
    private String matchingStatus;

    public ReedJobSearchResponse() {
    }

    public ReedJobSearchResponse(List<Job> jobs, Integer totalResults, Integer page, Integer pageSize) {
        this.jobs = jobs;
        this.totalResults = totalResults;
        this.page = page;
        this.pageSize = pageSize;
        this.totalPages = totalResults == null || pageSize == null || pageSize <= 0
                ? null
                : (int) Math.ceil((double) totalResults / pageSize);
    }

    public ReedJobSearchResponse(List<Job> jobs, List<TargetRoleJobResults> resultsByTargetRole,
                                 Integer totalResults, Integer page, Integer pageSize) {
        this(jobs, totalResults, page, pageSize);
        this.resultsByTargetRole = resultsByTargetRole;
    }

    public ReedJobSearchResponse(List<Job> jobs, List<TargetRoleJobResults> resultsByTargetRole,
                                 Integer totalResults, Integer page, Integer pageSize,
                                 List<ProviderResultStatus> providerResults,
                                 String searchStatus,
                                 String matchingStatus) {
        this(jobs, resultsByTargetRole, totalResults, page, pageSize);
        this.providerResults = providerResults;
        this.searchStatus = searchStatus;
        this.matchingStatus = matchingStatus;
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

    public Integer getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(Integer totalPages) {
        this.totalPages = totalPages;
    }

    public String getSort() {
        return sort;
    }

    public void setSort(String sort) {
        this.sort = sort;
    }

    public List<ProviderResultStatus> getProviderResults() {
        return providerResults;
    }

    public void setProviderResults(List<ProviderResultStatus> providerResults) {
        this.providerResults = providerResults;
    }

    public String getSearchStatus() {
        return searchStatus;
    }

    public void setSearchStatus(String searchStatus) {
        this.searchStatus = searchStatus;
    }

    public String getMatchingStatus() {
        return matchingStatus;
    }

    public void setMatchingStatus(String matchingStatus) {
        this.matchingStatus = matchingStatus;
    }

    public static class TargetRoleJobResults {
        private String targetRole;
        private List<Job> jobs;
        @Schema(description = "Number of jobs in this target role's bounded result window before paging.")
        private Integer totalResults;
        @Schema(description = "One-based page returned for this target role.", minimum = "1", maximum = "100")
        private Integer page;
        @Schema(description = "Maximum jobs returned for this target role on this page.", minimum = "1", maximum = "50")
        private Integer pageSize;
        @Schema(description = "Pages available in this target role's bounded result window.")
        private Integer totalPages;
        private List<ProviderResultStatus> providerResults;
        @Schema(allowableValues = {"COMPLETE", "PARTIAL", "UNAVAILABLE"})
        private String searchStatus;
        @Schema(allowableValues = {
                "COMPLETE",
                "NOT_RUN",
                "UNAVAILABLE",
                "TIMED_OUT",
                "SATURATED"
        })
        private String matchingStatus;

        public TargetRoleJobResults() {
        }

        public TargetRoleJobResults(
                String targetRole,
                List<Job> jobs,
                Integer totalResults,
                Integer page,
                Integer pageSize,
                List<ProviderResultStatus> providerResults,
                String searchStatus,
                String matchingStatus) {
            this.targetRole = targetRole;
            this.jobs = jobs;
            this.totalResults = totalResults;
            this.page = page;
            this.pageSize = pageSize;
            this.totalPages =
                    totalResults == null || pageSize == null || pageSize <= 0
                            ? null
                            : (int) Math.ceil((double) totalResults / pageSize);
            this.providerResults = providerResults;
            this.searchStatus = searchStatus;
            this.matchingStatus = matchingStatus;
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

        public Integer getTotalPages() {
            return totalPages;
        }

        public void setTotalPages(Integer totalPages) {
            this.totalPages = totalPages;
        }

        public List<ProviderResultStatus> getProviderResults() {
            return providerResults;
        }

        public void setProviderResults(
                List<ProviderResultStatus> providerResults) {
            this.providerResults = providerResults;
        }

        public String getSearchStatus() {
            return searchStatus;
        }

        public void setSearchStatus(String searchStatus) {
            this.searchStatus = searchStatus;
        }

        public String getMatchingStatus() {
            return matchingStatus;
        }

        public void setMatchingStatus(String matchingStatus) {
            this.matchingStatus = matchingStatus;
        }
    }
}
