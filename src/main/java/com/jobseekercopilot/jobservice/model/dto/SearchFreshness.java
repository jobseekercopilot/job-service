package com.jobseekercopilot.jobservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "Aggregate retrieval/cache truth for the response; provider-specific truth remains in providerResults.")
public class SearchFreshness {
    private String resultSource;
    private OffsetDateTime oldestRetrievedAtUtc;
    private OffsetDateTime servedAtUtc;
    private Long maximumCacheAgeSeconds;

    public String getResultSource() { return resultSource; }
    public void setResultSource(String resultSource) { this.resultSource = resultSource; }
    public OffsetDateTime getOldestRetrievedAtUtc() { return oldestRetrievedAtUtc; }
    public void setOldestRetrievedAtUtc(OffsetDateTime oldestRetrievedAtUtc) { this.oldestRetrievedAtUtc = oldestRetrievedAtUtc; }
    public OffsetDateTime getServedAtUtc() { return servedAtUtc; }
    public void setServedAtUtc(OffsetDateTime servedAtUtc) { this.servedAtUtc = servedAtUtc; }
    public Long getMaximumCacheAgeSeconds() { return maximumCacheAgeSeconds; }
    public void setMaximumCacheAgeSeconds(Long maximumCacheAgeSeconds) { this.maximumCacheAgeSeconds = maximumCacheAgeSeconds; }
}
