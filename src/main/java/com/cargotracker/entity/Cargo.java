package com.cargotracker.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "cargos")
public class Cargo {

    /**
     * Format of every cargo's public tracking number.
     * Defined here as a compile-time constant so DTOs and resource params can
     * reference it from {@code @Pattern(regexp = Cargo.TRACKING_NUMBER_PATTERN)}
     * without copy-pasting the regex in five places.
     *   "CGO-" + 10 chars from [A-Z0-9]
     */
    public static final String TRACKING_NUMBER_PATTERN = "CGO-[A-Z0-9]{10}";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tracking_number", nullable = false, unique = true, length = 20)
    private String trackingNumber;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "weight_kg", precision = 10, scale = 3)
    private BigDecimal weightKg;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private Status status = Status.BOOKED;

    @ManyToOne
    @JoinColumn(name = "origin_id")
    private Location origin;

    @ManyToOne
    @JoinColumn(name = "destination_id")
    private Location destination;

    @Column(name = "customer_username", length = 50)
    private String customerUsername;

    @Column(name = "expected_arrival")
    private LocalDate expectedArrival;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "cargo", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TrackingEvent> trackingEvents = new ArrayList<>();

    public enum Status {
        BOOKED, IN_TRANSIT, AT_DESTINATION, DELIVERED, CANCELLED
    }

    public Cargo() {
        this.createdAt = LocalDateTime.now();
    }

    public Cargo(String trackingNumber, Location origin, Location destination, 
                 String description, BigDecimal weightKg, LocalDate expectedArrival) {
        this();
        this.trackingNumber = trackingNumber;
        this.origin = origin;
        this.destination = destination;
        this.description = description;
        this.weightKg = weightKg;
        this.expectedArrival = expectedArrival;
        this.status = Status.BOOKED;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void addTrackingEvent(TrackingEvent event) {
        event.setCargo(this);
        this.trackingEvents.add(event);
        advanceStatus(event.getEventType());
        this.updatedAt = LocalDateTime.now();
    }

    private void advanceStatus(TrackingEvent.EventType eventType) {
        switch (eventType) {
            case PICKED_UP: case DEPARTED:
                this.status = Status.IN_TRANSIT;
                break;
            case ARRIVED_AT_PORT:
                this.status = Status.AT_DESTINATION;
                break;
            case DELIVERED:
                this.status = Status.DELIVERED;
                break;
            default: break;
        }
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getWeightKg() { return weightKg; }
    public void setWeightKg(BigDecimal weightKg) { this.weightKg = weightKg; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public Location getOrigin() { return origin; }
    public void setOrigin(Location origin) { this.origin = origin; }

    public Location getDestination() { return destination; }
    public void setDestination(Location destination) { this.destination = destination; }

    public String getCustomerUsername() { return customerUsername; }
    public void setCustomerUsername(String customerUsername) { this.customerUsername = customerUsername; }

    public LocalDate getExpectedArrival() { return expectedArrival; }
    public void setExpectedArrival(LocalDate expectedArrival) { this.expectedArrival = expectedArrival; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public List<TrackingEvent> getTrackingEvents() { return Collections.unmodifiableList(trackingEvents); }
}