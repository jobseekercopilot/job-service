package com.jobseekercopilot.jobservice.service;

import com.jobseekercopilot.jobservice.model.dto.Aspirations;
import com.jobseekercopilot.jobservice.model.dto.ApprenticeshipDetails;
import com.jobseekercopilot.jobservice.model.dto.CanonicalLocation;
import com.jobseekercopilot.jobservice.model.dto.HomeLocation;
import com.jobseekercopilot.jobservice.model.dto.Job;
import com.jobseekercopilot.jobservice.model.dto.JobDiscoveryAssessment;
import com.jobseekercopilot.jobservice.model.dto.JobExperience;
import com.jobseekercopilot.jobservice.model.dto.JobFieldProvenance;
import com.jobseekercopilot.jobservice.model.dto.JobSalary;
import com.jobseekercopilot.jobservice.model.dto.JobSearchRequest;
import com.jobseekercopilot.jobservice.model.dto.JobSkill;
import com.jobseekercopilot.jobservice.model.dto.JobSourceReference;
import com.jobseekercopilot.jobservice.model.dto.ProviderResultStatus;
import com.jobseekercopilot.jobservice.model.dto.ProviderDataProvenance;
import com.jobseekercopilot.jobservice.model.dto.ReedJobSearchResponse;
import com.jobseekercopilot.jobservice.model.dto.SalaryExpectation;
import com.jobseekercopilot.jobservice.model.dto.SearchFreshness;
import com.jobseekercopilot.jobservice.model.dto.SearchQualitySummary;
import com.jobseekercopilot.jobservice.model.dto.WorkPreferences;
import com.jobseekercopilot.jobservice.config.JobSearchResilienceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.Clock;
import java.time.ZoneOffset;
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
    private final JobDiscoveryClassifier discoveryClassifier;
    private final Clock clock;
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
        this(providerSearchCoordinator, deduplicationService,
                jobResultEnrichmentService, distanceCalculationService,
                jobMatchingEnricher, resilience, cacheTtlMinutes,
                new JobDiscoveryClassifier(), Clock.systemUTC());
    }

    @Autowired
    public JobSearchService(ProviderSearchCoordinator providerSearchCoordinator,
                            JobDeduplicationService deduplicationService,
                            JobResultEnrichmentService jobResultEnrichmentService,
                            DistanceCalculationService distanceCalculationService,
                            OptionalJobMatchingEnricher jobMatchingEnricher,
                            JobSearchResilienceProperties resilience,
                            @Value("${job.search.cache-ttl-minutes:${JOB_SEARCH_CACHE_TTL_MINUTES:10}}") int cacheTtlMinutes,
                            JobDiscoveryClassifier discoveryClassifier,
                            Clock clock) {
        this.providerSearchCoordinator = providerSearchCoordinator;
        this.deduplicationService = deduplicationService;
        this.jobResultEnrichmentService = jobResultEnrichmentService;
        this.distanceCalculationService = distanceCalculationService;
        this.jobMatchingEnricher = jobMatchingEnricher;
        this.resilience = resilience;
        this.cacheTtlMinutes = cacheTtlMinutes;
        this.discoveryClassifier = discoveryClassifier;
        this.clock = clock;
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
                        "NOT_RUN",
                        emptyQualitySummary()));
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
                        "NOT_RUN",
                        providerSearchResult.qualitySummary()));
                continue;
            }

            applyDistance(request, providerSearchResult.jobs());
            OptionalJobMatchingEnricher.MatchingOutcome matchingOutcome;
            if (request.getCandidateProfile() != null) {
                matchingOutcome = jobMatchingEnricher.enrich(
                            userId,
                            providerSearchResult.jobs(),
                            requestDeadlineNanos,
                            request.getHomeLocation(),
                            request.getWorkPreferences(),
                            targetRole,
                            request.getCandidateProfile());
            } else if (hasCommuteModes(request)) {
                matchingOutcome = jobMatchingEnricher.enrich(
                        userId,
                        providerSearchResult.jobs(),
                        requestDeadlineNanos,
                        request.getHomeLocation(),
                        request.getWorkPreferences());
            } else {
                matchingOutcome = jobMatchingEnricher.enrich(
                        userId,
                        providerSearchResult.jobs(),
                        requestDeadlineNanos);
            }
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
                    matchingOutcome.status(),
                    providerSearchResult.qualitySummary()));
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
                        .map(rolePage -> {
                            ReedJobSearchResponse.TargetRoleJobResults roleResponse =
                                new ReedJobSearchResponse.TargetRoleJobResults(
                                        rolePage.targetRole(),
                                        rolePage.jobs(),
                                        rolePage.totalResults(),
                                        page,
                                        pageSize,
                                        rolePage.providerResults(),
                                        rolePage.searchStatus(),
                                        rolePage.matchingStatus());
                            roleResponse.setQualitySummary(rolePage.qualitySummary());
                            return roleResponse;
                        })
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
        response.setFreshness(searchFreshness(providerResults));
        response.setQualitySummary(combineQualitySummaries(roleResults));
        return response;
    }

    private boolean hasCommuteModes(JobSearchRequest request) {
        return request.getWorkPreferences() != null
                && request.getWorkPreferences().getCommuteTravelModes() != null
                && !request.getWorkPreferences().getCommuteTravelModes().isEmpty();
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
        Job detail = providerSearchCoordinator.details(
                        userId,
                        provider,
                        externalJobId)
                .orElseThrow(() -> new JobNotFoundException(
                        "Job details are not available"));
        String title = firstNonBlank(detail.getTitle(), detail.getJobTitle());
        JobDiscoveryClassifier.ClassificationResult assessed =
                discoveryClassifier.classifyAndFilter(title, List.of(detail));
        if (assessed.jobs().isEmpty()
                && detail.getDiscoveryAssessment() != null
                && detail.getDiscoveryAssessment().getExclusionReasons()
                        .contains("KNOWN_EXPIRED_OR_CLOSED")) {
            throw new JobNotFoundException("Job is no longer available");
        }
        return detail;
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
                result.matchingStatus(),
                result.qualitySummary());
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
            if (partialPage != null && !partialPage.expired(cacheTtlMinutes, clock)) {
                log.info("Job provider partial-page cache hit jobs={}",
                        partialPage.result().jobs().size());
                return asCachedResult(partialPage);
            }
            if (partialPage != null) {
                partialPageCache.remove(ownerPageCacheKey, partialPage);
            }
        }
        CacheEntry cached = searchCache.get(cacheKey);
        if (cached != null && !cached.expired(cacheTtlMinutes, clock)) {
            log.info("Job provider search cache hit jobs={}",
                    cached.result().jobs().size());
            return asCachedResult(cached);
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
                            clock.instant(),
                            copyProviderSearchResult(fresh)));
            partialPageCache.remove(ownerPageCacheKey);
        } else if (fresh.anySuccess()) {
            partialPageCache.put(
                    ownerPageCacheKey,
                    new CacheEntry(
                            clock.instant(),
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
        OffsetDateTime retrievedAt = OffsetDateTime.now(clock);
        enrichedProviderJobs.forEach(job -> {
            // Provider ranking is not claimant-specific and must never be
            // presented as personal relevance.
            job.setMatchScore(null);
            job.setMatchAssessment(null);
            if (job.getSources() != null) {
                job.getSources().stream()
                        .filter(java.util.Objects::nonNull)
                        .filter(source -> source.getRetrievedAtUtc() == null)
                        .forEach(source -> source.setRetrievedAtUtc(retrievedAt));
            }
        });
        JobDiscoveryClassifier.ClassificationResult classified =
                discoveryClassifier.classifyAndFilter(
                        criteria.getTargetRole(), enrichedProviderJobs);
        log.info("Job normalisation complete count={} durationMs={}",
                classified.jobs().size(),
                (System.nanoTime() - normalisationStartedAt) / 1_000_000);
        log.info("Provider search complete rawCount={} uniqueCount={} durationMs={}",
                rawJobs.size(),
                classified.jobs().size(),
                (System.nanoTime() - startedAt) / 1_000_000);
        return new ProviderSearchResult(
                classified.jobs(),
                fanOut.providerResults(),
                fanOut.anyAttempted(),
                fanOut.anySuccess(),
                fanOut.complete(),
                classified.summary());
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
        validateCandidateProfile(request.getCandidateProfile());
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

    private void validateCandidateProfile(
            com.jobseekercopilot.jobservice.model.dto.CandidateProfile profile) {
        if (profile == null) return;
        if (profile.getSkills().size() > 100
                || profile.getRoles().size() > 50
                || profile.getQualifications().size() > 50) {
            throw new IllegalArgumentException(
                    "candidateProfile exceeds supported evidence bounds");
        }
        profile.getSkills().forEach(skill -> {
            if (skill == null || skill.isBlank() || skill.length() > 100) {
                throw new IllegalArgumentException(
                        "candidateProfile.skills contains an invalid value");
            }
        });
        profile.getRoles().forEach(role -> {
            if (role == null || role.getJobTitle() == null
                    || role.getJobTitle().isBlank()
                    || role.getJobTitle().length() > 200
                    || !("CURRENT".equals(role.getStatus())
                            || "PREVIOUS_ROLE".equals(role.getStatus()))
                    || !validProfileDate(role.getStartDate())
                    || (role.getEndDate() != null
                            && !validProfileDate(role.getEndDate()))) {
                throw new IllegalArgumentException(
                        "candidateProfile.roles contains an invalid value");
            }
        });
        profile.getQualifications().forEach(qualification -> {
            if (qualification == null
                    || qualification.getQualificationName() == null
                    || qualification.getQualificationName().isBlank()
                    || qualification.getQualificationName().length() > 200
                    || !("IN_PROGRESS".equals(qualification.getStatus())
                            || "COMPLETED".equals(qualification.getStatus()))
                    || (qualification.getDateAchieved() != null
                            && !validProfileDate(qualification.getDateAchieved()))
                    || (qualification.getExpectedCompletion() != null
                            && !validProfileDate(qualification.getExpectedCompletion()))) {
                throw new IllegalArgumentException(
                        "candidateProfile.qualifications contains an invalid value");
            }
        });
    }

    private boolean validProfileDate(String value) {
        return value != null
                && value.matches("\\d{4}-\\d{2}(?:-\\d{2})?");
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

    private ProviderSearchResult asCachedResult(CacheEntry entry) {
        ProviderSearchResult cached = copyProviderSearchResult(entry.result());
        OffsetDateTime servedAt = OffsetDateTime.now(clock);
        long cacheAgeSeconds = Math.max(
                0,
                Duration.between(entry.createdAt(), clock.instant()).toSeconds());
        cached.providerResults().forEach(status -> {
            ProviderDataProvenance provenance = status.getDataProvenance();
            if (provenance == null) {
                provenance = new ProviderDataProvenance();
                provenance.setProviderMode("UNKNOWN");
                provenance.setDataOrigin("UNKNOWN");
                provenance.setRetrievedAtUtc(
                        OffsetDateTime.ofInstant(entry.createdAt(), ZoneOffset.UTC));
                status.setDataProvenance(provenance);
            }
            provenance.setResultSource("JOB_SERVICE_CACHE");
            provenance.setServedAtUtc(servedAt);
            provenance.setCacheAgeSeconds(cacheAgeSeconds);
        });
        return cached;
    }

    private SearchFreshness searchFreshness(
            List<ProviderResultStatus> statuses) {
        List<ProviderDataProvenance> provenances = statuses == null
                ? List.of()
                : statuses.stream()
                        .map(ProviderResultStatus::getDataProvenance)
                        .filter(java.util.Objects::nonNull)
                        .toList();
        SearchFreshness freshness = new SearchFreshness();
        freshness.setServedAtUtc(OffsetDateTime.now(clock));
        Set<String> sources = provenances.stream()
                .map(ProviderDataProvenance::getResultSource)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        freshness.setResultSource(sources.isEmpty()
                ? "UNKNOWN"
                : sources.size() == 1 ? sources.iterator().next() : "MIXED");
        freshness.setOldestRetrievedAtUtc(provenances.stream()
                .map(ProviderDataProvenance::getRetrievedAtUtc)
                .filter(java.util.Objects::nonNull)
                .min(OffsetDateTime::compareTo)
                .orElse(null));
        freshness.setMaximumCacheAgeSeconds(provenances.stream()
                .map(ProviderDataProvenance::getCacheAgeSeconds)
                .filter(java.util.Objects::nonNull)
                .max(Long::compareTo)
                .orElse(null));
        return freshness;
    }

    private SearchQualitySummary combineQualitySummaries(
            List<RoleSearchResult> results) {
        SearchQualitySummary combined = emptyQualitySummary();
        results.stream()
                .map(RoleSearchResult::qualitySummary)
                .filter(java.util.Objects::nonNull)
                .forEach(summary -> {
                    combined.setAssessedJobCount(combined.getAssessedJobCount()
                            + summary.getAssessedJobCount());
                    combined.setEligibleJobCount(combined.getEligibleJobCount()
                            + summary.getEligibleJobCount());
                    combined.setExcludedExpiredCount(combined.getExcludedExpiredCount()
                            + summary.getExcludedExpiredCount());
                    combined.setExcludedPaidTrainingCount(combined.getExcludedPaidTrainingCount()
                            + summary.getExcludedPaidTrainingCount());
                    combined.setExcludedOccupationMismatchCount(combined.getExcludedOccupationMismatchCount()
                            + summary.getExcludedOccupationMismatchCount());
                });
        return combined;
    }

    private SearchQualitySummary emptyQualitySummary() {
        return new SearchQualitySummary();
    }

    private SearchQualitySummary copyQualitySummary(
            SearchQualitySummary source) {
        if (source == null) return emptyQualitySummary();
        SearchQualitySummary target = new SearchQualitySummary();
        target.setAssessedJobCount(source.getAssessedJobCount());
        target.setEligibleJobCount(source.getEligibleJobCount());
        target.setExcludedExpiredCount(source.getExcludedExpiredCount());
        target.setExcludedPaidTrainingCount(source.getExcludedPaidTrainingCount());
        target.setExcludedOccupationMismatchCount(source.getExcludedOccupationMismatchCount());
        return target;
    }

    private ProviderDataProvenance copyDataProvenance(
            ProviderDataProvenance source) {
        if (source == null) return null;
        ProviderDataProvenance target = new ProviderDataProvenance();
        target.setProviderMode(source.getProviderMode());
        target.setDataOrigin(source.getDataOrigin());
        target.setResultSource(source.getResultSource());
        target.setDatasetId(source.getDatasetId());
        target.setDatasetVersion(source.getDatasetVersion());
        target.setScenario(source.getScenario());
        target.setExternalCallsEnabled(source.getExternalCallsEnabled());
        target.setRetrievedAtUtc(source.getRetrievedAtUtc());
        target.setServedAtUtc(source.getServedAtUtc());
        target.setCacheAgeSeconds(source.getCacheAgeSeconds());
        return target;
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
                source.complete(),
                copyQualitySummary(source.qualitySummary()));
    }

    private ProviderResultStatus copyProviderStatus(ProviderResultStatus source) {
        ProviderResultStatus target = new ProviderResultStatus(
                source.getProvider(),
                source.getStatus(),
                source.getRawResultCount(),
                source.getErrorMessage());
        target.setDataProvenance(copyDataProvenance(source.getDataProvenance()));
        return target;
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
        target.setLocations(source.getLocations() == null ? List.of() : source.getLocations().stream().map(this::copyLocation).toList());
        target.setSalary(copySalary(source.getSalary()));
        target.setEmploymentType(source.getEmploymentType());
        target.setEmploymentTypeCode(source.getEmploymentTypeCode());
        target.setContractType(source.getContractType());
        target.setContractTypeCode(source.getContractTypeCode());
        target.setWorkplaceType(source.getWorkplaceType());
        target.setCategory(source.getCategory());
        target.setSpecialistType(source.getSpecialistType());
        target.setApprenticeshipDetails(copyApprenticeshipDetails(source.getApprenticeshipDetails()));
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
        target.setMatchAssessment(source.getMatchAssessment());
        target.setDiscoveryAssessment(
                copyDiscoveryAssessment(source.getDiscoveryAssessment()));
        return target;
    }

    private JobDiscoveryAssessment copyDiscoveryAssessment(
            JobDiscoveryAssessment source) {
        if (source == null) return null;
        JobDiscoveryAssessment target = new JobDiscoveryAssessment();
        target.setAlgorithmVersion(source.getAlgorithmVersion());
        target.setAvailability(source.getAvailability());
        target.setEngagementType(source.getEngagementType());
        target.setOccupationFamily(source.getOccupationFamily());
        target.setSeniority(source.getSeniority());
        target.setTargetRoleAlignment(source.getTargetRoleAlignment());
        target.setTargetRole(source.getTargetRole());
        target.setExcluded(source.isExcluded());
        target.setExclusionReasons(source.getExclusionReasons() == null
                ? List.of() : List.copyOf(source.getExclusionReasons()));
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

    private ApprenticeshipDetails copyApprenticeshipDetails(ApprenticeshipDetails source) {
        if (source == null) return null;
        ApprenticeshipDetails target = new ApprenticeshipDetails();
        target.setCourseTitle(source.getCourseTitle()); target.setCourseLevel(source.getCourseLevel()); target.setCourseLarsCode(source.getCourseLarsCode());
        target.setCourseRoute(source.getCourseRoute()); target.setApprenticeshipLevel(source.getApprenticeshipLevel()); target.setTrainingProvider(source.getTrainingProvider());
        target.setStartDate(source.getStartDate()); target.setDuration(source.getDuration()); target.setHoursPerWeek(source.getHoursPerWeek()); target.setNumberOfPositions(source.getNumberOfPositions());
        target.setWageType(source.getWageType()); target.setWageAdditionalInformation(source.getWageAdditionalInformation()); target.setWorkingWeekDescription(source.getWorkingWeekDescription());
        target.setNationalVacancy(source.getNationalVacancy()); target.setNationalVacancyDetails(source.getNationalVacancyDetails());
        target.setQualifications(source.getQualifications() == null ? List.of() : List.copyOf(source.getQualifications()));
        target.setThingsToConsider(source.getThingsToConsider()); target.setCompanyBenefitsInformation(source.getCompanyBenefitsInformation());
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
            String matchingStatus,
            SearchQualitySummary qualitySummary) {
    }

    private record RolePage(
            String targetRole,
            List<Job> jobs,
            int totalResults,
            List<ProviderResultStatus> providerResults,
            String searchStatus,
            String matchingStatus,
            SearchQualitySummary qualitySummary) {
    }

    private record ProviderSearchResult(
            List<Job> jobs,
            List<ProviderResultStatus> providerResults,
            boolean anyAttempted,
            boolean anySuccess,
            boolean complete,
            SearchQualitySummary qualitySummary) {
    }

    private record CacheEntry(Instant createdAt, ProviderSearchResult result) {
        boolean expired(int ttlMinutes, Clock clock) {
            return createdAt.plusSeconds((long) ttlMinutes * 60).isBefore(clock.instant());
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
