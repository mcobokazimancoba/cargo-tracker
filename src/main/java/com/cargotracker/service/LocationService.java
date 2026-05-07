package com.cargotracker.service;

import com.cargotracker.dto.request.Requests;
import com.cargotracker.dto.response.Responses;
import com.cargotracker.entity.Location;
import com.cargotracker.exception.Exceptions;
import com.cargotracker.repository.LocationRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Domain service for port and depot location management.
 *
 * Authentication and user management live in {@link AuthService}.
 * This class has a single responsibility: managing the network of locations
 * that cargo can originate from or be destined to.
 */
@ApplicationScoped
public class LocationService {

    @Inject
    private LocationRepository locationRepository;

    @Transactional
    public Responses.Location createLocation(@NotNull @Valid Requests.Location request) {
        String code = request.getUnlocode().toUpperCase();

        if (locationRepository.existsByUnlocode(code)) {
            throw Exceptions.duplicate("Location", "unlocode", code);
        }

        Location location = new Location(code, request.getCity(), request.getCountry());
        locationRepository.save(location);
        return Responses.Location.from(location);
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public List<Responses.Location> getAllLocations() {
        return locationRepository.findAll()
                .stream()
                .map(Responses.Location::from)
                .collect(Collectors.toList());
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public List<Responses.Location> searchLocations(@NotBlank String term) {
        return locationRepository.search(term)
                .stream()
                .map(Responses.Location::from)
                .collect(Collectors.toList());
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public Responses.Location findByUnlocode(@NotBlank String unlocode) {
        return locationRepository
                .findByUnlocode(unlocode.toUpperCase())
                .map(Responses.Location::from)
                .orElseThrow(() -> Exceptions.notFound("Location", unlocode));
    }
}