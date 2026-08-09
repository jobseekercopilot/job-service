package com.jobseekercopilot.jobservice.model.dto;

import java.math.BigDecimal;
import java.util.List;

public class CanonicalLocation {
    private String locationId;
    private String rawDisplayName;
    private String rawCity;
    private String rawRegion;
    private String rawCountry;
    private String displayName;
    private String postcode;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private List<String> areaParts;
    private String city;
    private String region;
    private String countryCode;
    private String sourceProvider;
    private CanonicalValueStatus normalisationStatus =
            CanonicalValueStatus.NOT_PROVIDED;
    private BigDecimal normalisationConfidence;
    private String locationType;
    private String precision;
    private String confidence;

    public String getLocationId() { return locationId; }
    public void setLocationId(String locationId) { this.locationId = locationId; }

    public String getRawDisplayName() {
        return rawDisplayName;
    }

    public void setRawDisplayName(String rawDisplayName) {
        this.rawDisplayName = rawDisplayName;
    }

    public String getRawCity() {
        return rawCity;
    }

    public void setRawCity(String rawCity) {
        this.rawCity = rawCity;
    }

    public String getRawRegion() {
        return rawRegion;
    }

    public void setRawRegion(String rawRegion) {
        this.rawRegion = rawRegion;
    }

    public String getRawCountry() {
        return rawCountry;
    }

    public void setRawCountry(String rawCountry) {
        this.rawCountry = rawCountry;
    }

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

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getSourceProvider() {
        return sourceProvider;
    }

    public void setSourceProvider(String sourceProvider) {
        this.sourceProvider = sourceProvider;
    }

    public CanonicalValueStatus getNormalisationStatus() {
        return normalisationStatus;
    }

    public void setNormalisationStatus(
            CanonicalValueStatus normalisationStatus) {
        this.normalisationStatus = normalisationStatus == null
                ? CanonicalValueStatus.UNKNOWN
                : normalisationStatus;
    }

    public BigDecimal getNormalisationConfidence() {
        return normalisationConfidence;
    }

    public void setNormalisationConfidence(
            BigDecimal normalisationConfidence) {
        this.normalisationConfidence = normalisationConfidence;
    }

    public String getLocationType() { return locationType; }
    public void setLocationType(String locationType) { this.locationType = locationType; }
    public String getPrecision() { return precision; }
    public void setPrecision(String precision) { this.precision = precision; }
    public String getConfidence() { return confidence; }
    public void setConfidence(String confidence) { this.confidence = confidence; }
}
