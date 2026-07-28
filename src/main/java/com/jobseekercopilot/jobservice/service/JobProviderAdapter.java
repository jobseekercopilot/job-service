package com.jobseekercopilot.jobservice.service;

import com.jobseekercopilot.jobservice.model.dto.Job;

import java.util.List;

public interface JobProviderAdapter {
    String provider();

    boolean isEnabled();

    List<Job> search(String userId, JobSearchCriteria criteria);

    default ProviderSearchOutcome searchWithStatus(
            String userId,
            JobSearchCriteria criteria) {
        return ProviderSearchOutcome.available(
                search(userId, criteria));
    }

    record ProviderSearchOutcome(
            List<Job> jobs,
            String status) {

        static ProviderSearchOutcome available(List<Job> jobs) {
            return new ProviderSearchOutcome(
                    jobs == null ? List.of() : List.copyOf(jobs),
                    "SUCCESS");
        }

        static ProviderSearchOutcome disabled() {
            return new ProviderSearchOutcome(List.of(), "DISABLED");
        }
    }
}
