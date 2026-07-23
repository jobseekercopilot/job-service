package com.jobseekercopilot.jobservice.service;

import com.jobseekercopilot.jobservice.model.dto.JobSalary;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class SalaryNormalisationService {

    public void normalise(JobSalary salary) {
        if (salary == null) {
            return;
        }
        Double minimum = annualise(salary.getMin(), salary.getPeriod());
        Double maximum = annualise(salary.getMax(), salary.getPeriod());
        salary.setNormalisedAnnualMinimum(minimum);
        salary.setNormalisedAnnualMaximum(maximum);

        if (minimum != null && maximum != null) {
            salary.setNormalisedAnnualMidpoint((minimum + maximum) / 2.0);
        } else {
            salary.setNormalisedAnnualMidpoint(minimum != null ? minimum : maximum);
        }
    }

    private Double annualise(Integer amount, String period) {
        if (amount == null || period == null || period.isBlank()) {
            return null;
        }
        return switch (period.trim().toUpperCase(Locale.ROOT)) {
            case "YEAR", "ANNUAL", "ANNUALLY" -> amount.doubleValue();
            case "MONTH", "MONTHLY" -> amount * 12.0;
            case "WEEK", "WEEKLY" -> amount * 52.0;
            case "DAY", "DAILY" -> amount * 5.0 * 52.0;
            case "HOUR", "HOURLY" -> amount * 37.5 * 52.0;
            default -> null;
        };
    }
}
