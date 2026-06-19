package com.jobseekercopilot.jobservice.model.dto;

public class JobSalary {
    private Integer min;
    private Integer max;
    private String currency;

    public JobSalary() {
    }

    public JobSalary(Integer min, Integer max, String currency) {
        this.min = min;
        this.max = max;
        this.currency = currency;
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
}