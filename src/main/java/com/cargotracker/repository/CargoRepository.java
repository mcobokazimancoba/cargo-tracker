package com.cargotracker.repository;

import com.cargotracker.entity.Cargo;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.*;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class CargoRepository {

    @PersistenceContext(unitName = "cargoTrackerPU")
    private EntityManager em;

    // ─────────────────────────────────────────────
    // WRITE OPERATIONS
    // ─────────────────────────────────────────────

    public Cargo save(Cargo cargo) {
        if (cargo.getId() == null) {
            em.persist(cargo);
            return cargo;
        } else {
            return em.merge(cargo);
        }
    }

    public void delete(Cargo cargo) {
        em.remove(em.contains(cargo) ? cargo : em.merge(cargo));
    }

    // ─────────────────────────────────────────────
    // READ OPERATIONS (SINGLE)
    // ─────────────────────────────────────────────

    public Optional<Cargo> findById(Long id) {
        return Optional.ofNullable(em.find(Cargo.class, id));
    }

    public Optional<Cargo> findByTrackingNumber(String trackingNumber) {
        return getSingleResult(
                "SELECT c FROM Cargo c WHERE c.trackingNumber = :tn",
                "tn", trackingNumber
        );
    }

    public Optional<Cargo> findByTrackingNumberWithDetails(String trackingNumber) {
        return getSingleResult(
                "SELECT DISTINCT c FROM Cargo c " +
                "JOIN FETCH c.origin " +
                "JOIN FETCH c.destination " +
                "LEFT JOIN FETCH c.trackingEvents te " +
                "LEFT JOIN FETCH te.location " +
                "WHERE c.trackingNumber = :tn",
                "tn", trackingNumber
        );
    }

    // ─────────────────────────────────────────────
    // READ OPERATIONS (LIST / PAGINATION)
    // ─────────────────────────────────────────────

    public List<Cargo> findAll(int page, int size) {
        return paginate(
                "SELECT c FROM Cargo c ORDER BY c.createdAt DESC",
                page, size
        );
    }

    public List<Cargo> findByCustomerUsername(String username, int page, int size) {
        return paginate(
                "SELECT c FROM Cargo c WHERE c.customerUsername = :username ORDER BY c.createdAt DESC",
                page, size,
                "username", username
        );
    }

    public List<Cargo> findByStatus(Cargo.Status status, int page, int size) {
        return paginate(
                "SELECT c FROM Cargo c WHERE c.status = :status ORDER BY c.createdAt DESC",
                page, size,
                "status", status
        );
    }

    public List<Cargo> findByStatusAndCustomer(Cargo.Status status, String username, int page, int size) {
        return paginate(
                "SELECT c FROM Cargo c WHERE c.status = :status AND c.customerUsername = :username ORDER BY c.createdAt DESC",
                page, size,
                "status", status,
                "username", username
        );
    }

    public List<Cargo> findByOriginId(Long locationId, int page, int size) {
        return paginate(
                "SELECT c FROM Cargo c WHERE c.origin.id = :locId ORDER BY c.createdAt DESC",
                page, size,
                "locId", locationId
        );
    }

    // ─────────────────────────────────────────────
    // COUNT OPERATIONS
    // ─────────────────────────────────────────────

    public long countAll() {
        return em.createQuery("SELECT COUNT(c) FROM Cargo c", Long.class)
                .getSingleResult();
    }

    public long countByCustomerUsername(String username) {
        return countQuery(
                "SELECT COUNT(c) FROM Cargo c WHERE c.customerUsername = :username",
                "username", username
        );
    }

    public long countByStatus(Cargo.Status status) {
        return countQuery(
                "SELECT COUNT(c) FROM Cargo c WHERE c.status = :status",
                "status", status
        );
    }

    public long countByStatusAndCustomer(Cargo.Status status, String username) {
        return countQuery(
                "SELECT COUNT(c) FROM Cargo c WHERE c.status = :status AND c.customerUsername = :username",
                "status", status,
                "username", username
        );
    }

    // ─────────────────────────────────────────────
    // UTILITY METHODS
    // ─────────────────────────────────────────────

    public boolean existsByTrackingNumber(String trackingNumber) {
        Long count = countQuery(
                "SELECT COUNT(c) FROM Cargo c WHERE c.trackingNumber = :tn",
                "tn", trackingNumber
        );
        return count > 0;
    }

    // ─────────────────────────────────────────────
    // INTERNAL HELPERS (CLEAN ABSTRACTION)
    // ─────────────────────────────────────────────

    private Optional<Cargo> getSingleResult(String jpql, Object... params) {
        try {
            TypedQuery<Cargo> query = em.createQuery(jpql, Cargo.class);
            setParameters(query, params);
            return Optional.of(query.getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    private List<Cargo> paginate(String jpql, int page, int size, Object... params) {
        TypedQuery<Cargo> query = em.createQuery(jpql, Cargo.class);
        setParameters(query, params);

        return query
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();
    }

    private long countQuery(String jpql, Object... params) {
        TypedQuery<Long> query = em.createQuery(jpql, Long.class);
        setParameters(query, params);
        return query.getSingleResult();
    }

    private void setParameters(Query query, Object... params) {
        for (int i = 0; i < params.length; i += 2) {
            query.setParameter((String) params[i], params[i + 1]);
        }
    }
}
