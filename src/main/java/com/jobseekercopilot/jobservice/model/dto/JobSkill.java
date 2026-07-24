package com.jobseekercopilot.jobservice.model.dto;

import java.math.BigDecimal;

public class JobSkill {
    private String name;
    private String rawName;
    private JobSkillType type = JobSkillType.UNKNOWN;
    private BigDecimal normalisationConfidence;
    private CanonicalValueStatus normalisationStatus =
            CanonicalValueStatus.NOT_PROVIDED;
    private String sourceProvider;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRawName() {
        return rawName;
    }

    public void setRawName(String rawName) {
        this.rawName = rawName;
    }

    public JobSkillType getType() {
        return type;
    }

    public void setType(JobSkillType type) {
        this.type = type == null ? JobSkillType.UNKNOWN : type;
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
