package com.jobseekercopilot.jobservice.model.dto;

public class MatchReason {
    private String code;
    private String severity;
    private String status;
    private String explanation;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
}
