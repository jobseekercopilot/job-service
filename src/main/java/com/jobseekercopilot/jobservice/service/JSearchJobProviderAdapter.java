package com.jobseekercopilot.jobservice.service;

import com.jobseekercopilot.jobservice.model.dto.CanonicalLocation;
import com.jobseekercopilot.jobservice.model.dto.JSearchApplyOption;
import com.jobseekercopilot.jobservice.model.dto.JSearchJob;
import com.jobseekercopilot.jobservice.model.dto.JSearchSearchRequest;
import com.jobseekercopilot.jobservice.model.dto.JSearchSearchResponse;
import com.jobseekercopilot.jobservice.model.dto.Job;
import com.jobseekercopilot.jobservice.model.dto.JobSalary;
import com.jobseekercopilot.jobservice.model.dto.JobSourceReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class JSearchJobProviderAdapter implements JobProviderAdapter {
    private static final Pattern UK_POSTCODE = Pattern.compile("^[A-Z]{1,2}\\d[A-Z\\d]?\\s*\\d[A-Z]{2}$", Pattern.CASE_INSENSITIVE);

    private final RestTemplate restTemplate;
    private final PublisherNormalisationService publisherNormalisationService;
    private final String baseUrl;
    private final boolean enabled;

    public JSearchJobProviderAdapter(RestTemplate restTemplate,
                                     PublisherNormalisationService publisherNormalisationService,
                                     @Value("${services.jsearch-gateway.url:http://jsearch-gateway:8102}") String baseUrl,
                                     @Value("${providers.jsearch.enabled:${JSEARCH_ENABLED:true}}") boolean enabled) {
        this.restTemplate = restTemplate;
        this.publisherNormalisationService = publisherNormalisationService;
        this.baseUrl = baseUrl;
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
        JSearchSearchResponse response = restTemplate.postForObject(
                baseUrl + "/api/v1/jsearch/jobs/search",
                new JSearchSearchRequest(criteria.getTargetRole(), jsearchLocation(criteria), criteria.isRemoteOnly(), null),
                JSearchSearchResponse.class);
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
        location.setDisplayName(source.getLocationDisplayName());
        location.setAreaParts(List.of(nonNull(source.getCountry()), nonNull(source.getState()), nonNull(source.getCity())).stream()
                .filter(value -> !value.isBlank())
                .toList());
        location.setLatitude(source.getLatitude());
        location.setLongitude(source.getLongitude());
        job.setCanonicalLocation(location);
        if (source.getSalaryMinimum() != null || source.getSalaryMaximum() != null) {
            job.setSalary(new JobSalary(source.getSalaryMinimum(), source.getSalaryMaximum(), source.getSalaryCurrency(), source.getSalaryPeriod()));
        }
        job.setEmploymentType(source.getEmploymentType());
        job.setPostedDate(source.getPostedAt());
        job.setPostedAt(source.getPostedAt());
        job.setExpiresAt(source.getExpiresAt());
        job.setRemote(source.getRemote());
        job.setDescription(source.getDescription());
        job.setUrl(primaryApplyUrl(source));
        job.setSourceUrl(primaryApplyUrl(source));
        job.setSources(sourceReferences(source));
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
        reference.setPublisher(publisherNormalisationService.normalise(publisher, applyUrl, direct, "Other Job Site"));
        reference.setListingUrl(applyUrl);
        reference.setApplyUrl(applyUrl);
        reference.setDirectApply(direct);
        reference.setProviderPostedAt(parseDateTime(source.getPostedAt()));
        return reference;
    }

    private String primaryApplyUrl(JSearchJob source) {
        if (source.getApplyOptions() != null) {
            return source.getApplyOptions().stream()
                    .filter(option -> Boolean.TRUE.equals(option.getDirect()) && option.getApplyUrl() != null)
                    .map(JSearchApplyOption::getApplyUrl)
                    .findFirst()
                    .orElse(source.getPrimaryApplyUrl());
        }
        return source.getPrimaryApplyUrl();
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
