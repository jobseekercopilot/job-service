package com.jobseekercopilot.jobservice.model.dto;

public class JobSalary {
    private Integer min;
    private Integer max;
    private String currency;
    private String period;
    private Double normalisedAnnualMinimum;
    private Double normalisedAnnualMaximum;
    private Double normalisedAnnualMidpoint;

    public JobSalary() {
    }

    public JobSalary(Integer min, Integer max, String currency) {
        this.min = min;
        this.max = max;
        this.currency = currency;
    }

    public JobSalary(Integer min, Integer max, String currency, String period) {
        this.min = min;
        this.max = max;
        this.currency = currency;
        this.period = period;
    }

    public Integer getMin() {
        return min;
    }

    public void setMin(Integer min) {
        this.min = min;
    }

    public Integer getMax() {
        return max;
    }

    public void setMax(Integer max) {
        this.max = max;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public Double getNormalisedAnnualMinimum() {
        return normalisedAnnualMinimum;
    }

    public void setNormalisedAnnualMinimum(Double normalisedAnnualMinimum) {
        this.normalisedAnnualMinimum = normalisedAnnualMinimum;
    }

    public Double getNormalisedAnnualMaximum() {
        return normalisedAnnualMaximum;
    }

    public void setNormalisedAnnualMaximum(Double normalisedAnnualMaximum) {
        this.normalisedAnnualMaximum = normalisedAnnualMaximum;
    }

    public Double getNormalisedAnnualMidpoint() {
        return normalisedAnnualMidpoint;
    }

    public void setNormalisedAnnualMidpoint(Double normalisedAnnualMidpoint) {
        this.normalisedAnnualMidpoint = normalisedAnnualMidpoint;
    }
}
