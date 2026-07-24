package com.jobseekercopilot.jobservice.service;

import com.jobseekercopilot.generated.reedgateway.api.ReedJobsApi;
import com.jobseekercopilot.generated.reedgateway.model.ExternalJob;
import com.jobseekercopilot.generated.reedgateway.model.ExternalSearchRequest;
import com.jobseekercopilot.generated.reedgateway.model.ExternalSearchResponse;
import com.jobseekercopilot.jobservice.model.dto.CanonicalLocation;
import com.jobseekercopilot.jobservice.model.dto.CanonicalValueStatus;
import com.jobseekercopilot.jobservice.model.dto.Job;
import com.jobseekercopilot.jobservice.model.dto.JobSalary;
import com.jobseekercopilot.jobservice.model.dto.JobSourceReference;
import com.jobseekercopilot.jobservice.model.dto.JobSourceType;
import com.jobseekercopilot.jobservice.model.dto.SalaryPeriodCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class ReedJobProviderAdapter implements JobProviderAdapter {
    private final ReedJobsApi reedJobsApi;
    private final PublisherNormalisationService publisherNormalisationService;
    private final boolean enabled;
    private final int defaultPage;
    private final int pageSize;

    public ReedJobProviderAdapter(ReedJobsApi reedJobsApi,
                                  PublisherNormalisationService publisherNormalisationService,
                                  @Value("${providers.reed.enabled:true}") boolean enabled,
                                  @Value("${job-search.default-page:1}") int defaultPage,
                                  @Value("${job-search.default-page-size:100}") int pageSize) {
        this.reedJobsApi = reedJobsApi;
        this.publisherNormalisationService = publisherNormalisationService;
        this.enabled = enabled;
        this.defaultPage = defaultPage;
        this.pageSize = pageSize;
    }

    @Override
    public String provider() {
        return "REED";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public List<Job> search(String userId, JobSearchCriteria criteria) {
        ExternalSearchRequest request = new ExternalSearchRequest()
                .keywords(List.of(criteria.getTargetRole()))
                .location(criteria.getLocation())
                .distance(criteria.getDistanceMiles())
                .employmentType(criteria.getEmploymentTypes())
                .salaryMin(criteria.getSalaryMin())
                .salaryMax(criteria.getSalaryMax())
                .currency(criteria.getCurrency())
                .page(defaultPage)
                .pageSize(pageSize);
        ExternalSearchResponse response = reedJobsApi.externalSearch(request, userId);
        if (response == null || response.getJobs() == null) {
            return List.of();
        }
        return response.getJobs().stream().map(this::fromDownstreamJob).toList();
    }

    private Job fromDownstreamJob(ExternalJob source) {
        Job target = new Job();
        target.setId(source.getId());
        target.setCanonicalJobId(source.getId());
        target.setProvider(provider());
        target.setPrimarySource(provider());
        target.setExternalJobId(source.getId());
        target.setTitle(source.getTitle());
        target.setJobTitle(source.getTitle());
        target.setCompany(source.getCompany());
        target.setCompanyName(source.getCompany());
        target.setLocation(source.getLocation());
        CanonicalLocation location = new CanonicalLocation();
        location.setRawDisplayName(source.getLocation());
        location.setDisplayName(source.getLocation());
        location.setSourceProvider(provider());
        location.setNormalisationStatus(source.getLocation() == null
                ? CanonicalValueStatus.NOT_PROVIDED
                : CanonicalValueStatus.RAW_ONLY);
        target.setCanonicalLocation(location);
        if (source.getSalary() != null) {
            JobSalary salary = new JobSalary(
                    source.getSalary().getMin(),
                    source.getSalary().getMax(),
                    source.getSalary().getCurrency(),
                    "YEAR");
            salary.setPeriodCode(SalaryPeriodCode.YEAR);
            salary.setSourceProvider(provider());
            target.setSalary(salary);
        }
        target.setEmploymentType(source.getEmploymentType());
        target.setPostedDate(source.getPostedDate());
        target.setPostedAt(source.getPostedDate());
        target.setPostedAtUtc(
                CanonicalJobMappingSupport.parseOffsetDateTime(
                        source.getPostedDate()));
        target.setDescription(source.getDescription());
        String safeSourceUrl =
                CanonicalUrlPolicy.safeHttpUrl(source.getUrl());
        target.setUrl(safeSourceUrl);
        target.setSourceUrl(safeSourceUrl);
        target.setMatchScore(source.getMatchScore());

        JobSourceReference sourceReference = new JobSourceReference();
        sourceReference.setProvider(provider());
        sourceReference.setExternalJobId(source.getId());
        sourceReference.setRawPublisher("Reed.co.uk");
        sourceReference.setPublisher(publisherNormalisationService.normalise("Reed.co.uk", source.getUrl(), false, "Reed.co.uk"));
        sourceReference.setSourceType(JobSourceType.JOB_BOARD);
        sourceReference.setListingUrl(safeSourceUrl);
        sourceReference.setApplyUrl(safeSourceUrl);
        sourceReference.setDirectApply(false);
        sourceReference.setProviderPostedAt(parseDateTime(source.getPostedDate()));
        sourceReference.setProviderPostedAtRaw(source.getPostedDate());
        sourceReference.setProviderPostedAtUtc(target.getPostedAtUtc());
        target.setSources(List.of(sourceReference));
        target.setFieldProvenance(List.of(
                CanonicalJobMappingSupport.rawField(
                        provider(), source.getId(), "title", source.getTitle()),
                CanonicalJobMappingSupport.rawField(
                        provider(), source.getId(), "employmentType",
                        source.getEmploymentType()),
                CanonicalJobMappingSupport.timestampField(
                        provider(), source.getId(), "postedAt",
                        source.getPostedDate(), target.getPostedAtUtc())));
        return target;
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.length() == 10 ? value + "T00:00:00" : value);
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
