package com.cargotracker.dto.response;

/**
 * Single public access point for all response DTOs.
 */
public final class Responses {

    private Responses() {}

    public static final class Location extends LocationResponse {
        public Location(Long id, String unlocode, String city, String country) {
            super(id, unlocode, city, country);
        }
        public static Location from(com.cargotracker.entity.Location loc) {
            return new Location(loc.getId(), loc.getUnlocode(), loc.getCity(), loc.getCountry());
        }
    }

    public static final class CargoSummary extends CargoSummaryResponse {
        public CargoSummary(CargoSummaryResponse base) {
            super(base.getId(), base.getTrackingNumber(), base.getOrigin(),
                  base.getDestination(), base.getDescription(), base.getWeightKg(),
                  base.getStatus(), base.getExpectedArrival(), base.getCreatedAt());
        }
        public static CargoSummary from(com.cargotracker.entity.Cargo c) {
            return new CargoSummary(CargoSummaryResponse.from(c));
        }
    }

    public static final class CargoDetail extends CargoDetailResponse {

        public Object customerName;
        public CargoDetail(CargoDetailResponse base) {
            super(base, base.getTrackingEvents());
        }
        public static CargoDetail from(com.cargotracker.entity.Cargo c) {
            return new CargoDetail(CargoDetailResponse.from(c));
        }
    }

    public static final class Auth extends AuthResponse {
        public Auth(String token, String username, String role, long expiresInSeconds) {
            super(token, username, role, expiresInSeconds);
        }
    }

    public static final class User extends UserResponse {
        public User(UserResponse base) {
            super(base.getId(), base.getUsername(), base.getEmail(),
                  base.getFullName(), base.getRole(), base.isActive(), base.getCreatedAt());
        }
        public static User from(com.cargotracker.entity.AppUser u) {
            return new User(UserResponse.from(u));
        }
    }

    public static final class Page<T> extends PageResponse<T> {
        public Page(java.util.List<T> content, int page, int size, long totalElements) {
            super(content, page, size, totalElements);
        }
    }

    public static class Error {

        public Error(int i, String unauthorized, String message) {
        }
    }
}