package com.jobseekercopilot.jobservice.service;

import com.jobseekercopilot.jobservice.model.dto.Job;

import java.util.List;

public interface JobProviderAdapter {
    String provider();

    boolean isEnabled();

    List<Job> search(String userId, JobSearchCriteria criteria);
}
