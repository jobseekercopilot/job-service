package com.jobseekercopilot.jobservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DistanceCalculationServiceTest {
    private final DistanceCalculationService service = new DistanceCalculationService();

    @Test
    void calculatesApproximateMilesUsingHaversineFormula() {
        Double miles = service.calculateMiles(51.5010, -0.1416, 51.5074, -0.1278);

        assertThat(miles).isNotNull();
        assertThat(miles).isBetween(0.7, 0.8);
    }

    @Test
    void returnsNullWhenUserCoordinatesAreMissing() {
        assertThat(service.calculateMiles(null, -0.1416, 51.5074, -0.1278)).isNull();
        assertThat(service.calculateMiles(51.5010, null, 51.5074, -0.1278)).isNull();
    }

    @Test
    void returnsNullWhenJobCoordinatesAreMissing() {
        assertThat(service.calculateMiles(51.5010, -0.1416, null, -0.1278)).isNull();
        assertThat(service.calculateMiles(51.5010, -0.1416, 51.5074, null)).isNull();
    }
}
