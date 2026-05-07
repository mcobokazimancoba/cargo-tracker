package com.cargotracker.dto.response;

import com.cargotracker.entity.Cargo;
import java.util.List;
import java.util.stream.Collectors;

public class CargoDetailResponse extends CargoSummaryResponse {
    private final List<TrackingEventResponse> trackingEvents;

    public CargoDetailResponse(CargoSummaryResponse base, List<TrackingEventResponse> events) {
        super(base.getId(), base.getTrackingNumber(), base.getOrigin(),
              base.getDestination(), base.getDescription(), base.getWeightKg(),
              base.getStatus(), base.getExpectedArrival(), base.getCreatedAt());
        this.trackingEvents = List.copyOf(events);
    }

    public static CargoDetailResponse from(Cargo c) {
        CargoSummaryResponse base = CargoSummaryResponse.from(c);
        List<TrackingEventResponse> events = c.getTrackingEvents().stream()
            .map(TrackingEventResponse::from)
            .collect(Collectors.toList());
        return new CargoDetailResponse(base, events);
    }

    public List<TrackingEventResponse> getTrackingEvents() { return trackingEvents; }
}