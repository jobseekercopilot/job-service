package com.jobseekercopilot.jobservice.service;

import com.jobseekercopilot.generated.jsearchgateway.api.JSearchJobsApi;
import com.jobseekercopilot.generated.jsearchgateway.model.JSearchApplyOption;
import com.jobseekercopilot.generated.jsearchgateway.model.JSearchJob;
import com.jobseekercopilot.generated.jsearchgateway.model.JSearchSearchRequest;
import com.jobseekercopilot.generated.jsearchgateway.model.JSearchSearchResponse;
import com.jobseekercopilot.jobservice.model.dto.CanonicalLocation;
import com.jobseekercopilot.jobservice.model.dto.CanonicalValueStatus;
import com.jobseekercopilot.jobservice.model.dto.Job;
import com.jobseekercopilot.jobservice.model.dto.JobSalary;
import com.jobseekercopilot.jobservice.model.dto.JobSourceReference;
import com.jobseekercopilot.jobservice.model.dto.WorkplaceTypeCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class JSearchJobProviderAdapter implements JobProviderAdapter {
    private static final Pattern UK_POSTCODE = Pattern.compile("^[A-Z]{1,2}\\d[A-Z\\d]?\\s*\\d[A-Z]{2}$", Pattern.CASE_INSENSITIVE);

    private final JSearchJobsApi jSearchJobsApi;
    private final PublisherNormalisationService publisherNormalisationService;
    private final boolean enabled;

    public JSearchJobProviderAdapter(JSearchJobsApi jSearchJobsApi,
                                     PublisherNormalisationService publisherNormalisationService,
                                     @Value("${providers.jsearch.enabled:${JSEARCH_ENABLED:true}}") boolean enabled) {
        this.jSearchJobsApi = jSearchJobsApi;
        this.publisherNormalisationService = publisherNormalisationService;
        this.enabled = enabled;
    }

    @Override
    public String provider() {
        return "JSEARCH";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public List<Job> search(String userId, JobSearchCriteria criteria) {
        JSearchSearchRequest request = new JSearchSearchRequest();
        request.setTargetRole(criteria.getTargetRole());
        request.setLocation(jsearchLocation(criteria));
        request.setRemoteOnly(criteria.isRemoteOnly());
        JSearchSearchResponse response = jSearchJobsApi.search(request);
        return response == null || response.getJobs() == null
                ? List.of()
                : response.getJobs().stream().map(this::toJob).toList();
    }

    private String jsearchLocation(JobSearchCriteria criteria) {
        String primaryLocation = criteria.getLocation();
        if (!isUkPostcode(primaryLocation)) {
            return primaryLocation;
        }
        if (criteria.getRequest() != null
                && criteria.getRequest().getAspirations() != null
                && criteria.getRequest().getAspirations().getLocations() != null) {
            return criteria.getRequest().getAspirations().getLocations().stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::trim)
                    .filter(value -> !isUkPostcode(value))
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    private boolean isUkPostcode(String value) {
        return value != null && UK_POSTCODE.matcher(value.trim()).matches();
    }

    private Job toJob(JSearchJob source) {
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
        location.setRawCity(source.getCity());
        location.setRawRegion(source.getState());
        location.setRawCountry(source.getCountry());
        location.setDisplayName(source.getLocationDisplayName());
        location.setAreaParts(List.of(nonNull(source.getCountry()), nonNull(source.getState()), nonNull(source.getCity())).stream()
                .filter(value -> !value.isBlank())
                .toList());
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
                    source.getSalaryCurrency(),
                    source.getSalaryPeriod());
            salary.setSourceProvider(provider());
            job.setSalary(salary);
        }
        job.setEmploymentType(source.getEmploymentType());
        job.setPostedDate(source.getPostedAt());
        job.setPostedAt(source.getPostedAt());
        job.setExpiresAt(source.getExpiresAt());
        job.setPostedAtUtc(
                CanonicalJobMappingSupport.parseOffsetDateTime(
                        source.getPostedAt()));
        job.setExpiresAtUtc(
                CanonicalJobMappingSupport.parseOffsetDateTime(
                        source.getExpiresAt()));
        job.setRemote(source.getRemote());
        if (Boolean.TRUE.equals(source.getRemote())) {
            job.setWorkplaceType(WorkplaceTypeCode.REMOTE);
        }
        job.setDescription(source.getDescription());
        job.setUrl(primaryApplyUrl(source));
        job.setSourceUrl(primaryApplyUrl(source));
        job.setSources(sourceReferences(source));
        List<com.jobseekercopilot.jobservice.model.dto.JobFieldProvenance>
                provenance = new ArrayList<>();
        provenance.add(CanonicalJobMappingSupport.rawField(
                provider(), source.getExternalJobId(), "title",
                source.getTitle()));
        provenance.add(CanonicalJobMappingSupport.rawField(
                provider(), source.getExternalJobId(), "employmentType",
                source.getEmploymentType()));
        provenance.add(CanonicalJobMappingSupport.timestampField(
                provider(), source.getExternalJobId(), "postedAt",
                source.getPostedAt(), job.getPostedAtUtc()));
        provenance.add(CanonicalJobMappingSupport.timestampField(
                provider(), source.getExternalJobId(), "expiresAt",
                source.getExpiresAt(), job.getExpiresAtUtc()));
        if (Boolean.TRUE.equals(source.getRemote())) {
            provenance.add(CanonicalJobMappingSupport.normalisedField(
                    provider(), source.getExternalJobId(), "workplaceType",
                    source.getRemote(), WorkplaceTypeCode.REMOTE));
        } else {
            provenance.add(CanonicalJobMappingSupport.rawField(
                    provider(), source.getExternalJobId(), "workplaceType",
                    source.getRemote()));
        }
        job.setFieldProvenance(provenance);
        return job;
    }

    private List<JobSourceReference> sourceReferences(JSearchJob source) {
        List<JobSourceReference> references = new ArrayList<>();
        if (source.getApplyOptions() != null && !source.getApplyOptions().isEmpty()) {
            for (JSearchApplyOption option : source.getApplyOptions()) {
                references.add(reference(source, option.getPublisher(), option.getApplyUrl(), option.getDirect()));
            }
        } else {
            references.add(reference(source, source.getPublisher(), source.getPrimaryApplyUrl(), source.getDirectApply()));
        }
        return references;
    }

    private JobSourceReference reference(JSearchJob source, String publisher, String applyUrl, Boolean direct) {
        JobSourceReference reference = new JobSourceReference();
        reference.setProvider(provider());
        reference.setExternalJobId(source.getExternalJobId());
        reference.setRawPublisher(publisher);
        reference.setPublisher(publisherNormalisationService.normalise(publisher, applyUrl, direct, "Other Job Site"));
        String safeApplyUrl = CanonicalUrlPolicy.safeHttpUrl(applyUrl);
        reference.setListingUrl(safeApplyUrl);
        reference.setApplyUrl(safeApplyUrl);
        reference.setDirectApply(direct);
        reference.setProviderPostedAt(parseDateTime(source.getPostedAt()));
        reference.setProviderPostedAtRaw(source.getPostedAt());
        reference.setProviderPostedAtUtc(
                CanonicalJobMappingSupport.parseOffsetDateTime(
                        source.getPostedAt()));
        reference.setProviderExpiresAtRaw(source.getExpiresAt());
        reference.setProviderExpiresAtUtc(
                CanonicalJobMappingSupport.parseOffsetDateTime(
                        source.getExpiresAt()));
        return reference;
    }

    private String primaryApplyUrl(JSearchJob source) {
        if (source.getApplyOptions() != null) {
            return source.getApplyOptions().stream()
                    .filter(option -> Boolean.TRUE.equals(option.getDirect()) && option.getApplyUrl() != null)
                    .map(JSearchApplyOption::getApplyUrl)
                    .map(CanonicalUrlPolicy::safeHttpUrl)
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(CanonicalUrlPolicy.safeHttpUrl(
                            source.getPrimaryApplyUrl()));
        }
        return CanonicalUrlPolicy.safeHttpUrl(
                source.getPrimaryApplyUrl());
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

    private String nonNull(String value) {
        return value == null ? "" : value;
    }
}
