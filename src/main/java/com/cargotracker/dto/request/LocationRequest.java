package com.cargotracker.dto.request;

import com.cargotracker.entity.Location;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class LocationRequest {

    @NotBlank
    @Pattern(regexp = Location.UNLOCODE_PATTERN,
             message = "UN/LOCODE must be 5 uppercase chars: 2 country letters + 3 alphanumeric")
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