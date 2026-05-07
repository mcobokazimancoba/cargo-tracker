package com.cargotracker.service;

import com.cargotracker.dto.request.Requests;
import com.cargotracker.dto.response.Responses;
import com.cargotracker.entity.Cargo;
import com.cargotracker.entity.Location;
import com.cargotracker.entity.TrackingEvent;
import com.cargotracker.exception.Exceptions;
import com.cargotracker.repository.CargoRepository;
import com.cargotracker.repository.LocationRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.security.SecureRandom;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class CargoService {

    @Inject
    private CargoRepository cargoRepository;

    @Inject
    private LocationRepository locationRepository;

    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private String generateTrackingNumber() {
        String candidate;
        int attempts = 0;
        do {
            if (++attempts > 10) {
                throw Exceptions.businessRule("Could not generate a unique tracking number — try again");
            }
            StringBuilder sb = new StringBuilder("CGO-");
            for (int i = 0; i < 10; i++) {
                sb.append(ALPHANUMERIC.charAt(SECURE_RANDOM.nextInt(ALPHANUMERIC.length())));
            }
            candidate = sb.toString();
        } while (cargoRepository.existsByTrackingNumber(candidate));
        return candidate;
    }

    // ── book ──────────────────────────────────────────────────────────────────

    @Transactional
    public Responses.CargoSummary book(@NotNull @Valid Requests.Booking request,
                                       String bookedByUsername) {

        if (request.getOriginUnlocode().equalsIgnoreCase(request.getDestinationUnlocode())) {
            throw Exceptions.businessRule("Origin and destination cannot be the same location");
        }

        Location origin = locationRepository
                .findByUnlocode(request.getOriginUnlocode())
                .orElseThrow(() -> Exceptions.notFound("Location", request.getOriginUnlocode()));

        Location destination = locationRepository
                .findByUnlocode(request.getDestinationUnlocode())
                .orElseThrow(() -> Exceptions.notFound("Location", request.getDestinationUnlocode()));

        Cargo cargo = new Cargo(
                generateTrackingNumber(),
                origin,
                destination,
                request.getDescription(),
                request.getWeightKg(),
                request.getExpectedArrival()
        );

        cargo.setCustomerUsername(bookedByUsername); // save who booked it

        cargoRepository.save(cargo);
        return Responses.CargoSummary.from(cargo);
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Transactional
    public Responses.CargoSummary update(
            @NotBlank String trackingNumber,
            @NotNull @Valid Requests.CargoUpdate request) {

        Cargo cargo = cargoRepository
                .findByTrackingNumber(trackingNumber)
                .orElseThrow(() -> Exceptions.notFound("Cargo", trackingNumber));

        if (cargo.getStatus() == Cargo.Status.CANCELLED) {
            throw Exceptions.invalidState(cargo.getStatus().name(), "update");
        }

        if (request.getDescription()     != null) cargo.setDescription(request.getDescription());
        if (request.getWeightKg()        != null) cargo.setWeightKg(request.getWeightKg());
        if (request.getExpectedArrival() != null) cargo.setExpectedArrival(request.getExpectedArrival());

        cargoRepository.save(cargo);
        return Responses.CargoSummary.from(cargo);
    }

    // ── cancel ────────────────────────────────────────────────────────────────

    @Transactional
    public Responses.CargoSummary cancel(@NotBlank String trackingNumber) {

        Cargo cargo = cargoRepository
                .findByTrackingNumber(trackingNumber)
                .orElseThrow(() -> Exceptions.notFound("Cargo", trackingNumber));

        if (cargo.getStatus() == Cargo.Status.DELIVERED ||
            cargo.getStatus() == Cargo.Status.CANCELLED) {
            throw Exceptions.invalidState(cargo.getStatus().name(), "cancel");
        }

        cargo.setStatus(Cargo.Status.CANCELLED);
        cargoRepository.save(cargo);
        return Responses.CargoSummary.from(cargo);
    }

    // ── deletePermanent ───────────────────────────────────────────────────────

    @Transactional
    public void deletePermanent(@NotBlank String trackingNumber) {

        Cargo cargo = cargoRepository
                .findByTrackingNumber(trackingNumber)
                .orElseThrow(() -> Exceptions.notFound("Cargo", trackingNumber));

        cargoRepository.delete(cargo);
    }

    // ── addTrackingEvent ──────────────────────────────────────────────────────

    @Transactional
    public Responses.CargoDetail addTrackingEvent(
            @NotBlank String trackingNumber,
            @NotNull @Valid Requests.TrackingEvent request) {

        Cargo cargo = cargoRepository
                .findByTrackingNumberWithDetails(trackingNumber)
                .orElseThrow(() -> Exceptions.notFound("Cargo", trackingNumber));

        if (cargo.getStatus() == Cargo.Status.DELIVERED ||
            cargo.getStatus() == Cargo.Status.CANCELLED) {
            throw Exceptions.invalidState(cargo.getStatus().name(), "add tracking event");
        }

        Location eventLocation = locationRepository
                .findByUnlocode(request.getLocationUnlocode())
                .orElseThrow(() -> Exceptions.notFound("Location", request.getLocationUnlocode()));

        if (request.getEventType() == TrackingEvent.EventType.DELIVERED &&
            !eventLocation.getUnlocode().equalsIgnoreCase(cargo.getDestination().getUnlocode())) {
            throw Exceptions.businessRule(
                "DELIVERED event must be recorded at the cargo's destination (" +
                cargo.getDestination().getUnlocode() + ")"
            );
        }

        TrackingEvent event = new TrackingEvent(
                request.getEventType(),
                eventLocation,
                request.getOccurredAt(),
                request.getNotes()
        );

        cargo.addTrackingEvent(event);
        return Responses.CargoDetail.from(cargo);
    }

    // ── findByTrackingNumber ──────────────────────────────────────────────────

    @Transactional(Transactional.TxType.SUPPORTS)
    public Responses.CargoDetail findByTrackingNumber(@NotBlank String trackingNumber) {
        Cargo cargo = cargoRepository
                .findByTrackingNumberWithDetails(trackingNumber)
                .orElseThrow(() -> Exceptions.notFound("Cargo", trackingNumber));
        return Responses.CargoDetail.from(cargo);
    }

    // ── findAll ───────────────────────────────────────────────────────────────

    @Transactional(Transactional.TxType.SUPPORTS)
    public Responses.Page<Responses.CargoSummary> findAll(
            @Min(0) int page,
            @Min(1) @Max(100) int size) {

        List<Responses.CargoSummary> content = cargoRepository
                .findAll(page, size)
                .stream()
                .map(Responses.CargoSummary::from)
                .collect(Collectors.toList());

        return new Responses.Page<>(content, page, size, cargoRepository.countAll());
    }

    // ── findByBookedBy (any role — own shipments only) ────────────────────────

    @Transactional(Transactional.TxType.SUPPORTS)
    public Responses.Page<Responses.CargoSummary> findByBookedBy(
            @NotBlank String username,
            @Min(0) int page,
            @Min(1) @Max(100) int size) {

        List<Responses.CargoSummary> content = cargoRepository
                .findByCustomerUsername(username, page, size)
                .stream()
                .map(Responses.CargoSummary::from)
                .collect(Collectors.toList());

        return new Responses.Page<>(content, page, size,
                cargoRepository.countByCustomerUsername(username));
    }

    // ── findByStatus ──────────────────────────────────────────────────────────

    @Transactional(Transactional.TxType.SUPPORTS)
    public Responses.Page<Responses.CargoSummary> findByStatus(
            @NotNull Cargo.Status status,
            @Min(0) int page,
            @Min(1) @Max(100) int size) {

        List<Responses.CargoSummary> content = cargoRepository
                .findByStatus(status, page, size)
                .stream()
                .map(Responses.CargoSummary::from)
                .collect(Collectors.toList());

        return new Responses.Page<>(content, page, size, cargoRepository.countByStatus(status));
    }
}