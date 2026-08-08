package com.jobseekercopilot.jobservice.service;

import com.jobseekercopilot.jobservice.model.dto.Job;

import java.util.List;
import java.util.Optional;

public interface JobProviderAdapter {
    String provider();

    boolean isEnabled();

    List<Job> search(String userId, JobSearchCriteria criteria);

    default Optional<Job> details(String userId, String externalJobId) {
        return Optional.empty();
    }
}
