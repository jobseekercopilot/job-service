package com.jobseekercopilot.jobservice.service;

import com.jobseekercopilot.jobservice.model.dto.CanonicalValueStatus;
import com.jobseekercopilot.jobservice.model.dto.ExperienceLevelCode;
import com.jobseekercopilot.jobservice.model.dto.Job;
import com.jobseekercopilot.jobservice.model.dto.JobDiscoveryAssessment;
import com.jobseekercopilot.jobservice.model.dto.JobExperience;
import com.jobseekercopilot.jobservice.model.dto.JobSourceReference;
import com.jobseekercopilot.jobservice.model.dto.SearchQualitySummary;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Conservative deterministic rules for vacancy hygiene. */
@Component
public class JobDiscoveryClassifier {
    static final String ALGORITHM_VERSION = "DISCOVERY_RULES_V1";
    private static final Pattern MINIMUM_EXPERIENCE = Pattern.compile(
            "(?i)(?:at least|minimum(?: of)?|min\\.?\\s*)?\\b(\\d{1,2})\\s*\\+?\\s*(?:years?|yrs?)\\b[^.\\n]{0,45}\\b(?:experience|commercial|professional|development|engineering)\\b");
    private static final Set<String> CLOSED_PHRASES = Set.of(
            "no longer accepting applications",
            "applications are now closed",
            "this vacancy has closed",
            "position has been filled");
    private static final Set<String> PAID_TRAINING_PHRASES = Set.of(
            "training fee", "course fee", "tuition fee", "finance available",
            "pay for your training", "self funded training", "job guarantee after training");
    private static final Set<String> PAID_TRAINING_TITLE_PHRASES = Set.of(
            "career programme", "career program", "placement programme",
            "placement program", "training academy", "bootcamp");
    private static final Set<String> SENIOR_TERMS = Set.of("senior", "sr");
    private static final Set<String> LEAD_TERMS = Set.of(
            "lead", "principal", "staff", "head", "director", "manager", "architect");
    private static final Set<String> JUNIOR_TERMS = Set.of(
            "junior", "jr", "graduate", "entry", "trainee", "intern", "apprentice");

    private final Clock clock;

    public JobDiscoveryClassifier() {
        this(Clock.systemUTC());
    }

    JobDiscoveryClassifier(Clock clock) {
        this.clock = clock;
    }

    ClassificationResult classifyAndFilter(String targetRole, List<Job> jobs) {
        List<Job> safeJobs = jobs == null ? List.of() : jobs;
        List<Job> eligible = new ArrayList<>();
        SearchQualitySummary summary = new SearchQualitySummary();
        summary.setAssessedJobCount(safeJobs.size());
        for (Job job : safeJobs) {
            JobDiscoveryAssessment assessment = assess(targetRole, job);
            job.setDiscoveryAssessment(assessment);
            enrichExperience(job, assessment);
            if (!assessment.isExcluded()) {
                eligible.add(job);
                continue;
            }
            if (assessment.getExclusionReasons().contains("KNOWN_EXPIRED_OR_CLOSED")) {
                summary.setExcludedExpiredCount(summary.getExcludedExpiredCount() + 1);
            }
            if (assessment.getExclusionReasons().contains("PAID_TRAINING_SCHEME")) {
                summary.setExcludedPaidTrainingCount(summary.getExcludedPaidTrainingCount() + 1);
            }
            if (assessment.getExclusionReasons().contains("OCCUPATION_MISMATCH")) {
                summary.setExcludedOccupationMismatchCount(summary.getExcludedOccupationMismatchCount() + 1);
            }
        }
        summary.setEligibleJobCount(eligible.size());
        return new ClassificationResult(List.copyOf(eligible), summary);
    }

