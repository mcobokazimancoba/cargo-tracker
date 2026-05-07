package com.cargotracker.repository;

import com.cargotracker.entity.Location;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;

import java.util.List;
import java.util.Optional;

/**
 * Data access layer for {@link Location} entities.
 */
@ApplicationScoped
public class LocationRepository {

    @PersistenceContext(unitName = "cargoTrackerPU")
    private EntityManager em;

    public void save(Location location) {
        em.persist(location);
    }

    public Location update(Location location) {
        return em.merge(location);
    }

    public Optional<Location> findById(Long id) {
        return Optional.ofNullable(em.find(Location.class, id));
    }

    public Optional<Location> findByUnlocode(String unlocode) {
        try {
            Location loc = em.createQuery(
                    "SELECT l FROM Location l WHERE l.unlocode = :code",
                    Location.class)
                .setParameter("code", unlocode.toUpperCase())
                .getSingleResult();
            return Optional.of(loc);
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    public List<Location> findAll() {
        return em.createQuery(
                "SELECT l FROM Location l ORDER BY l.country, l.city",
                Location.class)
            .getResultList();
    }

    /** Case-insensitive city/country search — used for the location autocomplete UI. */
    public List<Location> search(String term) {
        String pattern = "%" + term.toLowerCase() + "%";
        return em.createQuery(
                "SELECT l FROM Location l " +
                "WHERE LOWER(l.city) LIKE :pattern " +
                "   OR LOWER(l.country) LIKE :pattern " +
                "   OR LOWER(l.unlocode) LIKE :pattern " +
                "ORDER BY l.country, l.city",
                Location.class)
            .setParameter("pattern", pattern)
            .setMaxResults(20)
            .getResultList();
    }

    public boolean existsByUnlocode(String unlocode) {
        Long count = em.createQuery(
                "SELECT COUNT(l) FROM Location l WHERE l.unlocode = :code", Long.class)
            .setParameter("code", unlocode.toUpperCase())
            .getSingleResult();
        return count > 0;
    }
}