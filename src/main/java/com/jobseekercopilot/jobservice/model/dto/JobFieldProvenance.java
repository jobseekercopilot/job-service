package com.jobseekercopilot.jobservice.model.dto;

import java.math.BigDecimal;

public class JobFieldProvenance {
    private String fieldName;
    private String sourceProvider;
    private String sourceExternalJobId;
    private String rawValue;
    private String normalisedValue;
    private CanonicalValueStatus status = CanonicalValueStatus.UNKNOWN;
    private BigDecimal confidence;
    private String ruleVersion;

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getSourceProvider() {
        return sourceProvider;
    }

    public void setSourceProvider(String sourceProvider) {
        this.sourceProvider = sourceProvider;
    }

    public String getSourceExternalJobId() {
        return sourceExternalJobId;
    }

    public void setSourceExternalJobId(String sourceExternalJobId) {
        this.sourceExternalJobId = sourceExternalJobId;
    }

    public String getRawValue() {
        return rawValue;
    }

    public void setRawValue(String rawValue) {
        this.rawValue = rawValue;
    }

    public String getNormalisedValue() {
        return normalisedValue;
    }

    public void setNormalisedValue(String normalisedValue) {
        this.normalisedValue = normalisedValue;
    }

    public CanonicalValueStatus getStatus() {
        return status;
    }

    public void setStatus(CanonicalValueStatus status) {
        this.status = status == null ? CanonicalValueStatus.UNKNOWN : status;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public String getRuleVersion() {
        return ruleVersion;
    }

    public void setRuleVersion(String ruleVersion) {
        this.ruleVersion = ruleVersion;
    }
}
