package com.cargotracker.dto.response;

import com.cargotracker.entity.Location;

public class LocationResponse {
    private final Long id;
    private final String unlocode;
    private final String city;
    private final String country;

    public LocationResponse(Long id, String unlocode, String city, String country) {
        this.id = id;
        this.unlocode = unlocode;
        this.city = city;
        this.country = country;
    }

    public static LocationResponse from(Location loc) {
        return new LocationResponse(loc.getId(), loc.getUnlocode(), loc.getCity(), loc.getCountry());
    }

    public Long getId() { return id; }
    public String getUnlocode() { return unlocode; }
    public String getCity() { return city; }
    public String getCountry() { return country; }
}