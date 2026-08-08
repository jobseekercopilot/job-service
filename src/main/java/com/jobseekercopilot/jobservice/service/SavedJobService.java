package com.jobseekercopilot.jobservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobseekercopilot.jobservice.entity.SavedJob;
import com.jobseekercopilot.jobservice.entity.SavedJobSnapshot;
import com.jobseekercopilot.jobservice.model.dto.Job;
import com.jobseekercopilot.jobservice.model.dto.JobSourceReference;
import com.jobseekercopilot.jobservice.model.dto.SavedJobPageResponse;
import com.jobseekercopilot.jobservice.model.dto.SavedJobResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SavedJobService {

    private static final int MAX_PERSISTENCE_ATTEMPTS = 3;
    private static final int MAX_OWNER_LENGTH = 128;
    private static final int MAX_ID_LENGTH = 128;
    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_COMPANY_LENGTH = 200;
    private static final int MAX_DESCRIPTION_LENGTH = 12_000;
    private static final int MAX_SNAPSHOT_BYTES = 256_000;
    private static final int MAX_SOURCES = 20;
    private static final int MAX_SKILLS = 100;
    private static final int MAX_FIELD_PROVENANCE = 200;

    private final SavedJobTransaction transaction;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public SavedJobService(
            SavedJobTransaction transaction,
            ObjectMapper objectMapper,
            Clock clock) {
        this.transaction = transaction;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public SavedJobSaveResult save(String userId, Job candidate) {
        String ownerId = requireOwner(userId);
        NormalizedSnapshot normalized = normalize(candidate);
        Instant now = clock.instant();
        UUID savedJobId = stableId("saved-job", ownerId, normalized.canonicalJobId());
        UUID snapshotId = stableId(
                "saved-job-snapshot",
                savedJobId.toString(),
                normalized.contentSha256());

        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_PERSISTENCE_ATTEMPTS; attempt++) {
            try {
                SavedJobTransaction.PersistenceResult result = transaction.save(
                        savedJobId,
                        snapshotId,
                        ownerId,
                        normalized.canonicalJobId(),
                        normalized.canonicalSchemaVersion(),
                        normalized.contentSha256(),
                        normalized.snapshotJson(),
                        now,
                        normalized.sourceRetrievedAt());
                return new SavedJobSaveResult(
                        result.outcome(),
                        response(result.savedJob(), result.snapshot(), now));
            } catch (DataIntegrityViolationException
                    | ConcurrencyFailureException failure) {
                lastFailure = failure;
            }
        }
        throw new IllegalStateException(
                "Saved job could not be committed after concurrent updates.",
                lastFailure);
    }

    public SavedJobResponse get(String userId, UUID savedJobId) {
        String ownerId = requireOwner(userId);
        SavedJobTransaction.PersistenceResult result =
                transaction.findActive(ownerId, savedJobId);
        return response(
                result.savedJob(),
                result.snapshot(),
                clock.instant());
    }

    public SavedJobPageResponse list(
            String userId,
            int page,
            int size) {
        String ownerId = requireOwner(userId);
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "page must be non-negative and size must be between 1 and 100.");
        }
        Page<SavedJobTransaction.PersistenceResult> results =
                transaction.findActive(ownerId, page, size);
        Instant now = clock.instant();
        return new SavedJobPageResponse(
                results.stream()
                        .map(result -> response(
                                result.savedJob(),
                                result.snapshot(),
                                now))
                        .toList(),
                page,
                size,
                results.getTotalElements(),
                results.getTotalPages());
    }

    public void unsave(String userId, UUID savedJobId) {
        transaction.unsave(
                requireOwner(userId),
                savedJobId,
                clock.instant());
    }

    private NormalizedSnapshot normalize(Job candidate) {
        if (candidate == null) {
            throw new IllegalArgumentException("Job snapshot is required.");
        }
        String canonicalJobId = firstText(
                candidate.getCanonicalJobId(),
                candidate.getId());
        canonicalJobId = requireBounded(
                canonicalJobId,
                "canonicalJobId",
                MAX_ID_LENGTH);
        String schemaVersion = requireBounded(
                candidate.getCanonicalSchemaVersion(),
                "canonicalSchemaVersion",
                32);
        if (!Job.CURRENT_CANONICAL_SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException(
                    "Unsupported canonical job schema version.");
        }
        String title = requireBounded(
                firstText(candidate.getTitle(), candidate.getJobTitle()),
                "title",
                MAX_TITLE_LENGTH);
        String company = requireBounded(
                firstText(candidate.getCompanyName(), candidate.getCompany()),
                "companyName",
                MAX_COMPANY_LENGTH);
        String advertiserName = optionalBounded(
                firstText(candidate.getAdvertiserName(), company),
                "advertiserName",
                MAX_COMPANY_LENGTH);
        String hiringOrganisationName = optionalBounded(
                candidate.getHiringOrganisationName(),
                "hiringOrganisationName",
                MAX_COMPANY_LENGTH);
        String applicationContactName = optionalBounded(
                candidate.getApplicationContactName(),
                "applicationContactName",
                MAX_COMPANY_LENGTH);
        requireBounded(
                candidate.getDescription(),
                "description",
                MAX_DESCRIPTION_LENGTH);
        if (candidate.getSources().size() > MAX_SOURCES
                || candidate.getSkills().size() > MAX_SKILLS
                || candidate.getFieldProvenance().size()
                        > MAX_FIELD_PROVENANCE) {
            throw new IllegalArgumentException(
                    "Canonical job provenance exceeds supported bounds.");
        }

        ObjectNode snapshot = objectMapper.valueToTree(candidate);
        snapshot.put("canonicalSchemaVersion", schemaVersion);
        snapshot.put("canonicalJobId", canonicalJobId);
        snapshot.put("id", canonicalJobId);
        snapshot.put("title", title);
        snapshot.put("jobTitle", title);
        snapshot.put("companyName", company);
        snapshot.put("company", company);
        snapshot.put("advertiserName", advertiserName);
        snapshot.put("advertiserType", candidate.getAdvertiserType().name());
        if (hiringOrganisationName == null) {
            snapshot.remove("hiringOrganisationName");
        } else {
            snapshot.put("hiringOrganisationName", hiringOrganisationName);
        }
        if (applicationContactName == null) {
            snapshot.remove("applicationContactName");
        } else {
            snapshot.put("applicationContactName", applicationContactName);
        }
        snapshot.put(
                "descriptionCompleteness",
                candidate.getDescriptionCompleteness().name());
        for (String mutableField : new String[] {
                "matchScore",
                "distanceMiles",
                "applicationStatus",
                "applicationId",
                "cvDocumentId",
                "coverLetterDocumentId",
                "appliedAt",
                "applicationUpdatedAt"}) {
            snapshot.remove(mutableField);
        }

        try {
            String snapshotJson = objectMapper.writeValueAsString(
                    sort(snapshot));
            if (snapshotJson.getBytes(StandardCharsets.UTF_8).length
                    > MAX_SNAPSHOT_BYTES) {
                throw new IllegalArgumentException(
                        "Canonical job snapshot exceeds the storage limit.");
            }
            return new NormalizedSnapshot(
                    canonicalJobId,
                    schemaVersion,
                    snapshotJson,
                    sha256(snapshotJson),
                    sourceRetrievedAt(candidate));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "Canonical job snapshot could not be normalised.");
        }
    }

    private SavedJobResponse response(
            SavedJob savedJob,
            SavedJobSnapshot snapshot,
            Instant now) {
        Job job;
        try {
            job = objectMapper.readValue(snapshot.getSnapshotJson(), Job.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Stored canonical job snapshot is unreadable.");
        }
        return new SavedJobResponse(
                savedJob.getId(),
                savedJob.getCanonicalJobId(),
                snapshot.getCanonicalSchemaVersion(),
                snapshot.getSnapshotVersion(),
                snapshot.getContentVersion(),
                snapshot.getContentSha256(),
                snapshot.getCapturedAt(),
                snapshot.getSourceRetrievedAt(),
                isExpired(job, now) ? "EXPIRED_SNAPSHOT" : "SNAPSHOT",
                savedJob.getSavedAt(),
                savedJob.getUpdatedAt(),
                job);
    }

    private JsonNode sort(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = objectMapper.createObjectNode();
            Map<String, JsonNode> fields = new TreeMap<>();
            Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> field = iterator.next();
                fields.put(field.getKey(), sort(field.getValue()));
            }
            fields.forEach(sorted::set);
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode sorted = objectMapper.createArrayNode();
            node.forEach(value -> sorted.add(sort(value)));
            return sorted;
        }
        return node;
    }

    private Instant sourceRetrievedAt(Job job) {
        return job.getSources().stream()
                .map(JobSourceReference::getRetrievedAtUtc)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .map(OffsetDateTime::toInstant)
                .orElse(null);
    }

    private boolean isExpired(Job job, Instant now) {
        return expired(job.getApplicationDeadlineAtUtc(), now)
                || expired(job.getExpiresAtUtc(), now);
    }

    private boolean expired(OffsetDateTime value, Instant now) {
        return value != null && !value.toInstant().isAfter(now);
    }

    private UUID stableId(String namespace, String left, String right) {
        return UUID.nameUUIDFromBytes(
                (namespace + "\u0000" + left + "\u0000" + right)
                        .getBytes(StandardCharsets.UTF_8));
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private String requireBounded(String value, String field, int maximum) {
        String required = requireText(value, field + " is required.");
        if (required.length() > maximum || containsControl(required)) {
            throw new IllegalArgumentException(field + " is invalid.");
        }
        return required;
    }

    private String optionalBounded(String value, String field, int maximum) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String bounded = value.trim();
        if (bounded.length() > maximum || containsControl(bounded)) {
            throw new IllegalArgumentException(field + " is invalid.");
        }
        return bounded;
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String requireOwner(String userId) {
        String ownerId = requireText(userId, "Authenticated owner is required.");
        if (ownerId.length() > MAX_OWNER_LENGTH || containsControl(ownerId)) {
            throw new IllegalArgumentException("Authenticated owner is invalid.");
        }
        return ownerId;
    }

    private boolean containsControl(String value) {
        return value.chars().anyMatch(
                character -> Character.isISOControl(character)
                        && character != '\n'
                        && character != '\r'
                        && character != '\t');
    }

    private String firstText(String preferred, String fallback) {
        return StringUtils.hasText(preferred) ? preferred : fallback;
    }

    private record NormalizedSnapshot(
            String canonicalJobId,
            String canonicalSchemaVersion,
            String snapshotJson,
            String contentSha256,
            Instant sourceRetrievedAt) {
    }
}
