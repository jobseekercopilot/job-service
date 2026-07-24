package com.jobseekercopilot.jobservice.model.dto;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

public class JobSourceReference {
    private String integrationProvider;
    private String provider;
    private String publisher;
    private String rawPublisher;
    private JobSourceType sourceType = JobSourceType.UNKNOWN;
    private String externalJobId;
    private String listingUrl;
    private String applyUrl;
    private Boolean directApply;
    private LocalDateTime providerPostedAt;
    private String providerPostedAtRaw;
    private OffsetDateTime providerPostedAtUtc;
    private String providerExpiresAtRaw;
    private OffsetDateTime providerExpiresAtUtc;
    private OffsetDateTime retrievedAtUtc;

    public String getIntegrationProvider() {
        return integrationProvider;
    }

    public void setIntegrationProvider(String integrationProvider) {
        this.integrationProvider = integrationProvider;
        this.provider = integrationProvider;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
        this.integrationProvider = provider;
    }

    public String getPublisher() {
        return publisher;
    }

    public void setPublisher(String publisher) {
        this.publisher = publisher;
    }

    public String getRawPublisher() {
        return rawPublisher;
    }

    public void setRawPublisher(String rawPublisher) {
        this.rawPublisher = rawPublisher;
    }

    public JobSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(JobSourceType sourceType) {
        this.sourceType = sourceType == null
                ? JobSourceType.UNKNOWN
                : sourceType;
    }

    public String getExternalJobId() {
        return externalJobId;
    }

    public void setExternalJobId(String externalJobId) {
        this.externalJobId = externalJobId;
    }

    public String getListingUrl() {
        return listingUrl;
    }

    public void setListingUrl(String listingUrl) {
        this.listingUrl = listingUrl;
    }

    public String getApplyUrl() {
        return applyUrl;
    }

    public void setApplyUrl(String applyUrl) {
        this.applyUrl = applyUrl;
    }

    public Boolean getDirectApply() {
        return directApply;
    }

    public void setDirectApply(Boolean directApply) {
        this.directApply = directApply;
    }

    public LocalDateTime getProviderPostedAt() {
        return providerPostedAt;
    }

    public void setProviderPostedAt(LocalDateTime providerPostedAt) {
        this.providerPostedAt = providerPostedAt;
    }

    public String getProviderPostedAtRaw() {
        return providerPostedAtRaw;
    }

    public void setProviderPostedAtRaw(String providerPostedAtRaw) {
        this.providerPostedAtRaw = providerPostedAtRaw;
    }

    public OffsetDateTime getProviderPostedAtUtc() {
        return providerPostedAtUtc;
    }

    public void setProviderPostedAtUtc(
            OffsetDateTime providerPostedAtUtc) {
        this.providerPostedAtUtc = providerPostedAtUtc;
    }

    public String getProviderExpiresAtRaw() {
        return providerExpiresAtRaw;
    }

    public void setProviderExpiresAtRaw(String providerExpiresAtRaw) {
        this.providerExpiresAtRaw = providerExpiresAtRaw;
    }

    public OffsetDateTime getProviderExpiresAtUtc() {
        return providerExpiresAtUtc;
    }

    public void setProviderExpiresAtUtc(
            OffsetDateTime providerExpiresAtUtc) {
        this.providerExpiresAtUtc = providerExpiresAtUtc;
    }

    public OffsetDateTime getRetrievedAtUtc() {
        return retrievedAtUtc;
    }

    public void setRetrievedAtUtc(OffsetDateTime retrievedAtUtc) {
        this.retrievedAtUtc = retrievedAtUtc;
    }
}
