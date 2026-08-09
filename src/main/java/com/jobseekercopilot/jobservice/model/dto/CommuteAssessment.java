package com.jobseekercopilot.jobservice.model.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class CommuteAssessment {
    public enum Status { WITHIN_PREFERENCE, ABOVE_PREFERENCE, UNAVAILABLE, NOT_APPLICABLE, NOT_EVALUATED }
    public enum EstimateStatus { ESTIMATED, APPROXIMATE, UNAVAILABLE }
    public enum Outcome { WITHIN, ABOVE, NO_PREFERENCE, UNAVAILABLE }

    private Status status;
    private WorkplaceTypeCode workplaceType;
    private List<ModeAssessment> modes = new ArrayList<>();
    private CommuteTravelMode bestSuitableMode;
    private String explanationCode;
    private String providerAttribution;

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public WorkplaceTypeCode getWorkplaceType() { return workplaceType; }
    public void setWorkplaceType(WorkplaceTypeCode workplaceType) { this.workplaceType = workplaceType; }
    public List<ModeAssessment> getModes() { return modes; }
    public void setModes(List<ModeAssessment> modes) { this.modes = modes == null ? new ArrayList<>() : modes; }
    public CommuteTravelMode getBestSuitableMode() { return bestSuitableMode; }
    public void setBestSuitableMode(CommuteTravelMode bestSuitableMode) { this.bestSuitableMode = bestSuitableMode; }
    public String getExplanationCode() { return explanationCode; }
    public void setExplanationCode(String explanationCode) { this.explanationCode = explanationCode; }
    public String getProviderAttribution() { return providerAttribution; }
    public void setProviderAttribution(String providerAttribution) { this.providerAttribution = providerAttribution; }

    public static class ModeAssessment {
        private CommuteTravelMode mode;
        private EstimateStatus estimateStatus;
        private Integer durationMinutes;
        private BigDecimal distanceMiles;
        private Integer thresholdMinutes;
        private Outcome outcome;
        private OffsetDateTime calculatedFor;
        private String originPrecision;
        private String destinationPrecision;
        private String reasonCode;

        public CommuteTravelMode getMode() { return mode; }
        public void setMode(CommuteTravelMode mode) { this.mode = mode; }
        public EstimateStatus getEstimateStatus() { return estimateStatus; }
        public void setEstimateStatus(EstimateStatus estimateStatus) { this.estimateStatus = estimateStatus; }
        public Integer getDurationMinutes() { return durationMinutes; }
        public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
        public BigDecimal getDistanceMiles() { return distanceMiles; }
        public void setDistanceMiles(BigDecimal distanceMiles) { this.distanceMiles = distanceMiles; }
        public Integer getThresholdMinutes() { return thresholdMinutes; }
        public void setThresholdMinutes(Integer thresholdMinutes) { this.thresholdMinutes = thresholdMinutes; }
        public Outcome getOutcome() { return outcome; }
        public void setOutcome(Outcome outcome) { this.outcome = outcome; }
        public OffsetDateTime getCalculatedFor() { return calculatedFor; }
        public void setCalculatedFor(OffsetDateTime calculatedFor) { this.calculatedFor = calculatedFor; }
        public String getOriginPrecision() { return originPrecision; }
        public void setOriginPrecision(String originPrecision) { this.originPrecision = originPrecision; }
        public String getDestinationPrecision() { return destinationPrecision; }
        public void setDestinationPrecision(String destinationPrecision) { this.destinationPrecision = destinationPrecision; }
        public String getReasonCode() { return reasonCode; }
        public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }
    }
}
