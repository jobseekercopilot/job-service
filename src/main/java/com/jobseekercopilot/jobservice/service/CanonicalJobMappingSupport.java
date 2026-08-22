package com.jobseekercopilot.jobservice.service;

import com.jobseekercopilot.jobservice.model.dto.CanonicalValueStatus;
import com.jobseekercopilot.jobservice.model.dto.JobFieldProvenance;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

final class CanonicalJobMappingSupport {

    private static final String DIRECT_RULE_VERSION =
            "canonical-schema-2.0/direct";

    private CanonicalJobMappingSupport() {
    }

    static OffsetDateTime parseOffsetDateTime(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(rawValue);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    static JobFieldProvenance rawField(
            String provider,
            String externalJobId,
            String fieldName,
            Object rawValue) {
        return field(
                provider,
                externalJobId,
                fieldName,
                rawValue,
                null,
                rawValue == null
                        ? CanonicalValueStatus.NOT_PROVIDED
                        : CanonicalValueStatus.RAW_ONLY,
                null);
    }

    static JobFieldProvenance timestampField(
            String provider,
            String externalJobId,
            String fieldName,
            String rawValue,
            OffsetDateTime normalisedValue) {
        return field(
                provider,
                externalJobId,
                fieldName,
                rawValue,
                normalisedValue,
                rawValue == null
                        ? CanonicalValueStatus.NOT_PROVIDED
                        : normalisedValue == null
                                ? CanonicalValueStatus.RAW_ONLY
                                : CanonicalValueStatus.NORMALISED,
                normalisedValue == null ? null : BigDecimal.ONE);
    }

    static JobFieldProvenance normalisedField(
            String provider,
            String externalJobId,
            String fieldName,
            Object rawValue,
            Object normalisedValue) {
        return field(
                provider,
                externalJobId,
                fieldName,
                rawValue,
                normalisedValue,
                CanonicalValueStatus.NORMALISED,
                BigDecimal.ONE);
    }

    private static JobFieldProvenance field(
            String provider,
            String externalJobId,
            String fieldName,
            Object rawValue,
            Object normalisedValue,
            CanonicalValueStatus status,
            BigDecimal confidence) {
        JobFieldProvenance provenance = new JobFieldProvenance();
        provenance.setFieldName(fieldName);
        provenance.setSourceProvider(provider);
        provenance.setSourceExternalJobId(externalJobId);
        provenance.setRawValue(stringValue(rawValue));
        provenance.setNormalisedValue(stringValue(normalisedValue));
        provenance.setStatus(status);
        provenance.setConfidence(confidence);
        provenance.setRuleVersion(DIRECT_RULE_VERSION);
        return provenance;
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }
}
