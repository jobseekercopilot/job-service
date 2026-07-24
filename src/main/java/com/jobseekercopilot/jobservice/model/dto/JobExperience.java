package com.jobseekercopilot.jobservice.model.dto;

import java.math.BigDecimal;

public class JobExperience {
    private String rawValue;
    private ExperienceLevelCode level = ExperienceLevelCode.UNKNOWN;
    private BigDecimal minimumYears;
    private BigDecimal maximumYears;
    private BigDecimal normalisationConfidence;
    private CanonicalValueStatus normalisationStatus =
            CanonicalValueStatus.NOT_PROVIDED;
    private String sourceProvider;

    public String getRawValue() {
        return rawValue;
    }

    public void setRawValue(String rawValue) {
        this.rawValue = rawValue;
    }

    public ExperienceLevelCode getLevel() {
        return level;
    }

    public void setLevel(ExperienceLevelCode level) {
        this.level = level == null ? ExperienceLevelCode.UNKNOWN : level;
    }

    public BigDecimal getMinimumYears() {
        return minimumYears;
    }

    public void setMinimumYears(BigDecimal minimumYears) {
        this.minimumYears = minimumYears;
    }

    public BigDecimal getMaximumYears() {
        return maximumYears;
    }

    public void setMaximumYears(BigDecimal maximumYears) {
        this.maximumYears = maximumYears;
    }

    public BigDecimal getNormalisationConfidence() {
        return normalisationConfidence;
    }

    public void setNormalisationConfidence(BigDecimal normalisationConfidence) {
        this.normalisationConfidence = normalisationConfidence;
    }

    public CanonicalValueStatus getNormalisationStatus() {
        return normalisationStatus;
    }

    public void setNormalisationStatus(
            CanonicalValueStatus normalisationStatus) {
        this.normalisationStatus = normalisationStatus == null
                ? CanonicalValueStatus.UNKNOWN
                : normalisationStatus;
    }

    public String getSourceProvider() {
        return sourceProvider;
    }

    public void setSourceProvider(String sourceProvider) {
        this.sourceProvider = sourceProvider;
    }
}
