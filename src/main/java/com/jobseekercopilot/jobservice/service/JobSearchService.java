package com.jobseekercopilot.jobservice.service;

import com.jobseekercopilot.jobservice.model.dto.Aspirations;
import com.jobseekercopilot.jobservice.model.dto.CanonicalLocation;
import com.jobseekercopilot.jobservice.model.dto.HomeLocation;
import com.jobseekercopilot.jobservice.model.dto.Job;
import com.jobseekercopilot.jobservice.model.dto.JobExperience;
import com.jobseekercopilot.jobservice.model.dto.JobFieldProvenance;
import com.jobseekercopilot.jobservice.model.dto.JobSalary;
import com.jobseekercopilot.jobservice.model.dto.JobSearchRequest;
import com.jobseekercopilot.jobservice.model.dto.JobSkill;
import com.jobseekercopilot.jobservice.model.dto.JobSourceReference;
import com.jobseekercopilot.jobservice.model.dto.ProviderResultStatus;
import com.jobseekercopilot.jobservice.model.dto.ReedJobSearchResponse;
import com.jobseekercopilot.jobservice.model.dto.SalaryExpectation;
import com.jobseekercopilot.jobservice.model.dto.WorkPreferences;
import com.jobseekercopilot.jobservice.config.JobSearchResilienceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class JobSearchService {
    private static final Logger log = LoggerFactory.getLogger(JobSearchService.class);
    private static final int DEFAULT_DISTANCE = 25;
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE = 100;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_TARGET_ROLES = 10;
    private static final int MAX_AGGREGATE_RESULTS = 1_000;
    private static final String DEFAULT_SORT = "MOST_RELEVANT";
    private static final Set<String> SUPPORTED_SORTS = Set.of(
            DEFAULT_SORT,
            "CLOSEST",
            "HIGHEST_SALARY",
            "NEWEST_POSTED",
            "OLDEST_POSTED",
            "COMPANY_AZ",
            "JOB_TITLE_AZ");

    private final ProviderSearchCoordinator providerSearchCoordinator;
    private final JobDeduplicationService deduplicationService;
    private final JobResultEnrichmentService jobResultEnrichmentService;
    private final DistanceCalculationService distanceCalculationService;
    private final OptionalJobMatchingEnricher jobMatchingEnricher;
    private final JobSearchResilienceProperties resilience;
    private final int cacheTtlMinutes;
    private final Map<String, CacheEntry> searchCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry> partialPageCache =
            new ConcurrentHashMap<>();

    public JobSearchService(ProviderSearchCoordinator providerSearchCoordinator,
                            JobDeduplicationService deduplicationService,
                            JobResultEnrichmentService jobResultEnrichmentService,
                            DistanceCalculationService distanceCalculationService,
                            OptionalJobMatchingEnricher jobMatchingEnricher,
                            JobSearchResilienceProperties resilience,
                            @Value("${job.search.cache-ttl-minutes:${JOB_SEARCH_CACHE_TTL_MINUTES:10}}") int cacheTtlMinutes) {
        this.providerSearchCoordinator = providerSearchCoordinator;
        this.deduplicationService = deduplicationService;
        this.jobResultEnrichmentService = jobResultEnrichmentService;
        this.distanceCalculationService = distanceCalculationService;
        this.jobMatchingEnricher = jobMatchingEnricher;
        this.resilience = resilience;
        this.cacheTtlMinutes = cacheTtlMinutes;
    }

    public ReedJobSearchResponse searchJobs(String userId, JobSearchRequest request) {
        long searchStartedAt = System.nanoTime();
        long requestDeadlineNanos = searchStartedAt
                + TimeUnit.MILLISECONDS.toNanos(resilience.getRequestTimeoutMs());
        validateRequest(request);

        List<String> targetRoles = targetRoles(request);
        int page = page(request);
        int pageSize = pageSize(request);
        String sort = sort(request);
        Set<String> selectedProviders = selectedProviders(request);
        log.info("Job search started roles={} providers={} page={} pageSize={} sort={}",
                targetRoles.size(),
                selectedProviders.isEmpty() ? "ALL" : String.join(",", selectedProviders),
                page,
                pageSize,
                sort);
        List<ProviderResultStatus> providerResults = new ArrayList<>();
        List<RoleSearchResult> roleResults = new ArrayList<>();
        boolean anyProviderSuccess = false;
        boolean anyProviderAttempted = false;
        boolean partial = false;
        String matchingStatus = "NOT_RUN";

        for (String targetRole : targetRoles) {
            if (System.nanoTime() >= requestDeadlineNanos) {
                anyProviderAttempted = true;
                ProviderResultStatus timeoutStatus = new ProviderResultStatus(
                        "REQUEST",
                        "TIMED_OUT",
                        0,
                        "Job search deadline reached");
                providerResults.add(timeoutStatus);
                roleResults.add(new RoleSearchResult(
                        targetRole,
                        List.of(),
                        List.of(timeoutStatus),
                        "UNAVAILABLE",
                        "NOT_RUN"));
                partial = true;
                continue;
            }
            JobSearchCriteria criteria = criteria(request, targetRole);
            ProviderSearchResult providerSearchResult = cachedProviderSearch(
                    userId,
                    criteria,
                    request,
                    requestDeadlineNanos);
            providerResults.addAll(providerSearchResult.providerResults());
            anyProviderSuccess |= providerSearchResult.anySuccess();
            anyProviderAttempted |= providerSearchResult.anyAttempted();
            partial |= !providerSearchResult.complete();

            if (!providerSearchResult.anySuccess()) {
                roleResults.add(new RoleSearchResult(
                        targetRole,
                        List.of(),
                        providerSearchResult.providerResults(),
                        "UNAVAILABLE",
                        "NOT_RUN"));
                continue;
            }

            applyDistance(request, providerSearchResult.jobs());
            OptionalJobMatchingEnricher.MatchingOutcome matchingOutcome =
                    jobMatchingEnricher.enrich(
                            userId,
                            providerSearchResult.jobs(),
                            requestDeadlineNanos);
            List<Job> enrichedJobs = matchingOutcome.jobs();
            matchingStatus = mergeMatchingStatus(
                    matchingStatus,
                    matchingOutcome.status());
            partial |= matchingOutcome.degraded();
            List<Job> boundedRoleJobs = enrichedJobs.stream()
                    .map(job -> new RoleJob(targetRole, job))
                    .sorted(resultOrder(sort))
                    .map(RoleJob::job)
                    .toList();
            String roleSearchStatus =
                    !providerSearchResult.complete() || matchingOutcome.degraded()
                            ? "PARTIAL"
                            : "COMPLETE";
            roleResults.add(new RoleSearchResult(
                    targetRole,
                    boundedRoleJobs,
                    providerSearchResult.providerResults(),
                    roleSearchStatus,
                    matchingOutcome.status()));
        }

        if (!anyProviderSuccess) {
            String reason = anyProviderAttempted
                    ? "All requested providers are unavailable"
                    : "No requested provider is enabled";
            throw new DownstreamServiceUnavailableException(reason);
        }

        List<RolePage> rolePages = roleResults.stream()
                .map(result -> page(result, page, pageSize))
                .toList();
        List<RoleJob> boundedJobs = roleResults.stream()
                .flatMap(result -> result.jobs().stream()
                        .map(job -> new RoleJob(result.targetRole(), job)))
                .sorted(resultOrder(sort))
                .limit(MAX_AGGREGATE_RESULTS)
                .toList();
        int totalResults = boundedJobs.size();
        long requestedStart = (long) (page - 1) * pageSize;
        int fromIndex = (int) Math.min(requestedStart, totalResults);
        int toIndex = Math.min(fromIndex + pageSize, totalResults);
        List<RoleJob> pageRows = boundedJobs.subList(fromIndex, toIndex);
        List<Job> pageJobs = pageRows.stream().map(RoleJob::job).toList();
        List<ReedJobSearchResponse.TargetRoleJobResults> resultsByTargetRole =
                rolePages.stream()
                        .map(rolePage ->
                                new ReedJobSearchResponse.TargetRoleJobResults(
                                        rolePage.targetRole(),
                                        rolePage.jobs(),
                                        rolePage.totalResults(),
                                        page,
                                        pageSize,
                                        rolePage.providerResults(),
                                        rolePage.searchStatus(),
                                        rolePage.matchingStatus()))
                        .toList();

        String searchStatus = partial ? "PARTIAL" : "COMPLETE";
        log.info("Job search completed roles={} availableCount={} returnedCount={} durationMs={}",
                targetRoles.size(),
                totalResults,
                pageJobs.size(),
                (System.nanoTime() - searchStartedAt) / 1_000_000);
        ReedJobSearchResponse response = new ReedJobSearchResponse(
                pageJobs,
                resultsByTargetRole,
                totalResults,
                page,
                pageSize,
                providerResults,
                searchStatus,
                matchingStatus);
        response.setSort(sort);
        return response;
    }

    public Job getJobDetails(
            String userId,
            String provider,
            String externalJobId) {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("Provider is required");
        }
        if (externalJobId == null || externalJobId.isBlank()) {
            throw new IllegalArgumentException("External job ID is required");
        }
        return providerSearchCoordinator.details(
                        userId,
                        provider,
                        externalJobId)
                .orElseThrow(() -> new JobNotFoundException(
                        "Job details are not available"));
    }

    private RolePage page(
            RoleSearchResult result,
            int page,
            int pageSize) {
        int totalResults = result.jobs().size();
        long requestedStart = (long) (page - 1) * pageSize;
        int fromIndex = (int) Math.min(requestedStart, totalResults);
        int toIndex = Math.min(fromIndex + pageSize, totalResults);
        return new RolePage(
                result.targetRole(),
                result.jobs().subList(fromIndex, toIndex),
                totalResults,
                result.providerResults(),
                result.searchStatus(),
                result.matchingStatus());
    }

    private ProviderSearchResult cachedProviderSearch(
            String userId,
            JobSearchCriteria criteria,
            JobSearchRequest request,
            long requestDeadlineNanos) {
        String cacheKey = cacheKey(criteria, request);
        String ownerPageCacheKey = ownerPageCacheKey(userId, cacheKey);
        if (page(request) > 1) {
            CacheEntry partialPage = partialPageCache.get(ownerPageCacheKey);
            if (partialPage != null && !partialPage.expired(cacheTtlMinutes)) {
                log.info("Job provider partial-page cache hit jobs={}",
                        partialPage.result().jobs().size());
                return copyProviderSearchResult(partialPage.result());
            }
            if (partialPage != null) {
                partialPageCache.remove(ownerPageCacheKey, partialPage);
            }
        }
        CacheEntry cached = searchCache.get(cacheKey);
        if (cached != null && !cached.expired(cacheTtlMinutes)) {
            log.info("Job provider search cache hit jobs={}",
                    cached.result().jobs().size());
            return copyProviderSearchResult(cached.result());
        }
        log.info("Job provider search cache miss");
        ProviderSearchResult fresh = searchProviders(
                userId,
                criteria,
                request,
                requestDeadlineNanos);
        if (fresh.complete() && fresh.anySuccess()) {
            searchCache.put(
                    cacheKey,
                    new CacheEntry(
                            Instant.now(),
                            copyProviderSearchResult(fresh)));
            partialPageCache.remove(ownerPageCacheKey);
        } else if (fresh.anySuccess()) {
            partialPageCache.put(
                    ownerPageCacheKey,
                    new CacheEntry(
                            Instant.now(),
                            copyProviderSearchResult(fresh)));
        }
        return copyProviderSearchResult(fresh);
    }

    private ProviderSearchResult searchProviders(
            String userId,
            JobSearchCriteria criteria,
            JobSearchRequest request,
            long requestDeadlineNanos) {
        long startedAt = System.nanoTime();
        Set<String> selectedProviders = selectedProviders(request);
        log.info("Provider search started providers={}",
                selectedProviders.isEmpty() ? "ALL" : String.join(",", selectedProviders));
        ProviderSearchCoordinator.ProviderFanOutResult fanOut;
        try {
            fanOut = providerSearchCoordinator.search(
                    userId,
                    criteria,
                    selectedProviders,
                    requestDeadlineNanos);
        } catch (ProviderSearchCoordinator.ProviderCoordinationException exception) {
            throw new DownstreamServiceUnavailableException(
                    "Provider coordination is unavailable",
                    exception);
        }
        List<Job> rawJobs = fanOut.jobs();

        long deduplicationStartedAt = System.nanoTime();
        List<Job> uniqueJobs = deduplicationService.deduplicate(rawJobs);
        long deduplicationDurationMs = (System.nanoTime() - deduplicationStartedAt) / 1_000_000;
        log.info("Deduplication complete rawCount={} uniqueCount={} duplicatesRemoved={} durationMs={}",
                rawJobs.size(),
                uniqueJobs.size(),
                rawJobs.size() - uniqueJobs.size(),
                deduplicationDurationMs);
        long normalisationStartedAt = System.nanoTime();
        List<Job> enrichedProviderJobs = jobResultEnrichmentService.enrich(criteria, uniqueJobs);
        log.info("Job normalisation complete count={} durationMs={}",
                enrichedProviderJobs.size(),
                (System.nanoTime() - normalisationStartedAt) / 1_000_000);
        log.info("Provider search complete rawCount={} uniqueCount={} durationMs={}",
                rawJobs.size(),
                enrichedProviderJobs.size(),
                (System.nanoTime() - startedAt) / 1_000_000);
        return new ProviderSearchResult(
                enrichedProviderJobs,
                fanOut.providerResults(),
                fanOut.anyAttempted(),
                fanOut.anySuccess(),
                fanOut.complete());
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
        List<String> employmentTypes =
                workPreferences == null || workPreferences.getEmploymentType() == null
                        ? List.of()
                        : workPreferences.getEmploymentType();
        return new JobSearchCriteria(
                request,
                targetRole,
                aspirations.getLocations().get(0),
                DEFAULT_DISTANCE,
                employmentTypes,
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
        if (targetRoles(request).size() > MAX_TARGET_ROLES) {
            throw new IllegalArgumentException(
                    "aspirations.desiredRoles cannot contain more than "
                            + MAX_TARGET_ROLES + " distinct roles");
        }
        if (aspirations.getLocations() == null || aspirations.getLocations().isEmpty()) {
            throw new IllegalArgumentException("Missing required field: aspirations.locations");
        }
        page(request);
        pageSize(request);
        sort(request);
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

    private int page(JobSearchRequest request) {
        int page = request.getPage() == null ? DEFAULT_PAGE : request.getPage();
        if (page < 1 || page > MAX_PAGE) {
            throw new IllegalArgumentException(
                    "page must be between 1 and " + MAX_PAGE);
        }
        return page;
    }

    private int pageSize(JobSearchRequest request) {
        int pageSize = request.getPageSize() == null
                ? DEFAULT_PAGE_SIZE
                : request.getPageSize();
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "pageSize must be between 1 and " + MAX_PAGE_SIZE);
        }
        return pageSize;
    }

    private String sort(JobSearchRequest request) {
        String value = request.getSort() == null || request.getSort().isBlank()
                ? DEFAULT_SORT
                : request.getSort().trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_SORTS.contains(value)) {
            throw new IllegalArgumentException(
                    "sort must be one of " + String.join(", ", SUPPORTED_SORTS.stream()
                            .sorted()
                            .toList()));
        }
        return value;
    }

    private Comparator<RoleJob> resultOrder(String sort) {
        Comparator<Job> primary = switch (sort) {
            case "CLOSEST" -> Comparator.comparing(
                    Job::getDistanceMiles,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "HIGHEST_SALARY" -> Comparator.comparing(
                    this::annualSalaryMidpoint,
                    Comparator.nullsLast(Comparator.reverseOrder()));
            case "NEWEST_POSTED" -> Comparator.comparing(
                    Job::getPostedAtUtc,
                    Comparator.nullsLast(Comparator.reverseOrder()));
            case "OLDEST_POSTED" -> Comparator.comparing(
                    Job::getPostedAtUtc,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "COMPANY_AZ" -> Comparator.comparing(
                    this::companyName,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "JOB_TITLE_AZ" -> Comparator.comparing(
                    this::jobTitle,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            default -> Comparator
                    .comparing(
                            Job::getMatchScore,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(
                            Job::getPostedAtUtc,
                            Comparator.nullsLast(Comparator.reverseOrder()));
        };
        return Comparator.comparing(RoleJob::job, primary)
                .thenComparing(
                        RoleJob::targetRole,
                        String.CASE_INSENSITIVE_ORDER)
                .thenComparing(
                        row -> stableJobKey(row.job()),
                        String.CASE_INSENSITIVE_ORDER);
    }

    private Double annualSalaryMidpoint(Job job) {
        return job.getSalary() == null
                ? null
                : job.getSalary().getNormalisedAnnualMidpoint();
    }

    private String companyName(Job job) {
        return firstNonBlank(job.getCompanyName(), job.getCompany());
    }

    private String jobTitle(Job job) {
        return firstNonBlank(job.getTitle(), job.getJobTitle());
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return second == null || second.isBlank() ? null : second.trim();
    }

    private String stableJobKey(Job job) {
        return String.join("|",
                safeKey(job.getPrimarySource()),
                safeKey(job.getCanonicalJobId()),
                safeKey(job.getExternalJobId()),
                safeKey(job.getId()),
                safeKey(jobTitle(job)),
                safeKey(companyName(job)),
                safeKey(job.getLocation()));
    }

    private String safeKey(String value) {
        return value == null ? "" : value.trim();
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
        parts.put("employmentTypes", criteria.getEmploymentTypes().stream()
                .filter(value -> value != null)
                .map(value -> value.toUpperCase(Locale.ROOT))
                .sorted()
                .toList());
        parts.put("salaryMin", criteria.getSalaryMin());
        parts.put("salaryMax", criteria.getSalaryMax());
        parts.put("currency", criteria.getCurrency());
        parts.put("providers", selectedProviders(request).stream().sorted().toList());
        return parts.toString();
    }

    private String ownerPageCacheKey(String userId, String cacheKey) {
        return userId + "\u001f" + cacheKey;
    }

    private String mergeMatchingStatus(String current, String next) {
        if ("NOT_RUN".equals(next)) {
            return current;
        }
        if ("COMPLETE".equals(current) || "NOT_RUN".equals(current)) {
            return next;
        }
        if ("TIMED_OUT".equals(current) || "TIMED_OUT".equals(next)) {
            return "TIMED_OUT";
        }
        if ("SATURATED".equals(current) || "SATURATED".equals(next)) {
            return "SATURATED";
        }
        return current;
    }

    private ProviderSearchResult copyProviderSearchResult(
            ProviderSearchResult source) {
        return new ProviderSearchResult(
                copyProviderJobs(source.jobs()),
                source.providerResults().stream()
                        .map(this::copyProviderStatus)
                        .toList(),
                source.anyAttempted(),
                source.anySuccess(),
                source.complete());
    }

    private ProviderResultStatus copyProviderStatus(ProviderResultStatus source) {
        return new ProviderResultStatus(
                source.getProvider(),
                source.getStatus(),
                source.getRawResultCount(),
                source.getErrorMessage());
    }

    private List<Job> copyProviderJobs(List<Job> jobs) {
        return jobs.stream().map(this::copyProviderJob).toList();
    }

    private Job copyProviderJob(Job source) {
        Job target = new Job();
        target.setCanonicalSchemaVersion(
                source.getCanonicalSchemaVersion());
        target.setId(source.getId());
        target.setCanonicalJobId(source.getCanonicalJobId());
        target.setProvider(source.getProvider());
        target.setPrimarySource(source.getPrimarySource());
        target.setExternalJobId(source.getExternalJobId());
        target.setTitle(source.getTitle());
        target.setJobTitle(source.getJobTitle());
        target.setCompany(source.getCompany());
        target.setCompanyName(source.getCompanyName());
        target.setAdvertiserName(source.getAdvertiserName());
        target.setAdvertiserType(source.getAdvertiserType());
        target.setHiringOrganisationName(
                source.getHiringOrganisationName());
        target.setApplicationContactName(
                source.getApplicationContactName());
        target.setLocation(source.getLocation());
        target.setCanonicalLocation(copyLocation(source.getCanonicalLocation()));
        target.setSalary(copySalary(source.getSalary()));
        target.setEmploymentType(source.getEmploymentType());
        target.setEmploymentTypeCode(source.getEmploymentTypeCode());
        target.setContractType(source.getContractType());
        target.setContractTypeCode(source.getContractTypeCode());
        target.setWorkplaceType(source.getWorkplaceType());
        target.setCategory(source.getCategory());
        target.setPostedDate(source.getPostedDate());
        target.setPostedAt(source.getPostedAt());
        target.setExpiresAt(source.getExpiresAt());
        target.setPostedAtUtc(source.getPostedAtUtc());
        target.setExpiresAtUtc(source.getExpiresAtUtc());
        target.setApplicationDeadlineAtUtc(
                source.getApplicationDeadlineAtUtc());
        target.setDistanceMiles(source.getDistanceMiles());
        target.setRemote(source.getRemote());
        target.setDescription(source.getDescription());
        target.setDescriptionCompleteness(
                source.getDescriptionCompleteness());
        target.setUrl(source.getUrl());
        target.setSourceUrl(source.getSourceUrl());
        target.setSources(source.getSources() == null
                ? List.of()
                : source.getSources().stream().map(this::copySource).toList());
        target.setSkills(source.getSkills() == null
                ? List.of()
                : source.getSkills().stream().map(this::copySkill).toList());
        target.setExperience(copyExperience(source.getExperience()));
        target.setFieldProvenance(
                source.getFieldProvenance() == null
                        ? List.of()
                        : source.getFieldProvenance().stream()
                                .map(this::copyFieldProvenance)
                                .toList());
        target.setMatchScore(source.getMatchScore());
        return target;
    }

    private CanonicalLocation copyLocation(CanonicalLocation source) {
        if (source == null) {
            return null;
        }
        CanonicalLocation target = new CanonicalLocation();
        target.setRawDisplayName(source.getRawDisplayName());
        target.setRawCity(source.getRawCity());
        target.setRawRegion(source.getRawRegion());
        target.setRawCountry(source.getRawCountry());
        target.setDisplayName(source.getDisplayName());
        target.setPostcode(source.getPostcode());
        target.setLatitude(source.getLatitude());
        target.setLongitude(source.getLongitude());
        target.setAreaParts(source.getAreaParts() == null
                ? null
                : List.copyOf(source.getAreaParts()));
        target.setCity(source.getCity());
        target.setRegion(source.getRegion());
        target.setCountryCode(source.getCountryCode());
        target.setSourceProvider(source.getSourceProvider());
        target.setNormalisationStatus(source.getNormalisationStatus());
        target.setNormalisationConfidence(
                source.getNormalisationConfidence());
        return target;
    }

    private JobSalary copySalary(JobSalary source) {
        if (source == null) {
            return null;
        }
        JobSalary target = new JobSalary();
        target.setMin(source.getMin());
        target.setMax(source.getMax());
        target.setCurrency(source.getCurrency());
        target.setPeriod(source.getPeriod());
        target.setNormalisedAnnualMinimum(source.getNormalisedAnnualMinimum());
        target.setNormalisedAnnualMaximum(source.getNormalisedAnnualMaximum());
        target.setNormalisedAnnualMidpoint(source.getNormalisedAnnualMidpoint());
        target.setRawMinimum(source.getRawMinimum());
        target.setRawMaximum(source.getRawMaximum());
        target.setRawCurrency(source.getRawCurrency());
        target.setRawPeriod(source.getRawPeriod());
        target.setMinimum(source.getMinimum());
        target.setMaximum(source.getMaximum());
        target.setCurrencyCode(source.getCurrencyCode());
        target.setPeriodCode(source.getPeriodCode());
        target.setPredicted(source.getPredicted());
        target.setSourceProvider(source.getSourceProvider());
        target.setNormalisationStatus(source.getNormalisationStatus());
        target.setNormalisationConfidence(
                source.getNormalisationConfidence());
        target.setNormalisationMethod(source.getNormalisationMethod());
        return target;
    }

    private JobSourceReference copySource(JobSourceReference source) {
        JobSourceReference target = new JobSourceReference();
        target.setProvider(source.getProvider());
        target.setRawPublisher(source.getRawPublisher());
        target.setPublisher(source.getPublisher());
        target.setSourceType(source.getSourceType());
        target.setExternalJobId(source.getExternalJobId());
        target.setListingUrl(source.getListingUrl());
        target.setApplyUrl(source.getApplyUrl());
        target.setDirectApply(source.getDirectApply());
        target.setProviderPostedAt(source.getProviderPostedAt());
        target.setProviderPostedAtRaw(source.getProviderPostedAtRaw());
        target.setProviderPostedAtUtc(source.getProviderPostedAtUtc());
        target.setProviderExpiresAtRaw(source.getProviderExpiresAtRaw());
        target.setProviderExpiresAtUtc(source.getProviderExpiresAtUtc());
        target.setRetrievedAtUtc(source.getRetrievedAtUtc());
        return target;
    }

    private JobSkill copySkill(JobSkill source) {
        JobSkill target = new JobSkill();
        target.setName(source.getName());
        target.setRawName(source.getRawName());
        target.setType(source.getType());
        target.setNormalisationConfidence(
                source.getNormalisationConfidence());
        target.setNormalisationStatus(source.getNormalisationStatus());
        target.setSourceProvider(source.getSourceProvider());
        return target;
    }

    private JobExperience copyExperience(JobExperience source) {
        if (source == null) {
            return new JobExperience();
        }
        JobExperience target = new JobExperience();
        target.setRawValue(source.getRawValue());
        target.setLevel(source.getLevel());
        target.setMinimumYears(source.getMinimumYears());
        target.setMaximumYears(source.getMaximumYears());
        target.setNormalisationConfidence(
                source.getNormalisationConfidence());
        target.setNormalisationStatus(source.getNormalisationStatus());
        target.setSourceProvider(source.getSourceProvider());
        return target;
    }

    private JobFieldProvenance copyFieldProvenance(
            JobFieldProvenance source) {
        JobFieldProvenance target = new JobFieldProvenance();
        target.setFieldName(source.getFieldName());
        target.setSourceProvider(source.getSourceProvider());
        target.setSourceExternalJobId(source.getSourceExternalJobId());
        target.setRawValue(source.getRawValue());
        target.setNormalisedValue(source.getNormalisedValue());
        target.setStatus(source.getStatus());
        target.setConfidence(source.getConfidence());
        target.setRuleVersion(source.getRuleVersion());
        return target;
    }

    private record RoleJob(String targetRole, Job job) {
    }

    private record RoleSearchResult(
            String targetRole,
            List<Job> jobs,
            List<ProviderResultStatus> providerResults,
            String searchStatus,
            String matchingStatus) {
    }

    private record RolePage(
            String targetRole,
            List<Job> jobs,
            int totalResults,
            List<ProviderResultStatus> providerResults,
            String searchStatus,
            String matchingStatus) {
    }

    private record ProviderSearchResult(
            List<Job> jobs,
            List<ProviderResultStatus> providerResults,
            boolean anyAttempted,
            boolean anySuccess,
            boolean complete) {
    }

    private record CacheEntry(Instant createdAt, ProviderSearchResult result) {
        boolean expired(int ttlMinutes) {
            return createdAt.plusSeconds((long) ttlMinutes * 60).isBefore(Instant.now());
        }
    }

    public static class JobNotFoundException extends RuntimeException {
        public JobNotFoundException(String message) {
            super(message);
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
