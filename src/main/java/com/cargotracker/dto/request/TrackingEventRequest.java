package com.cargotracker.dto.request;

import com.cargotracker.entity.Location;
import com.cargotracker.entity.TrackingEvent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public class TrackingEventRequest {

    @NotNull(message = "Event type is required")
    private TrackingEvent.EventType eventType;

    @NotBlank
    @Pattern(regexp = Location.UNLOCODE_PATTERN,
             message = "UN/LOCODE must be 5 uppercase chars: 2 country letters + 3 alphanumeric")
    private String locationUnlocode;

    // @PastOrPresent: a tracking event records something that ALREADY happened
    // (the cargo was picked up, departed, arrived, etc.). A future timestamp
    // would corrupt the audit trail and let an operator pre-date events.
    @NotNull(message = "Occurred-at timestamp is required")
    @PastOrPresent(message = "Tracking events cannot be in the future")
    private LocalDateTime occurredAt;

    @Size(max = 1000)
    private String notes;

    public TrackingEventRequest() {}

    public TrackingEvent.EventType getEventType() { return eventType; }
    public String getLocationUnlocode() { return locationUnlocode; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public String getNotes() { return notes; }

    public void setEventType(TrackingEvent.EventType eventType) { this.eventType = eventType; }
    public void setLocationUnlocode(String locationUnlocode) { this.locationUnlocode = locationUnlocode; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
    public void setNotes(String notes) { this.notes = notes; }
}