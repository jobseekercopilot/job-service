package com.jobseekercopilot.jobservice.service;

import org.springframework.stereotype.Service;

@Service
public class DistanceCalculationService {
    private static final double EARTH_RADIUS_MILES = 3958.7613;

    public Double calculateMiles(Double userLat, Double userLon, Double jobLat, Double jobLon) {
        if (userLat == null || userLon == null || jobLat == null || jobLon == null) {
            return null;
        }
        double lat1 = Math.toRadians(userLat);
        double lat2 = Math.toRadians(jobLat);
        double deltaLat = Math.toRadians(jobLat - userLat);
        double deltaLon = Math.toRadians(jobLon - userLon);
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        return EARTH_RADIUS_MILES * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
