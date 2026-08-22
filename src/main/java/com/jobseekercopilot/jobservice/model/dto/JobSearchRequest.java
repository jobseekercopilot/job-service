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

    @Schema(description = "Optional minimal claimant-declared evidence used by deterministic matching. Omitted for non-profile searches.")
    private CandidateProfile candidateProfile;

    @Schema(description = "Optional list of integration providers to search. Empty means all enabled providers.")
    private List<String> selectedProviders;

    @Schema(
            description = "One-based page applied to the aggregate compatibility view and independently to every target role.",
            example = "1",
            minimum = "1",
            maximum = "100",
            defaultValue = "1"
    )
    private Integer page;

    @Schema(
            description = "Maximum results returned in the aggregate compatibility view and per target role on one page.",
            example = "10",
            minimum = "1",
            maximum = "50",
            defaultValue = "10"
    )
    private Integer pageSize;

    @Schema(
            description = "Stable result order applied to the aggregate compatibility view and independently to every target role.",
            allowableValues = {
                    "MOST_RELEVANT",
                    "CLOSEST",
                    "HIGHEST_SALARY",
                    "NEWEST_POSTED",
                    "OLDEST_POSTED",
                    "COMPANY_AZ",
                    "JOB_TITLE_AZ"
            },
            defaultValue = "MOST_RELEVANT"
    )
    private String sort;

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

    public CandidateProfile getCandidateProfile() {
        return candidateProfile;
    }

    public void setCandidateProfile(CandidateProfile candidateProfile) {
        this.candidateProfile = candidateProfile;
    }

    public List<String> getSelectedProviders() {
        return selectedProviders;
    }

    public void setSelectedProviders(List<String> selectedProviders) {
        this.selectedProviders = selectedProviders;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public String getSort() {
        return sort;
    }

    public void setSort(String sort) {
        this.sort = sort;
    }
}
