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

    /**
     * Standard error body. Matches the shape produced by the JAX-RS exception
     * mappers in {@link com.cargotracker.exception.Exceptions} so every non-2xx
     * response — whether thrown by the service layer or built manually by a
     * resource — looks the same to clients.
     *
     * History note: this class previously had a constructor that accepted three
     * args and stored none of them, so manually built error responses (e.g. the
     * 429 in AuthResource and the unauthorized/forbidden helpers in
     * CargoResource) emitted empty {} bodies. Fixed here.
     */
    public static class Error {
        private int    status;
        private String error;
        private String message;

        public Error() {}                                        // for JSON-B

        public Error(int status, String error, String message) {
            this.status  = status;
            this.error   = error;
            this.message = message;
        }

        public int    getStatus()  { return status; }
        public String getError()   { return error; }
        public String getMessage() { return message; }

        public void setStatus(int status)        { this.status = status; }
        public void setError(String error)       { this.error = error; }
        public void setMessage(String message)   { this.message = message; }
    }
}