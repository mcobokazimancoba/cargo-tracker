package com.cargotracker.service;

import com.cargotracker.entity.Cargo;
import com.cargotracker.repository.CargoRepository;
import com.cargotracker.repository.LocationRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Produces operational analytics and reporting data for the dashboard.
 *
 * <p>Design note — why a separate service?
 * Analytics queries are read-only, often aggregate across multiple entities,
 * and have different performance characteristics than transactional operations.
 * Keeping them in a dedicated service means:
 * <ul>
 *   <li>CargoService stays focused on the cargo lifecycle</li>
 *   <li>Analytics queries can be optimised independently (caching, read replicas)</li>
 *   <li>The dashboard resource has one clear dependency to inject</li>
 * </ul>
 *
 * <p>All methods here are read-only. {@code TxType.SUPPORTS} joins an existing
 * transaction if one is active, but never starts a new one — correct for
 * pure read operations where no transaction is needed.
 */
@ApplicationScoped
public class AnalyticsService {

    @Inject
    private CargoRepository cargoRepository;

    @Inject
    private LocationRepository locationRepository;

    /*
     * Some analytics queries span multiple entities and are best expressed
     * as JPQL aggregates directly. We inject the EntityManager here rather
     * than creating a dedicated repository for one-off aggregate queries.
     *
     * This is intentional pragmatism: a repository that contains only one
     * complex analytic query adds indirection without adding abstraction value.
     * Pure aggregate queries that do not map to a single entity type live here.
     */
    @PersistenceContext(unitName = "cargoTrackerPU")
    private EntityManager em;

    // ── Dashboard summary ─────────────────────────────────────────────────────

    /**
     * Returns the top-level KPIs shown on the dashboard header.
     *
     * <p>Returns a structured record so the Resource layer has a
     * type-safe object to serialise, not a raw Map.
     */
    @Transactional(Transactional.TxType.SUPPORTS)
    public DashboardSummary getDashboardSummary() {
        long totalCargos     = cargoRepository.countAll();
        long booked          = cargoRepository.countByStatus(Cargo.Status.BOOKED);
        long inTransit       = cargoRepository.countByStatus(Cargo.Status.IN_TRANSIT);
        long atDestination   = cargoRepository.countByStatus(Cargo.Status.AT_DESTINATION);
        long delivered       = cargoRepository.countByStatus(Cargo.Status.DELIVERED);
        long cancelled       = cargoRepository.countByStatus(Cargo.Status.CANCELLED);

        BigDecimal totalWeightKg = getTotalWeightInTransit();

        return new DashboardSummary(
                totalCargos, booked, inTransit, atDestination,
                delivered, cancelled, totalWeightKg
        );
    }

    private BigDecimal getTotalWeightInTransit() {
        /*
         * COALESCE handles the case where no rows match — SUM over an empty
         * result set returns NULL in SQL, which would cause a NullPointerException
         * when unboxed to BigDecimal. COALESCE maps NULL → 0.
         */
        BigDecimal result = em.createQuery(
                "SELECT COALESCE(SUM(c.weightKg), 0) FROM Cargo c " +
                "WHERE c.status = :status",
                BigDecimal.class)
            .setParameter("status", Cargo.Status.IN_TRANSIT)
            .getSingleResult();

        return result;
    }

    // ── Status breakdown ──────────────────────────────────────────────────────

    /**
     * Returns counts per status — used to render the pie/bar chart on the dashboard.
     * LinkedHashMap preserves insertion order so the chart always renders statuses
     * in lifecycle order (BOOKED → IN_TRANSIT → ... → CANCELLED).
     */
    @Transactional(Transactional.TxType.SUPPORTS)
    public Map<String, Long> getStatusBreakdown() {
        Map<String, Long> breakdown = new LinkedHashMap<>();
        for (Cargo.Status status : Cargo.Status.values()) {
            breakdown.put(status.name(), cargoRepository.countByStatus(status));
        }
        return breakdown;
    }

    // ── Route analytics ───────────────────────────────────────────────────────

    /**
     * Returns the top 10 most active origin → destination routes by cargo count.
     *
     * <p>This query groups across Location entities and produces a projection,
     * not a single entity type — exactly the use case for direct JPQL in a service.
     */
    @Transactional(Transactional.TxType.SUPPORTS)
    public List<RouteStats> getTopRoutes() {
        /*
         * JPQL constructor expression: new RouteStats(...)
         * This tells EclipseLink to instantiate RouteStats objects directly
         * from the query result rows rather than returning Object[] arrays.
         * Much safer than casting array elements by index.
         */
        return em.createQuery(
                "SELECT new com.cargotracker.service.AnalyticsService$RouteStats(" +
                "    o.unlocode, o.city, d.unlocode, d.city, COUNT(c)" +
                ") " +
                "FROM Cargo c " +
                "JOIN c.origin o " +
                "JOIN c.destination d " +
                "GROUP BY o.unlocode, o.city, d.unlocode, d.city " +
                "ORDER BY COUNT(c) DESC",
                RouteStats.class)
            .setMaxResults(10)
            .getResultList();
    }

