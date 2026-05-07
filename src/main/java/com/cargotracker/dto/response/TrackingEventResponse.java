package com.cargotracker.dto.response;

import com.cargotracker.entity.TrackingEvent;
import java.time.LocalDateTime;

public class TrackingEventResponse {
    private final Long id;
    private final String eventType;
    private final LocationResponse location;
    private final LocalDateTime occurredAt;
    private final LocalDateTime recordedAt;
    private final String notes;

    public TrackingEventResponse(Long id, String eventType, LocationResponse location,
                                 LocalDateTime occurredAt, LocalDateTime recordedAt, String notes) {
        this.id = id;
        this.eventType = eventType;
        this.location = location;
        this.occurredAt = occurredAt;
        this.recordedAt = recordedAt;
        this.notes = notes;
    }

    public static TrackingEventResponse from(TrackingEvent e) {
        return new TrackingEventResponse(
            e.getId(),
            e.getEventType().name(),
            LocationResponse.from(e.getLocation()),
            e.getOccurredAt(),
            e.getRecordedAt(),
            e.getNotes()
        );
    }

    public Long getId() { return id; }
    public String getEventType() { return eventType; }
    public LocationResponse getLocation() { return location; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public LocalDateTime getRecordedAt() { return recordedAt; }
    public String getNotes() { return notes; }
}