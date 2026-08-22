package com.jobseekercopilot.jobservice.model.dto;

import java.math.BigDecimal;

public class JobSalary {
    private Integer min;
    private Integer max;
    private String currency;
    private String period;
    private Double normalisedAnnualMinimum;
    private Double normalisedAnnualMaximum;
    private Double normalisedAnnualMidpoint;
    private BigDecimal rawMinimum;
    private BigDecimal rawMaximum;
    private String rawCurrency;
    private String rawPeriod;
    private BigDecimal minimum;
    private BigDecimal maximum;
    private String currencyCode;
    private SalaryPeriodCode periodCode = SalaryPeriodCode.UNKNOWN;
    private Boolean predicted;
    private String sourceProvider;
    private CanonicalValueStatus normalisationStatus =
            CanonicalValueStatus.NOT_PROVIDED;
    private BigDecimal normalisationConfidence;
    private String normalisationMethod;

    public JobSalary() {
    }

    public JobSalary(Integer min, Integer max, String currency) {
        this(min, max, currency, null);
    }

    public JobSalary(Integer min, Integer max, String currency, String period) {
        this.min = min;
        this.max = max;
        this.currency = currency;
        this.period = period;
        this.rawMinimum = decimal(min);
        this.rawMaximum = decimal(max);
        this.rawCurrency = currency;
        this.rawPeriod = period;
        this.minimum = decimal(min);
        this.maximum = decimal(max);
        this.currencyCode = currency;
        this.normalisationStatus = min == null && max == null
                ? CanonicalValueStatus.NOT_PROVIDED
                : CanonicalValueStatus.RAW_ONLY;
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

    public BigDecimal getRawMinimum() {
        return rawMinimum;
    }

    public void setRawMinimum(BigDecimal rawMinimum) {
        this.rawMinimum = rawMinimum;
    }

    public BigDecimal getRawMaximum() {
        return rawMaximum;
    }

    public void setRawMaximum(BigDecimal rawMaximum) {
        this.rawMaximum = rawMaximum;
    }

    public String getRawCurrency() {
        return rawCurrency;
    }

    public void setRawCurrency(String rawCurrency) {
        this.rawCurrency = rawCurrency;
    }

    public String getRawPeriod() {
        return rawPeriod;
    }

    public void setRawPeriod(String rawPeriod) {
        this.rawPeriod = rawPeriod;
    }

    public BigDecimal getMinimum() {
        return minimum;
    }

    public void setMinimum(BigDecimal minimum) {
        this.minimum = minimum;
    }

    public BigDecimal getMaximum() {
        return maximum;
    }

    public void setMaximum(BigDecimal maximum) {
        this.maximum = maximum;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public SalaryPeriodCode getPeriodCode() {
        return periodCode;
    }

    public void setPeriodCode(SalaryPeriodCode periodCode) {
        this.periodCode = periodCode == null
                ? SalaryPeriodCode.UNKNOWN
                : periodCode;
    }

    public Boolean getPredicted() {
        return predicted;
    }

    public void setPredicted(Boolean predicted) {
        this.predicted = predicted;
    }

    public String getSourceProvider() {
        return sourceProvider;
    }

    public void setSourceProvider(String sourceProvider) {
        this.sourceProvider = sourceProvider;
    }

    public CanonicalValueStatus getNormalisationStatus() {
        return normalisationStatus;
    }

    public void setNormalisationStatus(
            CanonicalValueStatus normalisationStatus) {
        this.normalisationStatus = normalisationStatus == null
                ? CanonicalValueStatus.UNKNOWN
                : normalisationStatus;
    }

    public BigDecimal getNormalisationConfidence() {
        return normalisationConfidence;
    }

    public void setNormalisationConfidence(
            BigDecimal normalisationConfidence) {
        this.normalisationConfidence = normalisationConfidence;
    }

    public String getNormalisationMethod() {
        return normalisationMethod;
    }

    public void setNormalisationMethod(String normalisationMethod) {
        this.normalisationMethod = normalisationMethod;
    }

    private BigDecimal decimal(Integer value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }
}
