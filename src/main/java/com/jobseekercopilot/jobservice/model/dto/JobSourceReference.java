package com.jobseekercopilot.jobservice.model.dto;

import java.time.LocalDateTime;

public class JobSourceReference {
    private String integrationProvider;
    private String provider;
    private String publisher;
    private String externalJobId;
    private String listingUrl;
    private String applyUrl;
    private Boolean directApply;
    private LocalDateTime providerPostedAt;

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
}
