package com.cargotracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * An immutable record of something that happened to a {@link Cargo}.
 *
 * <p>TrackingEvents are created by the system (carrier updates, manual scans)
 * and are never modified after creation — they form an audit trail.
 *
 * <p>Design decision — owned entity:
 * This class has no repository of its own. It is always accessed through
 * {@code Cargo.getTrackingEvents()}. This is the aggregate root pattern:
 * the Cargo is the consistency boundary; events cannot exist without a parent cargo.
 */
@Entity
@Table(
    name = "tracking_events",
    indexes = {
        @Index(name = "idx_event_cargo",       columnList = "cargo_id"),
        @Index(name = "idx_event_occurred_at", columnList = "occurred_at"),
        @Index(name = "idx_event_type",        columnList = "event_type")
    }
)
public class TrackingEvent {

    /** What physically happened to the cargo at this point in time. */
    public enum EventType {
        PICKED_UP,
        DEPARTED,
        ARRIVED_AT_PORT,
        DELIVERED,
        EXCEPTION          // customs hold, damage, misdirection, etc.
    }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "event_seq")
    @SequenceGenerator(name = "event_seq", sequenceName = "tracking_event_id_seq", allocationSize = 100)
    private Long id;

    /*
     * The owning side of the Cargo ↔ TrackingEvent relationship.
     * The foreign key column "cargo_id" lives in this table.
     * insertable/updatable = true (defaults) — the column IS managed by JPA here.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "cargo_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_event_cargo")
    )
    private Cargo cargo;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private EventType eventType;

    /**
     * The location where this event occurred.
     * Not cascaded — locations outlive any single event.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "location_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_event_location")
    )
    private Location location;

    /** When this event physically occurred (carrier's timestamp, not our receive time). */
    @NotNull
    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    /** When our system recorded this event. Set by @PrePersist. */
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private LocalDateTime recordedAt;

    /** Free-text note — "Cleared customs", "Damaged on loading", etc. */
    @Size(max = 1000)
    @Column(name = "notes", length = 1000)
    private String notes;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @PrePersist
    private void onPrePersist() {
        this.recordedAt = LocalDateTime.now();
    }

    // ── Constructors ──────────────────────────────────────────────────────────

    protected TrackingEvent() {}

    public TrackingEvent(EventType eventType,
                         Location location,
                         LocalDateTime occurredAt,
                         String notes) {
        this.eventType   = eventType;
        this.location    = location;
        this.occurredAt  = occurredAt;
        this.notes       = notes;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public Long getId()                { return id; }
    public Cargo getCargo()            { return cargo; }
    public EventType getEventType()    { return eventType; }
    public Location getLocation()      { return location; }
    public LocalDateTime getOccurredAt()  { return occurredAt; }
    public LocalDateTime getRecordedAt()  { return recordedAt; }
    public String getNotes()           { return notes; }

    /*
     * Package-private setter — only Cargo.addTrackingEvent() calls this.
     * Callers outside the entity package cannot set the parent directly.
     */
    void setCargo(Cargo cargo) { this.cargo = cargo; }

    // ── Object contract ───────────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TrackingEvent other)) return false;
        // Surrogate key equality is safe here because events are immutable
        // after persist and we never compare transient events for equality.
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id == null ? System.identityHashCode(this) : id.hashCode();
    }

    @Override
    public String toString() {
        return "TrackingEvent{id=" + id + ", type=" + eventType + ", occurredAt=" + occurredAt + "}";
    }
}