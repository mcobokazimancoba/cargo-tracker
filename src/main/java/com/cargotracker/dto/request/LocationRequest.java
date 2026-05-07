package com.cargotracker.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LocationRequest {

    @NotBlank
    @Size(min = 5, max = 5, message = "UN/LOCODE must be exactly 5 characters")
    private String unlocode;

    @NotBlank
    @Size(max = 100)
    private String city;

    @NotBlank
    @Size(max = 100)
    private String country;

    public LocationRequest() {}

    public String getUnlocode() { return unlocode; }
    public String getCity() { return city; }
    public String getCountry() { return country; }

    public void setUnlocode(String unlocode) { this.unlocode = unlocode; }
    public void setCity(String city) { this.city = city; }
    public void setCountry(String country) { this.country = country; }
}