package com.cargotracker.dto.request;

import com.cargotracker.entity.TrackingEvent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public class TrackingEventRequest {

    @NotNull(message = "Event type is required")
    private TrackingEvent.EventType eventType;

    @NotBlank
    @Size(min = 5, max = 5)
    private String locationUnlocode;

    @NotNull(message = "Occurred-at timestamp is required")
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