package com.jobseekercopilot.jobservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
public class JobSearchRequest {

    @Schema(
            description = "User aspirations for job search including desired roles, locations, and salary expectations",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private Aspirations aspirations;

    @Schema(
            description = "Work preferences including employment types and location preferences",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED
    )
    private WorkPreferences workPreferences;

    @Schema(description = "User home location used for distance calculation and distance sorting.")
    private HomeLocation homeLocation;

    @Schema(description = "Optional list of integration providers to search. Empty means all enabled providers.")
    private List<String> selectedProviders;

    public JobSearchRequest() {
    }

    public Aspirations getAspirations() {
        return aspirations;
    }

    public void setAspirations(Aspirations aspirations) {
        this.aspirations = aspirations;
    }

    public WorkPreferences getWorkPreferences() {
        return workPreferences;
    }

    public void setWorkPreferences(WorkPreferences workPreferences) {
        this.workPreferences = workPreferences;
    }

    public HomeLocation getHomeLocation() {
        return homeLocation;
    }

    public void setHomeLocation(HomeLocation homeLocation) {
        this.homeLocation = homeLocation;
    }

    public List<String> getSelectedProviders() {
        return selectedProviders;
    }

    public void setSelectedProviders(List<String> selectedProviders) {
        this.selectedProviders = selectedProviders;
    }
}
