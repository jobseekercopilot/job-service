package com.jobseekercopilot.jobservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;

@Schema(description = "Minimal claimant-declared evidence used for deterministic job matching. It contains no inferred eligibility facts.")
public class CandidateProfile {
    private List<String> skills = new ArrayList<>();
    private List<CandidateRole> roles = new ArrayList<>();
    private List<CandidateQualification> qualifications = new ArrayList<>();

    public List<String> getSkills() { return skills; }
    public void setSkills(List<String> skills) { this.skills = skills == null ? new ArrayList<>() : skills; }
    public List<CandidateRole> getRoles() { return roles; }
    public void setRoles(List<CandidateRole> roles) { this.roles = roles == null ? new ArrayList<>() : roles; }
    public List<CandidateQualification> getQualifications() { return qualifications; }
    public void setQualifications(List<CandidateQualification> qualifications) { this.qualifications = qualifications == null ? new ArrayList<>() : qualifications; }
}