    // ── Volume over time ──────────────────────────────────────────────────────

    /**
     * Returns daily booking counts for the past N days.
     * Used to render the volume trend line on the dashboard.
     *
     * @param days number of days to look back (7, 30, 90)
     */
    @Transactional(Transactional.TxType.SUPPORTS)
    public List<DailyVolume> getBookingVolumeByDay(int days) {
        LocalDate since = LocalDate.now().minusDays(days);

        /*
         * FUNCTION('DATE', ...) is EclipseLink's escape hatch for calling
         * database-specific functions. DATE() truncates a timestamp to the
         * calendar date. This is PostgreSQL-compatible.
         *
         * In production you would use a named native query or a database view
         * for a query this complex. This is the JPQL equivalent for portability.
         */
        return em.createQuery(
                "SELECT new com.cargotracker.service.AnalyticsService$DailyVolume(" +
                "    FUNCTION('DATE', c.createdAt), COUNT(c)" +
                ") " +
                "FROM Cargo c " +
                "WHERE c.createdAt >= :since " +
                "GROUP BY FUNCTION('DATE', c.createdAt) " +
                "ORDER BY FUNCTION('DATE', c.createdAt) ASC",
                DailyVolume.class)
            .setParameter("since", since.atStartOfDay())
            .getResultList();
    }

    // ── Location throughput ───────────────────────────────────────────────────

    /**
     * Returns the top 10 busiest locations by total cargo passing through
     * (counted as either origin or destination).
     */
    @Transactional(Transactional.TxType.SUPPORTS)
    public List<LocationThroughput> getTopLocationsByThroughput() {
        /*
         * UNION in JPQL is not supported — this is one of the genuine limitations
         * of JPQL vs SQL. We use two queries and merge in Java.
         * For high-volume production systems this would be a native SQL query
         * or a materialised view.
         */
        List<Object[]> origins = em.createQuery(
                "SELECT l.unlocode, l.city, l.country, COUNT(c) " +
                "FROM Cargo c JOIN c.origin l " +
                "GROUP BY l.unlocode, l.city, l.country",
                Object[].class)
            .getResultList();

        List<Object[]> destinations = em.createQuery(
                "SELECT l.unlocode, l.city, l.country, COUNT(c) " +
                "FROM Cargo c JOIN c.destination l " +
                "GROUP BY l.unlocode, l.city, l.country",
                Object[].class)
            .getResultList();

        // Merge: sum counts for locations that appear in both result sets
        Map<String, LocationThroughput> merged = new LinkedHashMap<>();

        for (Object[] row : origins) {
            String unlocode = (String) row[0];
            merged.put(unlocode, new LocationThroughput(
                    unlocode, (String) row[1], (String) row[2], (Long) row[3]));
        }
        for (Object[] row : destinations) {
            String unlocode = (String) row[0];
            merged.merge(unlocode,
                    new LocationThroughput(unlocode, (String) row[1], (String) row[2], (Long) row[3]),
                    (existing, incoming) -> new LocationThroughput(
                            existing.unlocode(), existing.city(), existing.country(),
                            existing.cargoCount() + incoming.cargoCount()));
        }

        return merged.values().stream()
                .sorted((a, b) -> Long.compare(b.cargoCount(), a.cargoCount()))
                .limit(10)
                .toList();
    }

    // ── Response record types ─────────────────────────────────────────────────

    /*
     * Java records are the modern alternative to verbose POJOs for data carriers.
     * They are immutable by default, have auto-generated equals/hashCode/toString,
     * and the compact syntax makes the intent crystal clear.
     *
     * These are static nested types — they belong to AnalyticsService conceptually
     * and are not useful elsewhere in the codebase.
     *
     * The JPQL constructor expression references the fully-qualified class name,
     * which is why the $ inner-class separator appears in the JPQL strings above.
     */

    public record DashboardSummary(
            long totalCargos,
            long booked,
            long inTransit,
            long atDestination,
            long delivered,
            long cancelled,
            BigDecimal totalWeightInTransitKg
    ) {}

    public record RouteStats(
            String originUnlocode,
            String originCity,
            String destinationUnlocode,
            String destinationCity,
            long   cargoCount
    ) {}

    public record DailyVolume(
            Object date,        // String or LocalDate depending on DB function return
            long   count
    ) {}

    public record LocationThroughput(
            String unlocode,
            String city,
            String country,
            long   cargoCount
    ) {}
}