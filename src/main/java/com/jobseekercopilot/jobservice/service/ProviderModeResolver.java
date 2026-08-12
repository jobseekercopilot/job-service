package com.jobseekercopilot.jobservice.service;

import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/** Reads the gateway-owned mode endpoint without guessing whether data is live or fixture-backed. */
@Component
public class ProviderModeResolver {
    private static final Logger log = LoggerFactory.getLogger(ProviderModeResolver.class);
    private static final Duration CACHE_TTL = Duration.ofMinutes(1);

    private final RestTemplate restTemplate;
    private final Map<String, String> modeUrls;
    private final Clock clock;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public ProviderModeResolver(
            @Qualifier("providerModeRestTemplate") RestTemplate restTemplate,
            @Value("${services.reed-gateway.url:http://localhost:8087}") String reedUrl,
            @Value("${services.adzuna-gateway.url:http://adzuna-gateway:8101}") String adzunaUrl,
            @Value("${services.jsearch-gateway.url:http://jsearch-gateway:8102}") String jsearchUrl,
            @Value("${services.nhs-jobs-gateway.url:http://nhs-jobs-gateway:8104}") String nhsJobsUrl,
            @Value("${services.apprenticeships-gateway.url:http://apprenticeships-gateway:8105}") String apprenticeshipsUrl,
            Clock clock) {
        this.restTemplate = restTemplate;
        this.clock = clock;
        this.modeUrls = Map.of(
                "REED", reedUrl + "/internal/provider-mode",
                "ADZUNA", adzunaUrl + "/internal/provider-mode",
                "JSEARCH", jsearchUrl + "/internal/provider-mode",
                "NHS_JOBS", nhsJobsUrl + "/internal/provider-mode",
                "APPRENTICESHIPS", apprenticeshipsUrl + "/internal/provider-mode");
    }

    ProviderModeSnapshot resolve(String provider) {
        String key = provider == null ? "" : provider.toUpperCase(Locale.ROOT);
        String url = modeUrls.get(key);
        if (url == null) {
            return ProviderModeSnapshot.unknown();
        }
        CacheEntry cached = cache.get(key);
        Instant now = clock.instant();
        if (cached != null && cached.createdAt().plus(CACHE_TTL).isAfter(now)) {
            return cached.snapshot();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            ProviderModeSnapshot snapshot = from(response);
            cache.put(key, new CacheEntry(now, snapshot));
            return snapshot;
        } catch (RestClientException exception) {
            log.warn("Provider mode could not be verified provider={} error={}",
                    key, exception.getClass().getSimpleName());
            return ProviderModeSnapshot.unknown();
        }
    }

    private ProviderModeSnapshot from(Map<String, Object> response) {
        if (response == null) {
            return ProviderModeSnapshot.unknown();
        }
        String mode = upperText(response.get("mode"));
        Boolean externalCallsEnabled = response.get("externalCallsEnabled") instanceof Boolean value
                ? value
                : null;
        String dataOrigin = switch (mode) {
            case "LIVE" -> "LIVE_PROVIDER";
            case "FIXTURE" -> "FIXTURE";
            default -> "UNKNOWN";
        };
        return new ProviderModeSnapshot(
                mode,
                dataOrigin,
                text(response.get("datasetId")),
                text(response.get("datasetVersion")),
                text(response.get("scenario")),
                externalCallsEnabled);
    }

    private String text(Object value) {
        return value == null || value.toString().isBlank()
                ? null
                : value.toString().trim();
    }

    private String upperText(Object value) {
        String text = text(value);
        return text == null ? "UNKNOWN" : text.toUpperCase(Locale.ROOT);
    }

    record ProviderModeSnapshot(
            String mode,
            String dataOrigin,
            String datasetId,
            String datasetVersion,
            String scenario,
            Boolean externalCallsEnabled) {
        static ProviderModeSnapshot unknown() {
            return new ProviderModeSnapshot(
                    "UNKNOWN", "UNKNOWN", null, null, null, null);
        }
    }

    private record CacheEntry(Instant createdAt, ProviderModeSnapshot snapshot) {}
}
