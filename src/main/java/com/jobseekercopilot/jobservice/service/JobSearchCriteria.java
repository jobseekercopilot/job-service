package com.jobseekercopilot.jobservice.service;

import com.jobseekercopilot.jobservice.model.dto.JobSearchRequest;

import java.util.List;

public class JobSearchCriteria {
    private final JobSearchRequest request;
    private final String targetRole;
    private final String location;
    private final Integer distanceMiles;
    private final List<String> employmentTypes;
    private final Integer salaryMin;
    private final Integer salaryMax;
    private final String currency;
    private final boolean remoteOnly;

    public JobSearchCriteria(JobSearchRequest request, String targetRole, String location, Integer distanceMiles,
                             List<String> employmentTypes, Integer salaryMin, Integer salaryMax, String currency,
                             boolean remoteOnly) {
        this.request = request;
        this.targetRole = targetRole;
        this.location = location;
        this.distanceMiles = distanceMiles;
        this.employmentTypes = employmentTypes;
        this.salaryMin = salaryMin;
        this.salaryMax = salaryMax;
        this.currency = currency;
        this.remoteOnly = remoteOnly;
    }

    public JobSearchRequest getRequest() {
        return request;
    }

    public String getTargetRole() {
        return targetRole;
    }

    public String getLocation() {
        return location;
    }

    public Integer getDistanceMiles() {
        return distanceMiles;
    }

    public List<String> getEmploymentTypes() {
        return employmentTypes;
    }

    public Integer getSalaryMin() {
        return salaryMin;
    }

    public Integer getSalaryMax() {
        return salaryMax;
    }

    public String getCurrency() {
        return currency;
    }

    public boolean isRemoteOnly() {
        return remoteOnly;
    }
}
