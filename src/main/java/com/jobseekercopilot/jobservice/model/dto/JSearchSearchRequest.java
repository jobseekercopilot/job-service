package com.jobseekercopilot.jobservice.model.dto;

public class JSearchSearchRequest {
    private String targetRole;
    private String location;
    private Boolean remoteOnly;
    private String cursor;

    public JSearchSearchRequest() {
    }

    public JSearchSearchRequest(String targetRole, String location, Boolean remoteOnly, String cursor) {
        this.targetRole = targetRole;
        this.location = location;
        this.remoteOnly = remoteOnly;
        this.cursor = cursor;
    }

    public String getTargetRole() { return targetRole; }
    public void setTargetRole(String targetRole) { this.targetRole = targetRole; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public Boolean getRemoteOnly() { return remoteOnly; }
    public void setRemoteOnly(Boolean remoteOnly) { this.remoteOnly = remoteOnly; }
    public String getCursor() { return cursor; }
    public void setCursor(String cursor) { this.cursor = cursor; }
}
