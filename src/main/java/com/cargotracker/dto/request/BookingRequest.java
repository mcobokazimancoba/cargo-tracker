package com.cargotracker.dto.request;

import com.cargotracker.entity.Location;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public class BookingRequest {

    // @Pattern enforces the UN/LOCODE shape (2 country letters + 3 alphanum)
    // on top of @Size, so "abcde" or "!!!!!" are rejected up front instead of
    // hitting LocationRepository.findByUnlocode and returning a confusing 404.
    @NotBlank
    @Pattern(regexp = Location.UNLOCODE_PATTERN,
             message = "UN/LOCODE must be 5 uppercase chars: 2 country letters + 3 alphanumeric")
    private String originUnlocode;

    @NotBlank
    @Pattern(regexp = Location.UNLOCODE_PATTERN,
             message = "UN/LOCODE must be 5 uppercase chars: 2 country letters + 3 alphanumeric")
    private String destinationUnlocode;

    @NotBlank
    @Size(max = 500)
    private String description;

    // Upper cap chosen because the largest single-piece air cargo on record is
    // ~190 tonnes; 1,000,000 kg is comfortably above any legitimate booking
    // and rejects an obvious DoS / overflow / fat-finger input without
    // restricting real customers.
    @NotNull
    @Positive(message = "Weight must be greater than zero")
    @DecimalMax(value = "1000000", message = "Weight cannot exceed 1,000,000 kg")
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