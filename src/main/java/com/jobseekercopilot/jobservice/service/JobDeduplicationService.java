package com.jobseekercopilot.jobservice.service;

import com.jobseekercopilot.jobservice.model.dto.Job;
import com.jobseekercopilot.jobservice.model.dto.JobSalary;
import com.jobseekercopilot.jobservice.model.dto.JobSourceReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class JobDeduplicationService {
    private static final Pattern NON_WORD = Pattern.compile("[^a-z0-9]+");
    private static final Set<String> TRACKING_PARAMS = Set.of(
            "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
            "gclid", "fbclid", "msclkid", "irclickid");

    private final boolean enabled;
    private final int postedDateToleranceDays;
    private final double titleSimilarityThreshold;
    private final double descriptionSimilarityThreshold;

    public JobDeduplicationService(
            @Value("${job.deduplication.enabled:true}") boolean enabled,
            @Value("${job.deduplication.posted-date-tolerance-days:7}") int postedDateToleranceDays,
            @Value("${job.deduplication.title-similarity-threshold:0.90}") double titleSimilarityThreshold,
            @Value("${job.deduplication.description-similarity-threshold:0.80}") double descriptionSimilarityThreshold) {
        this.enabled = enabled;
        this.postedDateToleranceDays = postedDateToleranceDays;
        this.titleSimilarityThreshold = titleSimilarityThreshold;
        this.descriptionSimilarityThreshold = descriptionSimilarityThreshold;
    }

    public List<Job> deduplicate(List<Job> jobs) {
        if (!enabled || jobs == null || jobs.size() < 2) {
            return jobs == null ? List.of() : jobs;
        }
        List<Job> unique = new ArrayList<>();
        for (Job job : jobs) {
            Job duplicate = unique.stream()
                    .filter(existing -> calculateDuplicateConfidence(existing, job) >= 1.0)
                    .findFirst()
                    .orElse(null);
            if (duplicate == null) {
                unique.add(job);
            } else {
                mergeDuplicateJobs(duplicate, job);
            }
        }
        unique.forEach(job -> {
            job.setCanonicalJobId(createCanonicalJobId(job));
            job.setId(job.getCanonicalJobId());
        });
        return unique;
    }

    public double calculateDuplicateConfidence(Job left, Job right) {
        if (sameCanonicalApplyUrl(left, right) || overlappingApplyOptions(left, right)) {
            return 1.0;
        }
        if (normaliseCompany(left.getCompanyName()).equals(normaliseCompany(right.getCompanyName()))
                && !normaliseCompany(left.getCompanyName()).isBlank()
                && tokenSimilarity(normaliseTitle(left.getTitle()), normaliseTitle(right.getTitle())) >= titleSimilarityThreshold
                && normaliseLocation(left.getLocation()).equals(normaliseLocation(right.getLocation()))
                && postedDatesCompatible(left.getPostedDate(), right.getPostedDate())
                && descriptionsCompatible(left.getDescription(), right.getDescription())) {
            return 1.0;
        }
        return 0.0;
    }

    public Job mergeDuplicateJobs(Job primary, Job duplicate) {
        primary.setTitle(firstNonBlank(primary.getTitle(), duplicate.getTitle()));
        primary.setJobTitle(firstNonBlank(primary.getJobTitle(), duplicate.getJobTitle(), primary.getTitle()));
        primary.setCompanyName(firstNonBlank(primary.getCompanyName(), duplicate.getCompanyName()));
        primary.setCompany(firstNonBlank(primary.getCompany(), duplicate.getCompany(), primary.getCompanyName()));
        primary.setLocation(firstNonBlank(primary.getLocation(), duplicate.getLocation()));
        primary.setCanonicalLocation(preferredLocation(primary.getCanonicalLocation(), duplicate.getCanonicalLocation()));
        primary.setSalary(preferredSalary(primary.getSalary(), duplicate.getSalary()));
        primary.setEmploymentType(firstNonBlank(primary.getEmploymentType(), duplicate.getEmploymentType()));
        primary.setContractType(firstNonBlank(primary.getContractType(), duplicate.getContractType()));
        primary.setCategory(firstNonBlank(primary.getCategory(), duplicate.getCategory()));
        if (primary.getSpecialistType() == null || primary.getSpecialistType() == com.jobseekercopilot.jobservice.model.dto.JobSpecialistType.STANDARD) {
            primary.setSpecialistType(duplicate.getSpecialistType());
        }
        if (primary.getLocations() == null || primary.getLocations().isEmpty()) primary.setLocations(duplicate.getLocations());
        if (primary.getApprenticeshipDetails() == null) primary.setApprenticeshipDetails(duplicate.getApprenticeshipDetails());
        primary.setPostedDate(earliestDate(primary.getPostedDate(), duplicate.getPostedDate()));
        primary.setExpiresAt(firstNonBlank(primary.getExpiresAt(), duplicate.getExpiresAt()));
        primary.setRemote(primary.getRemote() != null ? primary.getRemote() : duplicate.getRemote());
        if (length(duplicate.getDescription()) > length(primary.getDescription())) {
            primary.setDescription(duplicate.getDescription());
        }
        primary.setUrl(preferredApplyUrl(primary, duplicate));
        primary.setSourceUrl(firstNonBlank(primary.getSourceUrl(), duplicate.getSourceUrl()));
        primary.setSources(mergeSources(primary.getSources(), duplicate.getSources()));
        primary.setPrimarySource(primarySource(primary));
        primary.setProvider(primary.getPrimarySource());
        primary.setCanonicalJobId(createCanonicalJobId(primary));
        primary.setId(primary.getCanonicalJobId());
        return primary;
    }

    public String canonicaliseUrl(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            URI uri = URI.create(url.trim());
            UriComponentsBuilder builder = UriComponentsBuilder.newInstance()
                    .scheme(uri.getScheme() == null ? "https" : uri.getScheme().toLowerCase(Locale.ROOT))
                    .host(uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ROOT))
                    .path(uri.getPath() == null ? "" : uri.getPath().replaceAll("/+$", ""));
            if (uri.getRawQuery() != null) {
                UriComponentsBuilder parsed = UriComponentsBuilder.fromUri(uri);
                parsed.build().getQueryParams().forEach((key, values) -> {
                    if (!TRACKING_PARAMS.contains(key.toLowerCase(Locale.ROOT))) {
                        values.forEach(value -> builder.queryParam(key, value));
                    }
                });
            }
            return builder.build(true).toUriString();
        } catch (IllegalArgumentException ex) {
            return url.trim().toLowerCase(Locale.ROOT);
        }
    }

    public String normaliseTitle(String value) {
        return normalise(value);
    }

    public String normaliseCompany(String value) {
        return normalise(value)
                .replaceAll("\\b(ltd|limited|plc|llp|inc|co|company)\\b", "")
                .trim();
    }

    public String normaliseLocation(String value) {
        return normalise(value);
    }

    public String createCanonicalJobId(Job job) {
        String seed = job.getSources() == null || job.getSources().isEmpty()
                ? String.join("|", normaliseTitle(job.getTitle()), normaliseCompany(job.getCompanyName()), normaliseLocation(job.getLocation()))
                : job.getSources().stream()
                        .map(source -> source.getProvider() + ":" + source.getExternalJobId())
                        .sorted()
                        .findFirst()
                        .orElse("");
        return "job_" + sha256(seed);
    }

    private boolean sameCanonicalApplyUrl(Job left, Job right) {
        String leftUrl = canonicaliseUrl(left.getUrl());
        String rightUrl = canonicaliseUrl(right.getUrl());
        return !leftUrl.isBlank() && leftUrl.equals(rightUrl);
    }

    private boolean overlappingApplyOptions(Job left, Job right) {
        Set<String> leftUrls = sourceUrls(left);
        Set<String> rightUrls = sourceUrls(right);
        leftUrls.retainAll(rightUrls);
        return !leftUrls.isEmpty();
    }

    private Set<String> sourceUrls(Job job) {
        Set<String> urls = new LinkedHashSet<>();
        if (job.getUrl() != null) {
            urls.add(canonicaliseUrl(job.getUrl()));
        }
        if (job.getSources() != null) {
            job.getSources().forEach(source -> {
                urls.add(canonicaliseUrl(source.getApplyUrl()));
                urls.add(canonicaliseUrl(source.getListingUrl()));
            });
        }
        urls.remove("");
        return urls;
    }

    private boolean postedDatesCompatible(String left, String right) {
        LocalDate leftDate = parseDate(left);
        LocalDate rightDate = parseDate(right);
        return leftDate == null || rightDate == null
                || Math.abs(ChronoUnit.DAYS.between(leftDate, rightDate)) <= postedDateToleranceDays;
    }

    private boolean descriptionsCompatible(String left, String right) {
        if (isBlank(left) || isBlank(right)) {
            return true;
        }
        return tokenSimilarity(normalise(left), normalise(right)) >= descriptionSimilarityThreshold;
    }

    private double tokenSimilarity(String left, String right) {
        if (left.isBlank() || right.isBlank()) {
            return 0.0;
        }
        Set<String> leftTokens = new LinkedHashSet<>(List.of(left.split(" ")));
        Set<String> rightTokens = new LinkedHashSet<>(List.of(right.split(" ")));
        Set<String> intersection = new LinkedHashSet<>(leftTokens);
        intersection.retainAll(rightTokens);
        Set<String> union = new LinkedHashSet<>(leftTokens);
        union.addAll(rightTokens);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private List<JobSourceReference> mergeSources(List<JobSourceReference> primary, List<JobSourceReference> duplicate) {
        Map<String, JobSourceReference> byKey = new LinkedHashMap<>();
        List<JobSourceReference> combined = new ArrayList<>();
        if (primary != null) {
            combined.addAll(primary);
        }
        if (duplicate != null) {
            combined.addAll(duplicate);
        }
        for (JobSourceReference source : combined) {
            String key = source.getProvider() + "|" + source.getExternalJobId() + "|" + canonicaliseUrl(source.getApplyUrl());
            byKey.putIfAbsent(key, source);
        }
        return new ArrayList<>(byKey.values());
    }

    private com.jobseekercopilot.jobservice.model.dto.CanonicalLocation preferredLocation(
            com.jobseekercopilot.jobservice.model.dto.CanonicalLocation primary,
            com.jobseekercopilot.jobservice.model.dto.CanonicalLocation duplicate) {
        if (hasCoordinates(primary)) {
            return primary;
        }
        if (hasCoordinates(duplicate)) {
            return duplicate;
        }
        return primary == null ? duplicate : primary;
    }

    private boolean hasCoordinates(com.jobseekercopilot.jobservice.model.dto.CanonicalLocation location) {
        return location != null && location.getLatitude() != null && location.getLongitude() != null;
    }

    private String preferredApplyUrl(Job primary, Job duplicate) {
        return primary.getSources().stream()
                .filter(source -> Boolean.TRUE.equals(source.getDirectApply()) && !isBlank(source.getApplyUrl()))
                .map(JobSourceReference::getApplyUrl)
                .findFirst()
                .orElse(firstNonBlank(primary.getUrl(), duplicate.getUrl()));
    }

    private String primarySource(Job job) {
        return job.getSources().stream()
                .filter(source -> Boolean.TRUE.equals(source.getDirectApply()))
                .sorted(Comparator.comparingInt((JobSourceReference source) -> providerPriority(source.getProvider())).reversed())
                .map(JobSourceReference::getProvider)
                .findFirst()
                .orElse(firstNonBlank(job.getPrimarySource(), job.getProvider()));
    }

    private int providerPriority(String provider) {
        if (provider == null) return 0;
        return switch (provider.toUpperCase(Locale.ROOT)) {
            case "NHS_JOBS", "APPRENTICESHIPS" -> 100;
            case "REED", "JSEARCH" -> 20;
            case "ADZUNA" -> 10;
            default -> 0;
        };
    }

    private JobSalary preferredSalary(JobSalary left, JobSalary right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.getMin() != null || left.getMax() != null ? left : right;
    }

    private String earliestDate(String left, String right) {
        LocalDate leftDate = parseDate(left);
        LocalDate rightDate = parseDate(right);
        if (leftDate == null) {
            return right;
        }
        if (rightDate == null) {
            return left;
        }
        return leftDate.isBefore(rightDate) ? left : right;
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.length() >= 10 ? value.substring(0, 10) : value);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String normalise(String value) {
        if (value == null) {
            return "";
        }
        return NON_WORD.matcher(value.toLowerCase(Locale.ROOT)).replaceAll(" ").trim().replaceAll("\\s+", " ");
    }

    private String sha256(String seed) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(seed.getBytes(StandardCharsets.UTF_8))).substring(0, 24);
        } catch (Exception ex) {
            return Integer.toHexString(seed.hashCode());
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private int length(String value) {
        return value == null ? 0 : value.length();
    }
}
