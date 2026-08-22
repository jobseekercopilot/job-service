package com.jobseekercopilot.jobservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public class ProviderResultStatus {
    private String provider;
    @Schema(allowableValues = {
            "SUCCESS",
            "DISABLED",
            "UNAVAILABLE",
            "TIMED_OUT",
            "SATURATED",
            "RATE_LIMITED",
            "CONFIGURATION_ERROR",
            "REJECTED"
    })
    private String status;
    private int rawResultCount;
    private String errorMessage;
    private ProviderDataProvenance dataProvenance;

    public ProviderResultStatus() {
    }

    public ProviderResultStatus(String provider, String status, int rawResultCount, String errorMessage) {
        this.provider = provider;
        this.status = status;
        this.rawResultCount = rawResultCount;
        this.errorMessage = errorMessage;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getRawResultCount() {
        return rawResultCount;
    }

    public void setRawResultCount(int rawResultCount) {
        this.rawResultCount = rawResultCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public ProviderDataProvenance getDataProvenance() {
        return dataProvenance;
    }

    public void setDataProvenance(ProviderDataProvenance dataProvenance) {
        this.dataProvenance = dataProvenance;
    }
}
