package com.jobseekercopilot.jobservice.model.dto;

import java.util.List;

public class WorkPreferences {
    private List<String> employmentType;
    private String remotePreference;
    private List<String> companySize;
    private List<String> culture;
    private Double homeLatitude;
    private Double homeLongitude;

    public WorkPreferences() {
    }

    public List<String> getEmploymentType() {
        return employmentType;
    }

    public void setEmploymentType(List<String> employmentType) {
        this.employmentType = employmentType;
    }

    public String getRemotePreference() {
        return remotePreference;
    }

    public void setRemotePreference(String remotePreference) {
        this.remotePreference = remotePreference;
    }

    public List<String> getCompanySize() {
        return companySize;
    }

    public void setCompanySize(List<String> companySize) {
        this.companySize = companySize;
    }

    public List<String> getCulture() {
        return culture;
    }

    public void setCulture(List<String> culture) {
        this.culture = culture;
    }

    public Double getHomeLatitude() {
        return homeLatitude;
    }

    public void setHomeLatitude(Double homeLatitude) {
        this.homeLatitude = homeLatitude;
    }

    public Double getHomeLongitude() {
        return homeLongitude;
    }

    public void setHomeLongitude(Double homeLongitude) {
        this.homeLongitude = homeLongitude;
    }
}
