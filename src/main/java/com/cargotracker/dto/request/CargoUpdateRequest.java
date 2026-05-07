package com.cargotracker.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public class CargoUpdateRequest {

    @Size(max = 500)
    private String description;

    @Positive
    private BigDecimal weightKg;

    @Future
    private LocalDate expectedArrival;

    public CargoUpdateRequest() {}

    public String getDescription() { return description; }
    public BigDecimal getWeightKg() { return weightKg; }
    public LocalDate getExpectedArrival() { return expectedArrival; }

    public void setDescription(String description) { this.description = description; }
    public void setWeightKg(BigDecimal weightKg) { this.weightKg = weightKg; }
    public void setExpectedArrival(LocalDate expectedArrival) { this.expectedArrival = expectedArrival; }
}