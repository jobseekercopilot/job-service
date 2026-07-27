package com.jobseekercopilot.jobservice.model.dto;

import java.util.List;

public record SavedJobPageResponse(
        List<SavedJobResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
