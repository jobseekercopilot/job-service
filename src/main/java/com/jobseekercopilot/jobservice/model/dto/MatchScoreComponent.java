package com.jobseekercopilot.jobservice.model.dto;

public class MatchScoreComponent {
    private String code;
    private Double score;
    private Double maximumScore;
    private String explanation;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }
    public Double getMaximumScore() { return maximumScore; }
    public void setMaximumScore(Double maximumScore) { this.maximumScore = maximumScore; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
}
