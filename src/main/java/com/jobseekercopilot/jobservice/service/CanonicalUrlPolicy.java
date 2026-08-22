package com.jobseekercopilot.jobservice.service;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

final class CanonicalUrlPolicy {

    private static final Set<String> ALLOWED_SCHEMES =
            Set.of("http", "https");

    private CanonicalUrlPolicy() {
    }

    static String safeHttpUrl(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        try {
            String trimmed = candidate.trim();
            URI uri = URI.create(trimmed);
            String scheme = uri.getScheme();
            if (scheme == null
                    || !ALLOWED_SCHEMES.contains(
                            scheme.toLowerCase(Locale.ROOT))
                    || uri.getHost() == null
                    || uri.getHost().isBlank()
                    || uri.getUserInfo() != null) {
                return null;
            }
            return scheme.toLowerCase(Locale.ROOT)
                    + trimmed.substring(scheme.length());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
