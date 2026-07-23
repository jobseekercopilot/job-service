package com.jobseekercopilot.jobservice.model.dto;

import java.math.BigDecimal;
import java.util.List;

public class CanonicalLocation {
    private String displayName;
    private String postcode;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private List<String> areaParts;

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPostcode() {
        return postcode;
    }

    public void setPostcode(String postcode) {
        this.postcode = postcode;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public List<String> getAreaParts() {
        return areaParts;
    }

    public void setAreaParts(List<String> areaParts) {
        this.areaParts = areaParts;
    }
}
