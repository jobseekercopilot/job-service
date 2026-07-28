package com.jobseekercopilot.jobservice.service;

import com.jobseekercopilot.generated.nhsjobsgateway.api.DefaultApi;
import com.jobseekercopilot.generated.nhsjobsgateway.model.CanonicalJob;
import com.jobseekercopilot.generated.nhsjobsgateway.model.NhsJobsSearchRequest;
import com.jobseekercopilot.generated.nhsjobsgateway.model.NhsJobsSearchResponse;
import com.jobseekercopilot.generated.nhsjobsgateway.model.ProviderAttribution;
import com.jobseekercopilot.jobservice.model.dto.CanonicalLocation;
import com.jobseekercopilot.jobservice.model.dto.CanonicalValueStatus;
import com.jobseekercopilot.jobservice.model.dto.Job;
import com.jobseekercopilot.jobservice.model.dto.JobSourceReference;
import com.jobseekercopilot.jobservice.model.dto.JobSourceType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class NhsJobsProviderAdapter implements JobProviderAdapter {
    private static final String PROVIDER = "NHS_JOBS";
    private static final String PUBLISHER = "NHS Jobs";
    private static final String DEFAULT_COUNTRY_CODE = "GB-ENG";

    private final DefaultApi nhsJobsApi;
    private final boolean enabled;
    private final int resultsPerPage;

    public NhsJobsProviderAdapter(
            DefaultApi nhsJobsApi,
            @Value("${providers.nhs-jobs.enabled:"
                    + "${NHS_JOBS_ENABLED:true}}")
                    boolean enabled,
            @Value("${providers.nhs-jobs.results-per-page:"
                    + "${NHS_JOBS_RESULTS_PER_PAGE:50}}")
                    int resultsPerPage) {
        this.nhsJobsApi = nhsJobsApi;
        this.enabled = enabled;
        this.resultsPerPage = resultsPerPage;
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public List<Job> search(String userId, JobSearchCriteria criteria) {
        return searchWithStatus(userId, criteria).jobs();
    }

    @Override
    public ProviderSearchOutcome searchWithStatus(
            String userId,
            JobSearchCriteria criteria) {
        NhsJobsSearchRequest request = new NhsJobsSearchRequest()
                .keyword(criteria.getTargetRole())
                .location(criteria.getLocation())
                .distanceMiles(criteria.getDistanceMiles())
                .countryCode(criteria.getLocation() == null
                                || criteria.getLocation().isBlank()
                        ? null : DEFAULT_COUNTRY_CODE)
                .contractTypes(criteria.getEmploymentTypes())
                .salaryFrom(criteria.getSalaryMin())
                .salaryTo(criteria.getSalaryMax())
                .page(1)
                .resultsPerPage(resultsPerPage);
        NhsJobsSearchResponse response = nhsJobsApi.searchNhsJobs(request);
        if (response == null
                || response.getJobs() == null) {
            return ProviderSearchOutcome.available(List.of());
        }
        if (response.getStatus()
                == NhsJobsSearchResponse.StatusEnum.DISABLED) {
            return ProviderSearchOutcome.disabled();
        }
        ProviderAttribution attribution = response.getAttribution();
        return ProviderSearchOutcome.available(
                response.getJobs().stream()
                        .map(job -> toJob(job, attribution))
                        .toList());
    }

    private Job toJob(
            CanonicalJob source,
            ProviderAttribution attribution) {
        Job job = new Job();
        job.setId(source.getExternalJobId());
        job.setCanonicalJobId(source.getExternalJobId());
        job.setProvider(PROVIDER);
        job.setPrimarySource(PROVIDER);
        job.setExternalJobId(source.getExternalJobId());
        job.setTitle(source.getTitle());
        job.setJobTitle(source.getTitle());
        job.setCompany(source.getEmployer());
        job.setCompanyName(source.getEmployer());
        String locationText = source.getLocations() == null
                ? null
                : String.join(", ", source.getLocations());
        job.setLocation(locationText);
        CanonicalLocation location = new CanonicalLocation();
        location.setRawDisplayName(locationText);
        location.setDisplayName(locationText);
        location.setAreaParts(source.getLocations());
        location.setSourceProvider(PROVIDER);
        location.setNormalisationStatus(locationText == null
                || locationText.isBlank()
                        ? CanonicalValueStatus.NOT_PROVIDED
                        : CanonicalValueStatus.RAW_ONLY);
        job.setCanonicalLocation(location);
        job.setSalaryText(source.getSalaryText());
        job.setContractType(source.getContractType());
        job.setPostedDate(source.getPostedAt());
        job.setPostedAt(source.getPostedAt());
        job.setExpiresAt(source.getClosesAt());
        job.setPostedAtUtc(parseProviderDate(source.getPostedAt()));
        job.setExpiresAtUtc(parseProviderDate(source.getClosesAt()));
        job.setApplicationDeadlineAtUtc(
                parseProviderDate(source.getClosesAt()));
        job.setDescription(source.getDescription());
        String safeListingUrl =
                CanonicalUrlPolicy.safeHttpUrl(
                        source.getSourceUrl() == null
                                ? null
                                : source.getSourceUrl().toString());
        String safeApplicationUrl =
                CanonicalUrlPolicy.safeHttpUrl(
                        source.getApplicationUrl() == null
                                ? null
                                : source.getApplicationUrl().toString());
        job.setUrl(safeApplicationUrl == null
                ? safeListingUrl
                : safeApplicationUrl);
        job.setSourceUrl(safeListingUrl);

        JobSourceReference reference = new JobSourceReference();
        reference.setProvider(PROVIDER);
        reference.setExternalJobId(source.getExternalJobId());
        reference.setRawPublisher(source.getSource());
        reference.setPublisher(PUBLISHER);
        reference.setSourceType(JobSourceType.JOB_BOARD);
        reference.setListingUrl(safeListingUrl);
        reference.setApplyUrl(safeApplicationUrl);
        reference.setDirectApply(false);
        reference.setProviderPostedAt(job.getPostedAtUtc());
        reference.setProviderPostedAtRaw(source.getPostedAt());
        reference.setProviderPostedAtUtc(job.getPostedAtUtc());
        reference.setProviderExpiresAtRaw(source.getClosesAt());
        reference.setProviderExpiresAtUtc(job.getExpiresAtUtc());
        if (attribution != null) {
            reference.setAttributionLabel(attribution.getLabel());
            reference.setAttributionSourceUrl(
                    CanonicalUrlPolicy.safeHttpUrl(
                            attribution.getSourceUrl().toString()));
            reference.setLicenceUrl(
                    CanonicalUrlPolicy.safeHttpUrl(
                            attribution.getLicenceUrl().toString()));
            reference.setDisclaimer(attribution.getDisclaimer());
        }
        job.setSources(List.of(reference));
        job.setFieldProvenance(List.of(
                CanonicalJobMappingSupport.rawField(
                        PROVIDER, source.getExternalJobId(), "title",
                        source.getTitle()),
                CanonicalJobMappingSupport.rawField(
                        PROVIDER, source.getExternalJobId(), "salaryText",
                        source.getSalaryText()),
                CanonicalJobMappingSupport.timestampField(
                        PROVIDER, source.getExternalJobId(), "postedAt",
                        source.getPostedAt(), job.getPostedAtUtc()),
                CanonicalJobMappingSupport.timestampField(
                        PROVIDER, source.getExternalJobId(), "closesAt",
                        source.getClosesAt(), job.getExpiresAtUtc())));
        return job;
    }

    private OffsetDateTime parseProviderDate(String value) {
        OffsetDateTime parsed =
                CanonicalJobMappingSupport.parseOffsetDateTime(value);
        if (parsed != null || value == null || value.isBlank()) {
            return parsed;
        }
        try {
            return LocalDate.parse(value).atStartOfDay()
                    .atOffset(ZoneOffset.UTC);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
