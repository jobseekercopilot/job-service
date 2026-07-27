package com.jobseekercopilot.jobservice.service;

import com.jobseekercopilot.jobservice.model.dto.SavedJobResponse;

public record SavedJobSaveResult(
        SavedJobSaveOutcome outcome,
        SavedJobResponse savedJob) {
}