    JobDiscoveryAssessment assess(String targetRole, Job job) {
        String title = firstNonBlank(job == null ? null : job.getTitle(), job == null ? null : job.getJobTitle());
        String description = job == null ? null : job.getDescription();
        String normalizedTitle = normalize(title);
        String normalizedDescription = normalize(description);
        String targetFamily = occupationFamily(normalize(targetRole));
        String jobFamily = occupationFamily(normalizedTitle);
        String alignment = alignment(targetFamily, jobFamily, normalize(targetRole), normalizedTitle);

        JobDiscoveryAssessment assessment = new JobDiscoveryAssessment();
        assessment.setAlgorithmVersion(ALGORITHM_VERSION);
        assessment.setAvailability(availability(job, normalizedDescription));
        assessment.setEngagementType(engagementType(job, normalizedTitle, normalizedDescription));
        assessment.setOccupationFamily(jobFamily);
        assessment.setSeniority(seniority(normalizedTitle));
        assessment.setTargetRoleAlignment(alignment);
        assessment.setTargetRole(targetRole);

        List<String> exclusions = new ArrayList<>();
        if ("EXPIRED".equals(assessment.getAvailability())
                || "CLOSED".equals(assessment.getAvailability())) {
            exclusions.add("KNOWN_EXPIRED_OR_CLOSED");
        }
        if ("PAID_TRAINING".equals(assessment.getEngagementType())) {
            exclusions.add("PAID_TRAINING_SCHEME");
        }
        if ("MISMATCHED".equals(alignment)) {
            exclusions.add("OCCUPATION_MISMATCH");
        }
        assessment.setExclusionReasons(exclusions);
        assessment.setExcluded(!exclusions.isEmpty());
        return assessment;
    }

    private String availability(Job job, String description) {
        if (containsAny(description, CLOSED_PHRASES)) {
            return "CLOSED";
        }
        OffsetDateTime deadline = earliestKnownDeadline(job);
        if (deadline != null && deadline.isBefore(OffsetDateTime.now(clock))) {
            return "EXPIRED";
        }
        return deadline == null ? "UNKNOWN" : "OPEN_AT_RETRIEVAL";
    }

    private OffsetDateTime earliestKnownDeadline(Job job) {
        if (job == null) {
            return null;
        }
        List<OffsetDateTime> values = new ArrayList<>();
        add(values, job.getApplicationDeadlineAtUtc());
        add(values, job.getExpiresAtUtc());
        if (job.getSources() != null) {
            for (JobSourceReference source : job.getSources()) {
                if (source != null) {
                    add(values, source.getProviderExpiresAtUtc());
                }
            }
        }
        return values.stream().min(OffsetDateTime::compareTo).orElse(null);
    }

    private void add(List<OffsetDateTime> values, OffsetDateTime value) {
        if (value != null) {
            values.add(value);
        }
    }

    private String engagementType(Job job, String title, String description) {
        if ((job != null && "APPRENTICESHIP".equals(String.valueOf(job.getSpecialistType())))
                || title.contains("apprentice")) {
            return "APPRENTICESHIP";
        }
        boolean paymentCue = containsAny(description, PAID_TRAINING_PHRASES);
        boolean schemeTitle = containsAny(title, PAID_TRAINING_TITLE_PHRASES);
        boolean trainingProviderCue = description.contains("training provider")
                || description.contains("course provider")
                || description.contains("guaranteed job placement");
        if (paymentCue || (schemeTitle && trainingProviderCue)) {
            return "PAID_TRAINING";
        }
        return "VACANCY";
    }

    static String occupationFamily(String text) {
        if (text == null || text.isBlank()) return "UNKNOWN";
        if (containsAny(text, Set.of("software", "developer", "programmer", "frontend", "front end", "backend", "back end", "full stack", "fullstack", "devops", "platform engineer", "site reliability", "web engineer", "java engineer", "cloud engineer", "mobile developer"))) return "SOFTWARE";
        if (containsAny(text, Set.of("data scientist", "data analyst", "data engineer", "machine learning", "analytics"))) return "DATA";
        if (containsAny(text, Set.of("quality assurance", "qa engineer", "test engineer", "software tester", "automation tester"))) return "QUALITY_ENGINEERING";
        if (containsAny(text, Set.of("cyber", "security engineer", "penetration", "soc analyst"))) return "CYBER_SECURITY";
        if (containsAny(text, Set.of("ux", "user experience", "ui designer", "product designer"))) return "DESIGN";
        if (containsAny(text, Set.of("project manager", "project coordinator", "product manager", "product owner", "scrum master"))) return "PRODUCT_PROJECT";
        if (containsAny(text, Set.of("helpdesk", "service desk", "technical support", "it support"))) return "IT_SUPPORT";
        if (containsAny(text, Set.of("sales", "retail", "warehouse", "care assistant", "nurse", "teacher", "chef", "driver"))) return "NON_TECHNICAL";
        return "UNKNOWN";
    }

