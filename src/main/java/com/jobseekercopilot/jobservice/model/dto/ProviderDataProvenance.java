package com.jobseekercopilot.jobservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "Truthful runtime origin and cache metadata for one provider result set.")
public class ProviderDataProvenance {
    private String providerMode;
    private String dataOrigin;
    private String resultSource;
    private String datasetId;
    private String datasetVersion;
    private String scenario;
    private Boolean externalCallsEnabled;
    private OffsetDateTime retrievedAtUtc;
    private OffsetDateTime servedAtUtc;
    private Long cacheAgeSeconds;

    public String getProviderMode() { return providerMode; }
    public void setProviderMode(String providerMode) { this.providerMode = providerMode; }
    public String getDataOrigin() { return dataOrigin; }
    public void setDataOrigin(String dataOrigin) { this.dataOrigin = dataOrigin; }
    public String getResultSource() { return resultSource; }
    public void setResultSource(String resultSource) { this.resultSource = resultSource; }
    public String getDatasetId() { return datasetId; }
    public void setDatasetId(String datasetId) { this.datasetId = datasetId; }
    public String getDatasetVersion() { return datasetVersion; }
    public void setDatasetVersion(String datasetVersion) { this.datasetVersion = datasetVersion; }
    public String getScenario() { return scenario; }
    public void setScenario(String scenario) { this.scenario = scenario; }
    public Boolean getExternalCallsEnabled() { return externalCallsEnabled; }
    public void setExternalCallsEnabled(Boolean externalCallsEnabled) { this.externalCallsEnabled = externalCallsEnabled; }
    public OffsetDateTime getRetrievedAtUtc() { return retrievedAtUtc; }
    public void setRetrievedAtUtc(OffsetDateTime retrievedAtUtc) { this.retrievedAtUtc = retrievedAtUtc; }
    public OffsetDateTime getServedAtUtc() { return servedAtUtc; }
    public void setServedAtUtc(OffsetDateTime servedAtUtc) { this.servedAtUtc = servedAtUtc; }
    public Long getCacheAgeSeconds() { return cacheAgeSeconds; }
    public void setCacheAgeSeconds(Long cacheAgeSeconds) { this.cacheAgeSeconds = cacheAgeSeconds; }
}
