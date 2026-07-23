package com.jobseekercopilot.jobservice.service;

import com.jobseekercopilot.jobservice.model.dto.Job;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class JobResultEnrichmentService {
    private final SalaryNormalisationService salaryNormalisationService;

    public JobResultEnrichmentService(SalaryNormalisationService salaryNormalisationService) {
        this.salaryNormalisationService = salaryNormalisationService;
    }

    public List<Job> enrich(JobSearchCriteria criteria, List<Job> jobs) {
        for (Job job : jobs) {
            if (job.getPostedAt() == null || job.getPostedAt().isBlank()) {
                job.setPostedAt(job.getPostedDate());
            }
            salaryNormalisationService.normalise(job.getSalary());
        }
        return jobs;
    }
}
