package com.cargotracker.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public class CargoUpdateRequest {

    // All fields nullable: this is a PATCH-style update — null means
    // "don't change". Service layer treats null as untouched, so no
    // @NotNull / @NotBlank here.
    @Size(max = 500)
    private String description;

    @Positive
    @DecimalMax(value = "1000000", message = "Weight cannot exceed 1,000,000 kg")
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