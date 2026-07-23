package com.jobseekercopilot.jobservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobseekercopilot.jobservice.model.dto.JobSalary;
import org.junit.jupiter.api.Test;

class SalaryNormalisationServiceTest {
    private final SalaryNormalisationService service = new SalaryNormalisationService();

    @Test
    void normalisesHourlyDailyAndYearlySalariesToAnnualMidpoint() {
        JobSalary hourly = new JobSalary(20, 30, "GBP", "HOUR");
        JobSalary daily = new JobSalary(400, 600, "GBP", "DAY");
        JobSalary yearly = new JobSalary(50000, 70000, "GBP", "YEAR");

        service.normalise(hourly);
        service.normalise(daily);
        service.normalise(yearly);

        assertThat(hourly.getNormalisedAnnualMidpoint()).isEqualTo(48750.0);
        assertThat(daily.getNormalisedAnnualMidpoint()).isEqualTo(130000.0);
        assertThat(yearly.getNormalisedAnnualMidpoint()).isEqualTo(60000.0);
    }

    @Test
    void leavesUnknownPeriodUnnormalisedSoItSortsLast() {
        JobSalary salary = new JobSalary(100, 200, "GBP", "PROJECT");

        service.normalise(salary);

        assertThat(salary.getNormalisedAnnualMinimum()).isNull();
        assertThat(salary.getNormalisedAnnualMaximum()).isNull();
        assertThat(salary.getNormalisedAnnualMidpoint()).isNull();
    }
}
