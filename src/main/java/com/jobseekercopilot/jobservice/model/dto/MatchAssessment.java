package com.jobseekercopilot.jobservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;

@Schema(description = "Explainable deterministic match result. No LLM or provider-supplied relevance score is used.")
public class MatchAssessment {
    private Double score;
    private String provenance;
    private String algorithmVersion;
    private String targetRole;
    private String rating;
    private boolean candidateProfileUsed;
    private List<MatchScoreComponent> components = new ArrayList<>();
    private List<MatchReason> reasons = new ArrayList<>();
    private List<MatchReason> hardGateReasons = new ArrayList<>();

    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }
    public String getProvenance() { return provenance; }
    public void setProvenance(String provenance) { this.provenance = provenance; }
    public String getAlgorithmVersion() { return algorithmVersion; }
    public void setAlgorithmVersion(String algorithmVersion) { this.algorithmVersion = algorithmVersion; }
    public String getTargetRole() { return targetRole; }
    public void setTargetRole(String targetRole) { this.targetRole = targetRole; }
    public String getRating() { return rating; }
    public void setRating(String rating) { this.rating = rating; }
    public boolean isCandidateProfileUsed() { return candidateProfileUsed; }
    public void setCandidateProfileUsed(boolean candidateProfileUsed) { this.candidateProfileUsed = candidateProfileUsed; }
    public List<MatchScoreComponent> getComponents() { return components; }
    public void setComponents(List<MatchScoreComponent> components) { this.components = components == null ? new ArrayList<>() : components; }
    public List<MatchReason> getReasons() { return reasons; }
    public void setReasons(List<MatchReason> reasons) { this.reasons = reasons == null ? new ArrayList<>() : reasons; }
    public List<MatchReason> getHardGateReasons() { return hardGateReasons; }
    public void setHardGateReasons(List<MatchReason> hardGateReasons) { this.hardGateReasons = hardGateReasons == null ? new ArrayList<>() : hardGateReasons; }
}
