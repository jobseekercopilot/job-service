package com.jobseekercopilot.jobservice.service;

public class SavedJobNotFoundException extends RuntimeException {
    public SavedJobNotFoundException() {
        super("Saved job was not found.");
    }
}
