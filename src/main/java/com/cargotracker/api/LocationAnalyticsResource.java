package com.cargotracker.api;

import com.cargotracker.dto.request.Requests;
import com.cargotracker.dto.response.Responses;
import com.cargotracker.service.AnalyticsService;
import com.cargotracker.service.LocationService;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.core.Context;

import java.util.List;

// ─────────────────────────────────────────────────────────────────────────────
//  Location resource
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Manages port and depot locations.
 *
 * <p>GET endpoints are public — accessible without authentication.
 * This is intentional: the booking form's autocomplete needs location data
 * before the user is logged in.
 *
 * <p>POST is restricted to ADMIN by RoleFilter — locations are system data,
 * not user-created content.
 */
@Path("locations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class LocationResource {

    @Inject
    private LocationService locationService;

    /**
     * GET /api/locations
     * GET /api/locations?search=johannesburg
     *
     * Returns all locations, or filters by search term when provided.
     * Used by the booking form autocomplete and the admin location list.
     */
    @GET
    public Response getLocations(@QueryParam("search") String search) {
        List<Responses.Location> result = (search != null && !search.isBlank())
                ? locationService.searchLocations(search)
                : locationService.getAllLocations();

        return Response.ok(result).build();
    }

    /**
     * POST /api/locations — create a new port/depot.
     * Requires ADMIN role (enforced by RoleFilter).
     */
    @POST
    public Response createLocation(
            @Valid Requests.Location request,
            @Context UriInfo uriInfo) {

        Responses.Location created = locationService.createLocation(request);

        return Response
                .status(Response.Status.CREATED)
                .entity(created)
                .location(uriInfo.getAbsolutePathBuilder()
                        .path(created.getUnlocode())
                        .build())
                .build();
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Analytics resource
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Provides operational analytics for the dashboard.
 * All endpoints require OPERATOR role minimum (enforced by RoleFilter).
 */
@Path("analytics")
@Produces(MediaType.APPLICATION_JSON)
class AnalyticsResource {

    @Inject
    private AnalyticsService analyticsService;

    /**
     * GET /api/analytics/summary
     * Returns the top-level KPI cards: total, by-status counts, weight in transit.
     */
    @GET
    @Path("summary")
    public Response getSummary() {
        return Response.ok(analyticsService.getDashboardSummary()).build();
    }

    /**
     * GET /api/analytics/status-breakdown
     * Returns counts per status for chart rendering.
     */
    @GET
    @Path("status-breakdown")
    public Response getStatusBreakdown() {
        return Response.ok(analyticsService.getStatusBreakdown()).build();
    }

    /**
     * GET /api/analytics/top-routes
     * Returns the 10 most active origin → destination pairs.
     */
    @GET
    @Path("top-routes")
    public Response getTopRoutes() {
        return Response.ok(analyticsService.getTopRoutes()).build();
    }

    /**
     * GET /api/analytics/volume?days=30
     * Returns daily booking counts for the past N days (default 30).
     */
    @GET
    @Path("volume")
    public Response getVolume(
            @QueryParam("days") @jakarta.validation.constraints.Min(1)
            @jakarta.validation.constraints.Max(365)
            @jakarta.ws.rs.DefaultValue("30") int days) {

        return Response.ok(analyticsService.getBookingVolumeByDay(days)).build();
    }

    /**
     * GET /api/analytics/top-locations
     * Returns the 10 busiest ports/depots by total cargo throughput.
     */
    @GET
    @Path("top-locations")
    public Response getTopLocations() {
        return Response.ok(analyticsService.getTopLocationsByThroughput()).build();
    }
}