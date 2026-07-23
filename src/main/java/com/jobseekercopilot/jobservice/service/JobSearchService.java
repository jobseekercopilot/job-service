package com.jobseekercopilot.jobservice.service;

import com.jobseekercopilot.jobservice.model.dto.Aspirations;
import com.jobseekercopilot.jobservice.model.dto.CanonicalLocation;
import com.jobseekercopilot.jobservice.model.dto.HomeLocation;
import com.jobseekercopilot.jobservice.model.dto.Job;
import com.jobseekercopilot.jobservice.model.dto.JobSearchRequest;
import com.jobseekercopilot.jobservice.model.dto.ProviderResultStatus;
import com.jobseekercopilot.jobservice.model.dto.ReedJobSearchResponse;
import com.jobseekercopilot.jobservice.model.dto.SalaryExpectation;
import com.jobseekercopilot.jobservice.model.dto.WorkPreferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class JobSearchService {
    private static final Logger log = LoggerFactory.getLogger(JobSearchService.class);
    private static final int DEFAULT_DISTANCE = 25;
    private static final int DEFAULT_PAGE = 1;
    private static final int RESPONSE_PAGE_SIZE = 10;

    private final List<JobProviderAdapter> providerAdapters;
    private final JobDeduplicationService deduplicationService;
    private final JobResultEnrichmentService jobResultEnrichmentService;
    private final DistanceCalculationService distanceCalculationService;
    private final JobMatchingClient jobMatchingClient;
    private final int cacheTtlMinutes;
    private final Map<String, CacheEntry> searchCache = new ConcurrentHashMap<>();

    public JobSearchService(List<JobProviderAdapter> providerAdapters,
                            JobDeduplicationService deduplicationService,
                            JobResultEnrichmentService jobResultEnrichmentService,
                            DistanceCalculationService distanceCalculationService,
                            JobMatchingClient jobMatchingClient,
                            @Value("${job.search.cache-ttl-minutes:${JOB_SEARCH_CACHE_TTL_MINUTES:10}}") int cacheTtlMinutes) {
        this.providerAdapters = providerAdapters;
        this.deduplicationService = deduplicationService;
        this.jobResultEnrichmentService = jobResultEnrichmentService;
        this.distanceCalculationService = distanceCalculationService;
        this.jobMatchingClient = jobMatchingClient;
        this.cacheTtlMinutes = cacheTtlMinutes;
    }

    public ReedJobSearchResponse searchJobs(String userId, JobSearchRequest request) {
        long searchStartedAt = System.nanoTime();
        validateRequest(request);

        List<String> targetRoles = targetRoles(request);
        Set<String> selectedProviders = selectedProviders(request);
        log.info("Job search started userId={} roles={} providers={} location={}",
                userId,
                targetRoles.size(),
                selectedProviders.isEmpty() ? "ALL" : String.join(",", selectedProviders),
                request.getAspirations().getLocations().get(0));
        List<ReedJobSearchResponse.TargetRoleJobResults> resultsByTargetRole = new ArrayList<>();
        List<ProviderResultStatus> providerResults = new ArrayList<>();
        List<Job> allJobs = new ArrayList<>();

        for (String targetRole : targetRoles) {
            JobSearchCriteria criteria = criteria(request, targetRole);
            ProviderSearchResult providerSearchResult = cachedProviderSearch(userId, criteria, request);
            providerResults.addAll(providerSearchResult.providerResults());
            applyDistance(request, providerSearchResult.jobs());
            long matchingStartedAt = System.nanoTime();
            List<Job> enrichedJobs = jobMatchingClient.enrichJobs(userId, providerSearchResult.jobs());
            log.info("Job matching complete targetRole={} enrichedCount={} durationMs={}",
                    targetRole,
                    enrichedJobs.size(),
                    (System.nanoTime() - matchingStartedAt) / 1_000_000);
            resultsByTargetRole.add(new ReedJobSearchResponse.TargetRoleJobResults(targetRole, enrichedJobs));
            allJobs.addAll(enrichedJobs);
        }

        log.info("Job search completed userId={} roles={} finalCount={} durationMs={}",
                userId,
                targetRoles.size(),
                allJobs.size(),
                (System.nanoTime() - searchStartedAt) / 1_000_000);
        return new ReedJobSearchResponse(
                allJobs,
                resultsByTargetRole,
                allJobs.size(),
                DEFAULT_PAGE,
                RESPONSE_PAGE_SIZE,
                providerResults);
    }

    private ProviderSearchResult cachedProviderSearch(String userId, JobSearchCriteria criteria, JobSearchRequest request) {
        String cacheKey = cacheKey(criteria, request);
        CacheEntry cached = searchCache.get(cacheKey);
        if (cached != null && !cached.expired(cacheTtlMinutes)) {
            log.info("Job provider search cache hit targetRole={} location={} jobs={}",
                    criteria.getTargetRole(),
                    criteria.getLocation(),
                    cached.result().jobs().size());
            return cached.result();
        }
        log.info("Job provider search cache miss targetRole={} location={}",
                criteria.getTargetRole(),
                criteria.getLocation());
        ProviderSearchResult fresh = searchProviders(userId, criteria, request);
        searchCache.put(cacheKey, new CacheEntry(Instant.now(), fresh));
        return fresh;
    }

    private ProviderSearchResult searchProviders(String userId, JobSearchCriteria criteria, JobSearchRequest request) {
        long startedAt = System.nanoTime();
        List<Job> rawJobs = new ArrayList<>();
        List<ProviderResultStatus> providerResults = new ArrayList<>();
        Set<String> selectedProviders = selectedProviders(request);
        log.info("Provider search started userId={} targetRole={} providers={} location={}",
                userId,
                criteria.getTargetRole(),
                selectedProviders.isEmpty() ? "ALL" : String.join(",", selectedProviders),
                criteria.getLocation());

        for (JobProviderAdapter adapter : providerAdapters) {
            if (!selectedProviders.isEmpty() && !selectedProviders.contains(adapter.provider())) {
                log.debug("Provider {} skipped by request selection", adapter.provider());
                continue;
            }
            if (!adapter.isEnabled()) {
                log.info("Provider {} disabled", adapter.provider());
                providerResults.add(new ProviderResultStatus(adapter.provider(), "DISABLED", 0, null));
                continue;
            }
            long providerStartedAt = System.nanoTime();
            try {
                List<Job> providerJobs = adapter.search(userId, criteria);
                rawJobs.addAll(providerJobs);
                log.info("Provider {} returned rawCount={} durationMs={}",
                        adapter.provider(),
                        providerJobs.size(),
                        (System.nanoTime() - providerStartedAt) / 1_000_000);
                providerResults.add(new ProviderResultStatus(adapter.provider(), "SUCCESS", providerJobs.size(), null));
            } catch (Exception ex) {
                log.warn("Provider {} failed durationMs={} error={}",
                        adapter.provider(),
                        (System.nanoTime() - providerStartedAt) / 1_000_000,
                        ex.getClass().getSimpleName(),
                        ex);
                providerResults.add(new ProviderResultStatus(
                        adapter.provider(), "UNAVAILABLE", 0, "Provider temporarily unavailable"));
            }
        }

        long deduplicationStartedAt = System.nanoTime();
        List<Job> uniqueJobs = deduplicationService.deduplicate(rawJobs);
        long deduplicationDurationMs = (System.nanoTime() - deduplicationStartedAt) / 1_000_000;
        log.info("Deduplication complete targetRole={} rawCount={} uniqueCount={} duplicatesRemoved={} durationMs={}",
                criteria.getTargetRole(),
                rawJobs.size(),
                uniqueJobs.size(),
                rawJobs.size() - uniqueJobs.size(),
                deduplicationDurationMs);
        long normalisationStartedAt = System.nanoTime();
        List<Job> enrichedProviderJobs = jobResultEnrichmentService.enrich(criteria, uniqueJobs);
        log.info("Job normalisation complete targetRole={} count={} durationMs={}",
                criteria.getTargetRole(),
                enrichedProviderJobs.size(),
                (System.nanoTime() - normalisationStartedAt) / 1_000_000);
        log.info("Provider search complete targetRole={} rawCount={} uniqueCount={} durationMs={}",
                criteria.getTargetRole(),
                rawJobs.size(),
                enrichedProviderJobs.size(),
                (System.nanoTime() - startedAt) / 1_000_000);
        return new ProviderSearchResult(enrichedProviderJobs, providerResults);
    }

    private void applyDistance(JobSearchRequest request, List<Job> jobs) {
        Double userLatitude = homeLatitude(request);
        Double userLongitude = homeLongitude(request);
        for (Job job : jobs) {
            CanonicalLocation location = job.getCanonicalLocation();
            Double jobLatitude = location == null || location.getLatitude() == null ? null : location.getLatitude().doubleValue();
            Double jobLongitude = location == null || location.getLongitude() == null ? null : location.getLongitude().doubleValue();
            job.setDistanceMiles(distanceCalculationService.calculateMiles(userLatitude, userLongitude, jobLatitude, jobLongitude));
        }
    }

    private Double homeLatitude(JobSearchRequest request) {
        HomeLocation homeLocation = request.getHomeLocation();
        if (homeLocation != null && homeLocation.getLatitude() != null) {
            return homeLocation.getLatitude();
        }
        WorkPreferences workPreferences = request.getWorkPreferences();
        return workPreferences == null ? null : workPreferences.getHomeLatitude();
    }

    private Double homeLongitude(JobSearchRequest request) {
        HomeLocation homeLocation = request.getHomeLocation();
        if (homeLocation != null && homeLocation.getLongitude() != null) {
            return homeLocation.getLongitude();
        }
        WorkPreferences workPreferences = request.getWorkPreferences();
        return workPreferences == null ? null : workPreferences.getHomeLongitude();
    }

    private JobSearchCriteria criteria(JobSearchRequest request, String targetRole) {
        Aspirations aspirations = request.getAspirations();
        WorkPreferences workPreferences = request.getWorkPreferences();
        SalaryExpectation salary = aspirations.getSalaryExpectation();
        return new JobSearchCriteria(
                request,
                targetRole,
                aspirations.getLocations().get(0),
                DEFAULT_DISTANCE,
                workPreferences == null ? List.of() : workPreferences.getEmploymentType(),
                salary == null ? null : salary.getMin(),
                salary == null ? null : salary.getMax(),
                salary == null ? null : salary.getCurrency(),
                false);
    }

    private void validateRequest(JobSearchRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        Aspirations aspirations = request.getAspirations();
        if (aspirations == null) {
            throw new IllegalArgumentException("Missing required field: aspirations");
        }
        if (aspirations.getDesiredRoles() == null || aspirations.getDesiredRoles().stream()
                .noneMatch(role -> role != null && !role.isBlank())) {
            throw new IllegalArgumentException("Missing required field: aspirations.desiredRoles");
        }
        if (aspirations.getLocations() == null || aspirations.getLocations().isEmpty()) {
            throw new IllegalArgumentException("Missing required field: aspirations.locations");
        }
        WorkPreferences workPreferences = request.getWorkPreferences();
        if (workPreferences != null && workPreferences.getEmploymentType() != null) {
            for (String employmentType : workPreferences.getEmploymentType()) {
                validateEmploymentType(employmentType);
            }
        }
        if (aspirations.getSalaryExpectation() != null) {
            SalaryExpectation salary = aspirations.getSalaryExpectation();
            if (salary.getMin() != null && salary.getMax() != null && salary.getMin() > salary.getMax()) {
                throw new IllegalArgumentException("Invalid salary range: min cannot be greater than max");
            }
        }
    }

    private void validateEmploymentType(String employmentType) {
        if (employmentType == null || employmentType.isBlank()) {
            return;
        }
        String normalized = employmentType.trim().toUpperCase(Locale.ROOT);
        boolean valid = switch (normalized) {
            case "FULL_TIME", "PART_TIME", "CONTRACT", "TEMPORARY" -> true;
            default -> false;
        };
        if (!valid) {
            throw new IllegalArgumentException("Invalid employment type: " + employmentType);
        }
    }

    private List<String> targetRoles(JobSearchRequest request) {
        return request.getAspirations().getDesiredRoles().stream()
                .filter(role -> role != null && !role.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private Set<String> selectedProviders(JobSearchRequest request) {
        return request.getSelectedProviders() == null
                ? Set.of()
                : request.getSelectedProviders().stream()
                        .filter(value -> value != null && !value.isBlank())
                        .map(value -> value.trim().toUpperCase(Locale.ROOT))
                        .collect(Collectors.toSet());
    }

    private String cacheKey(JobSearchCriteria criteria, JobSearchRequest request) {
        Map<String, Object> parts = new LinkedHashMap<>();
        parts.put("targetRole", criteria.getTargetRole().toLowerCase(Locale.ROOT));
        parts.put("location", criteria.getLocation().toLowerCase(Locale.ROOT));
        parts.put("distance", criteria.getDistanceMiles());
        parts.put("remote", criteria.isRemoteOnly());
        parts.put("providers", selectedProviders(request).stream().sorted().toList());
        return parts.toString();
    }

    private record ProviderSearchResult(List<Job> jobs, List<ProviderResultStatus> providerResults) {
    }

    private record CacheEntry(Instant createdAt, ProviderSearchResult result) {
        boolean expired(int ttlMinutes) {
            return createdAt.plusSeconds((long) ttlMinutes * 60).isBefore(Instant.now());
        }
    }

    public static class DownstreamServiceUnavailableException extends RuntimeException {
        public DownstreamServiceUnavailableException(String message) {
            super(message);
        }

        public DownstreamServiceUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
