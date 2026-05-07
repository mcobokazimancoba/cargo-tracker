package com.cargotracker.dto.response;

import com.cargotracker.entity.Cargo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class CargoSummaryResponse {
    private final Long id;
    private final String trackingNumber;
    private final LocationResponse origin;
    private final LocationResponse destination;
    private final String description;
    private final BigDecimal weightKg;
    private final String status;
    private final LocalDate expectedArrival;
    private final LocalDateTime createdAt;

    public CargoSummaryResponse(Long id, String trackingNumber, LocationResponse origin,
                                LocationResponse destination, String description,
                                BigDecimal weightKg, String status,
                                LocalDate expectedArrival, LocalDateTime createdAt) {
        this.id = id;
        this.trackingNumber = trackingNumber;
        this.origin = origin;
        this.destination = destination;
        this.description = description;
        this.weightKg = weightKg;
        this.status = status;
        this.expectedArrival = expectedArrival;
        this.createdAt = createdAt;
    }

    public static CargoSummaryResponse from(Cargo c) {
        return new CargoSummaryResponse(
            c.getId(),
            c.getTrackingNumber(),
            LocationResponse.from(c.getOrigin()),
            LocationResponse.from(c.getDestination()),
            c.getDescription(),
            c.getWeightKg(),
            c.getStatus().name(),
            c.getExpectedArrival(),
            c.getCreatedAt()
        );
    }

    // Getters
    public Long getId() { return id; }
    public String getTrackingNumber() { return trackingNumber; }
    public LocationResponse getOrigin() { return origin; }
    public LocationResponse getDestination() { return destination; }
    public String getDescription() { return description; }
    public BigDecimal getWeightKg() { return weightKg; }
    public String getStatus() { return status; }
    public LocalDate getExpectedArrival() { return expectedArrival; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}