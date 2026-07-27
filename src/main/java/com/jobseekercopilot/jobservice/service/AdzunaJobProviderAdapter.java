package com.jobseekercopilot.jobservice.service;

import com.jobseekercopilot.generated.adzunagateway.api.AdzunaJobsApi;
import com.jobseekercopilot.generated.adzunagateway.model.AdzunaJob;
import com.jobseekercopilot.generated.adzunagateway.model.AdzunaSearchRequest;
import com.jobseekercopilot.generated.adzunagateway.model.AdzunaSearchResponse;
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
public class AdzunaJobProviderAdapter implements JobProviderAdapter {
    private final AdzunaJobsApi adzunaJobsApi;
    private final PublisherNormalisationService publisherNormalisationService;
    private final boolean enabled;
    private final int resultsPerPage;

    public AdzunaJobProviderAdapter(AdzunaJobsApi adzunaJobsApi,
                                    PublisherNormalisationService publisherNormalisationService,
                                    @Value("${providers.adzuna.enabled:${ADZUNA_ENABLED:true}}") boolean enabled,
                                    @Value("${providers.adzuna.results-per-page:${ADZUNA_RESULTS_PER_PAGE:50}}") int resultsPerPage) {
        this.adzunaJobsApi = adzunaJobsApi;
        this.publisherNormalisationService = publisherNormalisationService;
        this.enabled = enabled;
        this.resultsPerPage = resultsPerPage;
    }

    @Override
    public String provider() {
        return "ADZUNA";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public List<Job> search(String userId, JobSearchCriteria criteria) {
        AdzunaSearchRequest request = new AdzunaSearchRequest();
        request.setTargetRole(criteria.getTargetRole());
        request.setLocation(criteria.getLocation());
        request.setDistanceMiles(criteria.getDistanceMiles());
        request.setPage(1);
        request.setResultsPerPage(resultsPerPage);
        AdzunaSearchResponse response = adzunaJobsApi.search(request);
        return response == null || response.getJobs() == null
                ? List.of()
                : response.getJobs().stream().map(this::toJob).toList();
    }

    private Job toJob(AdzunaJob source) {
        Job job = new Job();
        job.setId(source.getExternalJobId());
        job.setCanonicalJobId(source.getExternalJobId());
        job.setProvider(provider());
        job.setPrimarySource(provider());
        job.setExternalJobId(source.getExternalJobId());
        job.setTitle(source.getTitle());
        job.setJobTitle(source.getTitle());
        job.setCompany(source.getCompanyName());
        job.setCompanyName(source.getCompanyName());
        job.setLocation(source.getLocationDisplayName());
        CanonicalLocation location = new CanonicalLocation();
        location.setRawDisplayName(source.getLocationDisplayName());
        location.setDisplayName(source.getLocationDisplayName());
        location.setAreaParts(source.getLocationAreas());
        location.setLatitude(source.getLatitude());
        location.setLongitude(source.getLongitude());
        location.setSourceProvider(provider());
        location.setNormalisationStatus(
                source.getLocationDisplayName() == null
                        ? CanonicalValueStatus.NOT_PROVIDED
                        : CanonicalValueStatus.RAW_ONLY);
        job.setCanonicalLocation(location);
        if (source.getSalaryMinimum() != null || source.getSalaryMaximum() != null) {
            JobSalary salary = new JobSalary(
                    source.getSalaryMinimum(),
                    source.getSalaryMaximum(),
                    "GBP",
                    "YEAR");
            salary.setPeriodCode(SalaryPeriodCode.YEAR);
            salary.setPredicted(source.getSalaryPredicted());
            salary.setSourceProvider(provider());
            job.setSalary(salary);
        }
        job.setEmploymentType(source.getEmploymentType());
        job.setContractType(source.getContractType());
        job.setCategory(source.getCategory());
        job.setPostedDate(source.getPostedAt());
        job.setPostedAt(source.getPostedAt());
        job.setPostedAtUtc(
                CanonicalJobMappingSupport.parseOffsetDateTime(
                        source.getPostedAt()));
        job.setDescription(source.getDescription());
        String safeSourceUrl =
                CanonicalUrlPolicy.safeHttpUrl(source.getRedirectUrl());
        job.setUrl(safeSourceUrl);
        job.setSourceUrl(safeSourceUrl);

        JobSourceReference reference = new JobSourceReference();
        reference.setProvider(provider());
        reference.setExternalJobId(source.getExternalJobId());
        reference.setRawPublisher("Adzuna");
        reference.setPublisher(publisherNormalisationService.normalise("Adzuna", source.getRedirectUrl(), false, "Adzuna"));
        reference.setSourceType(JobSourceType.AGGREGATOR);
        reference.setListingUrl(safeSourceUrl);
        reference.setApplyUrl(safeSourceUrl);
        reference.setDirectApply(false);
        reference.setProviderPostedAt(job.getPostedAtUtc());
        reference.setProviderPostedAtRaw(source.getPostedAt());
        reference.setProviderPostedAtUtc(job.getPostedAtUtc());
        job.setSources(List.of(reference));
        job.setFieldProvenance(List.of(
                CanonicalJobMappingSupport.rawField(
                        provider(), source.getExternalJobId(), "title",
                        source.getTitle()),
                CanonicalJobMappingSupport.rawField(
                        provider(), source.getExternalJobId(), "employmentType",
                        source.getEmploymentType()),
                CanonicalJobMappingSupport.rawField(
                        provider(), source.getExternalJobId(), "contractType",
                        source.getContractType()),
                CanonicalJobMappingSupport.timestampField(
                        provider(), source.getExternalJobId(), "postedAt",
                        source.getPostedAt(), job.getPostedAtUtc())));
        return job;
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.replace("Z", ""));
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
