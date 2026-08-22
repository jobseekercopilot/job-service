package com.jobseekercopilot.jobservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;

@Schema(description = "Deterministic vacancy classification used to explain filtering and suitability checks.")
public class JobDiscoveryAssessment {
    private String algorithmVersion;
    @Schema(allowableValues = {"OPEN_AT_RETRIEVAL", "UNKNOWN", "CLOSED", "EXPIRED"})
    private String availability;
    @Schema(allowableValues = {"VACANCY", "APPRENTICESHIP", "PAID_TRAINING"})
    private String engagementType;
    @Schema(allowableValues = {
            "SOFTWARE", "DATA", "QUALITY_ENGINEERING", "CYBER_SECURITY",
            "DESIGN", "PRODUCT_PROJECT", "IT_SUPPORT", "NON_TECHNICAL", "UNKNOWN"
    })
    private String occupationFamily;
    @Schema(allowableValues = {"JUNIOR_ENTRY", "MID", "SENIOR", "LEADERSHIP", "UNSPECIFIED"})
    private String seniority;
    @Schema(allowableValues = {"ALIGNED", "RELATED", "MISMATCHED", "UNKNOWN"})
    private String targetRoleAlignment;
    private String targetRole;
    private boolean excluded;
    private List<String> exclusionReasons = new ArrayList<>();

    public String getAlgorithmVersion() { return algorithmVersion; }
    public void setAlgorithmVersion(String algorithmVersion) { this.algorithmVersion = algorithmVersion; }
    public String getAvailability() { return availability; }
    public void setAvailability(String availability) { this.availability = availability; }
    public String getEngagementType() { return engagementType; }
    public void setEngagementType(String engagementType) { this.engagementType = engagementType; }
    public String getOccupationFamily() { return occupationFamily; }
    public void setOccupationFamily(String occupationFamily) { this.occupationFamily = occupationFamily; }
    public String getSeniority() { return seniority; }
    public void setSeniority(String seniority) { this.seniority = seniority; }
    public String getTargetRoleAlignment() { return targetRoleAlignment; }
    public void setTargetRoleAlignment(String targetRoleAlignment) { this.targetRoleAlignment = targetRoleAlignment; }
    public String getTargetRole() { return targetRole; }
    public void setTargetRole(String targetRole) { this.targetRole = targetRole; }
    public boolean isExcluded() { return excluded; }
    public void setExcluded(boolean excluded) { this.excluded = excluded; }
    public List<String> getExclusionReasons() { return exclusionReasons; }
    public void setExclusionReasons(List<String> exclusionReasons) { this.exclusionReasons = exclusionReasons == null ? new ArrayList<>() : exclusionReasons; }
}
