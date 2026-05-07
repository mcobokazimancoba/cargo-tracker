package com.cargotracker.service;

import com.cargotracker.entity.AppUser;
import com.cargotracker.entity.Cargo;
import com.cargotracker.entity.Location;
import com.cargotracker.exception.AppException;
import com.cargotracker.repository.CargoRepository;
import com.cargotracker.repository.LocationRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Tests for the protected-endpoint authorization rule on
 * {@link CargoService#findByTrackingNumberAuthorized(String, AppUser)}.
 *
 * <p>This is the rule the user-spec for step 3 demanded: customers can
 * only read their own shipments. The matrix:
 *
 * <pre>
 *   guest (caller == null)         → allowed (preserves public guest tracking)
 *   OPERATOR / ADMIN               → allowed (any cargo)
 *   CUSTOMER who owns the cargo    → allowed
 *   CUSTOMER who does not own it   → 403
 * </pre>
 */
@ExtendWith(MockitoExtension.class)
class CargoServiceAuthorizationTest {

    @Mock private CargoRepository cargoRepository;
    @Mock private LocationRepository locationRepository;     // unused but injected by the service

    @InjectMocks
    private CargoService cargoService;

    private Cargo aliceCargo;

    @BeforeEach
    void setUp() {
        // Build a cargo owned by "alice".
        Location origin      = new Location("ZAJNB", "Johannesburg", "South Africa");
        Location destination = new Location("NLRTM", "Rotterdam", "Netherlands");

        aliceCargo = new Cargo(
                "CGO-AAAAAAAAAA",
                origin,
                destination,
                "Furniture",
                new BigDecimal("250.0"),
                LocalDate.now().plusDays(7));
        aliceCargo.setCustomerUsername("alice");

        when(cargoRepository.findByTrackingNumberWithDetails("CGO-AAAAAAAAAA"))
                .thenReturn(Optional.of(aliceCargo));
    }

    @Test
    @DisplayName("guest (no caller) sees the cargo — public tracking link must keep working")
    void guestAllowed() {
        assertDoesNotThrow(() ->
                cargoService.findByTrackingNumberAuthorized("CGO-AAAAAAAAAA", null));
    }

    @Test
    @DisplayName("OPERATOR sees any cargo — they manage shipments across customers")
    void operatorAllowed() {
        AppUser operator = new AppUser(
                "ops", "ops@example.com", "hash", "Operator", AppUser.Role.OPERATOR);

        assertNotNull(cargoService.findByTrackingNumberAuthorized("CGO-AAAAAAAAAA", operator));
    }

    @Test
    @DisplayName("ADMIN sees any cargo")
    void adminAllowed() {
        AppUser admin = new AppUser(
                "boss", "boss@example.com", "hash", "Admin", AppUser.Role.ADMIN);

        assertNotNull(cargoService.findByTrackingNumberAuthorized("CGO-AAAAAAAAAA", admin));
    }

    @Test
    @DisplayName("CUSTOMER who owns the cargo sees it")
    void ownerCustomerAllowed() {
        AppUser alice = new AppUser(
                "alice", "alice@example.com", "hash", "Alice", AppUser.Role.CUSTOMER);

        assertNotNull(cargoService.findByTrackingNumberAuthorized("CGO-AAAAAAAAAA", alice));
    }

    @Test
    @DisplayName("CUSTOMER who does NOT own the cargo gets 403 — the actual rule under test")
    void nonOwnerCustomerForbidden() {
        AppUser bob = new AppUser(
                "bob", "bob@example.com", "hash", "Bob", AppUser.Role.CUSTOMER);

        AppException ex = assertThrows(AppException.class, () ->
                cargoService.findByTrackingNumberAuthorized("CGO-AAAAAAAAAA", bob));

        assertEquals(403, ex.getHttpStatusCode());
        assertEquals("You can only view your own shipments", ex.getMessage());
    }
}
