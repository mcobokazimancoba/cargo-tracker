package com.cargotracker.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public class BookingRequest {

    @NotBlank
    @Size(min = 5, max = 5, message = "UN/LOCODE must be exactly 5 characters")
    private String originUnlocode;

    @NotBlank
    @Size(min = 5, max = 5, message = "UN/LOCODE must be exactly 5 characters")
    private String destinationUnlocode;

    @NotBlank
    @Size(max = 500)
    private String description;

    @NotNull
    @Positive(message = "Weight must be greater than zero")
    private BigDecimal weightKg;

    @Future(message = "Expected arrival must be a future date")
    private LocalDate expectedArrival;

    public BookingRequest() {}

    public String getOriginUnlocode() { return originUnlocode; }
    public String getDestinationUnlocode() { return destinationUnlocode; }
    public String getDescription() { return description; }
    public BigDecimal getWeightKg() { return weightKg; }
    public LocalDate getExpectedArrival() { return expectedArrival; }

    public void setOriginUnlocode(String originUnlocode) { this.originUnlocode = originUnlocode; }
    public void setDestinationUnlocode(String destinationUnlocode) { this.destinationUnlocode = destinationUnlocode; }
    public void setDescription(String description) { this.description = description; }
    public void setWeightKg(BigDecimal weightKg) { this.weightKg = weightKg; }
    public void setExpectedArrival(LocalDate expectedArrival) { this.expectedArrival = expectedArrival; }
}