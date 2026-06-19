package com.jobseekercopilot.jobservice.model.dto;

import java.util.List;

public class Aspirations {
    private List<String> desiredRoles;
    private List<String> industries;
    private SalaryExpectation salaryExpectation;
    private List<String> locations;

    public Aspirations() {
    }

    public List<String> getDesiredRoles() {
        return desiredRoles;
    }

    public void setDesiredRoles(List<String> desiredRoles) {
        this.desiredRoles = desiredRoles;
    }

    public List<String> getIndustries() {
        return industries;
    }

    public void setIndustries(List<String> industries) {
        this.industries = industries;
    }

    public SalaryExpectation getSalaryExpectation() {
        return salaryExpectation;
    }

    public void setSalaryExpectation(SalaryExpectation salaryExpectation) {
        this.salaryExpectation = salaryExpectation;
    }

    public List<String> getLocations() {
        return locations;
    }

    public void setLocations(List<String> locations) {
        this.locations = locations;
    }
}