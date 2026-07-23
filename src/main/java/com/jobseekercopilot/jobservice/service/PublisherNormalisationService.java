package com.jobseekercopilot.jobservice.service;

import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Locale;

@Service
public class PublisherNormalisationService {

    public String normalise(String publisher, String url, Boolean directApply, String fallback) {
        if (Boolean.TRUE.equals(directApply)) {
            return "Employer Site";
        }
        String value = firstNonBlank(publisher, host(url), fallback);
        if (value == null || value.isBlank()) {
            return "Other Job Site";
        }
        String normalised = value.toLowerCase(Locale.ROOT);
        if (normalised.contains("indeed")) {
            return "Indeed";
        }
        if (normalised.contains("linkedin")) {
            return "LinkedIn";
        }
        if (normalised.contains("reed")) {
            return "Reed.co.uk";
        }
        if (normalised.contains("glassdoor")) {
            return "Glassdoor";
        }
        if (normalised.contains("totaljobs")) {
            return "Totaljobs";
        }
        if (normalised.contains("cv-library") || normalised.contains("cvlibrary")) {
            return "CV-Library";
        }
        if (normalised.contains("google")) {
            return "Google Jobs";
        }
        if (normalised.contains("adzuna")) {
            return "Adzuna";
        }
        return value.trim();
    }

    private String host(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            return URI.create(url).getHost();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
