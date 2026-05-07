package com.cargotracker.api;

import com.cargotracker.config.AuthFilter;
import com.cargotracker.dto.request.Requests;
import com.cargotracker.dto.response.Responses;
import com.cargotracker.entity.AppUser;
import com.cargotracker.entity.Cargo;
import com.cargotracker.service.CargoService;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/cargos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CargoResource {

    @Inject
    private CargoService cargoService;

    // ── helpers ───────────────────────────────────────────────────────────────

    private AppUser getUser(ContainerRequestContext ctx) {
        return (AppUser) ctx.getProperty(AuthFilter.AUTHENTICATED_USER);
    }

    private Response unauthorized(String message) {
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity(new Responses.Error(401, "Unauthorized", message))
                .build();
    }

    private Response forbidden(String message) {
        return Response.status(Response.Status.FORBIDDEN)
                .entity(new Responses.Error(403, "Forbidden", message))
                .build();
    }

    // ── POST /cargos — book a new shipment ────────────────────────────────────

    @POST
    public Response book(@Valid Requests.Booking request,
                         @Context ContainerRequestContext ctx) {

        AppUser caller = getUser(ctx);
        if (caller == null) return unauthorized("Authentication required");

        String bookedByUsername = caller.getUsername();
        Responses.CargoSummary summary = cargoService.book(request, bookedByUsername);
        return Response.status(Response.Status.CREATED).entity(summary).build();
    }

    // ── GET /cargos/customer/mine — customer sees their own shipments ──────────
    //
    // IMPORTANT: this @Path("/customer/mine") MUST appear BEFORE the
    // @Path("/{trackingNumber}") method below, otherwise JAX-RS will try to
    // match the literal string "customer" as a tracking-number path param.

    @GET
    @Path("/customer/mine")
    public Response getMyCargos(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("100") int size,
            @Context ContainerRequestContext ctx) {

        AppUser caller = getUser(ctx);
        if (caller == null) return unauthorized("Authentication required");

        // Any authenticated role may call this; each user only sees their own.
        Responses.Page<Responses.CargoSummary> result =
                cargoService.findByBookedBy(caller.getUsername(), page, size);

        return Response.ok(result).build();
    }

    // ── GET /cargos — list all shipments (ADMIN / OPERATOR only) ─────────────

    @GET
    public Response getAll(
            @QueryParam("page")   @DefaultValue("0")  int page,
            @QueryParam("size")   @DefaultValue("20") int size,
            @QueryParam("status") String statusParam,
            @Context ContainerRequestContext ctx) {

        AppUser caller = getUser(ctx);
        if (caller == null) return unauthorized("Authentication required");

        if (caller.getRole() == AppUser.Role.CUSTOMER) {
            return forbidden("Customers must use /cargos/customer/mine");
        }

        if (statusParam != null && !statusParam.isBlank()) {
            Cargo.Status status;
            try {
                status = Cargo.Status.valueOf(statusParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new Responses.Error(400, "Bad Request",
                                "Unknown status: " + statusParam))
                        .build();
            }
            return Response.ok(cargoService.findByStatus(status, page, size)).build();
        }

        return Response.ok(cargoService.findAll(page, size)).build();
    }

    // ── GET /cargos/{trackingNumber} — public shipment tracking ──────────────

    @GET
    @Path("/{trackingNumber}")
    public Response track(@PathParam("trackingNumber") String trackingNumber,
                          @Context ContainerRequestContext ctx) {

        // Public endpoint (AuthFilter allows unauthenticated GETs on CGO- paths).
        // We still accept an authenticated caller so customers can track their own.
        Responses.CargoDetail detail = cargoService.findByTrackingNumber(trackingNumber);
        return Response.ok(detail).build();
    }

    // ── PUT /cargos/{trackingNumber} — update cargo details ──────────────────

    @PUT
    @Path("/{trackingNumber}")
    public Response update(@PathParam("trackingNumber") String trackingNumber,
                           @Valid Requests.CargoUpdate request,
                           @Context ContainerRequestContext ctx) {

        AppUser caller = getUser(ctx);
        if (caller == null) return unauthorized("Authentication required");

        if (caller.getRole() == AppUser.Role.CUSTOMER) {
            return forbidden("Customers cannot update shipments");
        }

        Responses.CargoSummary summary = cargoService.update(trackingNumber, request);
        return Response.ok(summary).build();
    }

    // ── POST /cargos/{trackingNumber}/events — add a tracking event ───────────

    @POST
    @Path("/{trackingNumber}/events")
    public Response addEvent(@PathParam("trackingNumber") String trackingNumber,
                             @Valid Requests.TrackingEvent request,
                             @Context ContainerRequestContext ctx) {

        AppUser caller = getUser(ctx);
        if (caller == null) return unauthorized("Authentication required");

        if (caller.getRole() == AppUser.Role.CUSTOMER) {
            return forbidden("Customers cannot add tracking events");
        }

        Responses.CargoDetail detail = cargoService.addTrackingEvent(trackingNumber, request);
        return Response.ok(detail).build();
    }

    // ── DELETE /cargos/{trackingNumber} — cancel a shipment ──────────────────

    @DELETE
    @Path("/{trackingNumber}")
    public Response cancel(@PathParam("trackingNumber") String trackingNumber,
                           @Context ContainerRequestContext ctx) {

        AppUser caller = getUser(ctx);
        if (caller == null) return unauthorized("Authentication required");

        if (caller.getRole() == AppUser.Role.CUSTOMER) {
            return forbidden("Customers cannot cancel shipments");
        }

        Responses.CargoSummary summary = cargoService.cancel(trackingNumber);
        return Response.ok(summary).build();
    }

    // ── DELETE /cargos/{trackingNumber}/permanent — hard delete (ADMIN only) ──

    @DELETE
    @Path("/{trackingNumber}/permanent")
    public Response deletePermanent(@PathParam("trackingNumber") String trackingNumber,
                                    @Context ContainerRequestContext ctx) {

        AppUser caller = getUser(ctx);
        if (caller == null) return unauthorized("Authentication required");

        if (caller.getRole() != AppUser.Role.ADMIN) {
            return forbidden("Only admins can permanently delete shipments");
        }

        cargoService.deletePermanent(trackingNumber);
        return Response.noContent().build();
    }
}