    private String alignment(String targetFamily, String jobFamily, String target, String title) {
        if (!"UNKNOWN".equals(targetFamily) && targetFamily.equals(jobFamily)) {
            return "ALIGNED";
        }
        if (Set.of("SOFTWARE", "DATA", "QUALITY_ENGINEERING", "CYBER_SECURITY").contains(targetFamily)
                && Set.of("SOFTWARE", "DATA", "QUALITY_ENGINEERING", "CYBER_SECURITY").contains(jobFamily)) {
            return "RELATED";
        }
        Set<String> targetTokens = coreTokens(target);
        Set<String> titleTokens = coreTokens(title);
        if (!targetTokens.isEmpty() && titleTokens.stream().anyMatch(targetTokens::contains)) {
            return "RELATED";
        }
        if (!"UNKNOWN".equals(targetFamily) && !"UNKNOWN".equals(jobFamily)) {
            return "MISMATCHED";
        }
        return "UNKNOWN";
    }

    private String seniority(String title) {
        Set<String> tokens = coreTokens(title);
        if (tokens.stream().anyMatch(LEAD_TERMS::contains)) return "LEADERSHIP";
        if (tokens.stream().anyMatch(SENIOR_TERMS::contains)) return "SENIOR";
        if (tokens.stream().anyMatch(JUNIOR_TERMS::contains)) return "JUNIOR_ENTRY";
        if (tokens.contains("mid") || title.contains("mid level")) return "MID";
        return "UNSPECIFIED";
    }

    private void enrichExperience(Job job, JobDiscoveryAssessment assessment) {
        if (job == null) return;
        JobExperience experience = job.getExperience() == null ? new JobExperience() : job.getExperience();
        if (experience.getMinimumYears() == null) {
            Matcher matcher = MINIMUM_EXPERIENCE.matcher(job.getDescription() == null ? "" : job.getDescription());
            if (matcher.find()) {
                experience.setRawValue(matcher.group());
                experience.setMinimumYears(new BigDecimal(matcher.group(1)));
                experience.setNormalisationStatus(CanonicalValueStatus.NORMALISED);
                experience.setNormalisationConfidence(new BigDecimal("0.90"));
                experience.setSourceProvider(job.getPrimarySource());
            }
        }
        if (experience.getLevel() == null || experience.getLevel() == ExperienceLevelCode.UNKNOWN) {
            ExperienceLevelCode level = switch (assessment.getSeniority()) {
                case "JUNIOR_ENTRY" -> ExperienceLevelCode.ENTRY;
                case "MID" -> ExperienceLevelCode.MID;
                case "SENIOR" -> ExperienceLevelCode.SENIOR;
                case "LEADERSHIP" -> ExperienceLevelCode.LEAD;
                default -> ExperienceLevelCode.UNKNOWN;
            };
            experience.setLevel(level);
        }
        job.setExperience(experience);
    }

    private static Set<String> coreTokens(String text) {
        if (text == null || text.isBlank()) return Set.of();
        Set<String> stop = Set.of("the", "a", "an", "and", "of", "for", "to", "role", "job");
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        Arrays.stream(text.split(" "))
                .filter(token -> token.length() > 1 && !stop.contains(token))
                .forEach(tokens::add);
        return tokens;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9+#.]+", " ")
                .trim().replaceAll("\\s+", " ");
    }

    private static boolean containsAny(String value, Set<String> phrases) {
        return value != null && phrases.stream().anyMatch(value::contains);
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second == null ? "" : second;
    }

    record ClassificationResult(List<Job> jobs, SearchQualitySummary summary) {}
}
