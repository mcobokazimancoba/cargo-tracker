package com.cargotracker.repository;

import com.cargotracker.entity.AppUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;

import java.util.List;
import java.util.Optional;

/**
 * Data access layer for {@link AppUser} entities.
 */
@ApplicationScoped
public class AppUserRepository {

    @PersistenceContext(unitName = "cargoTrackerPU")
    private EntityManager em;

    public void save(AppUser user) {
        em.persist(user);
    }

    public AppUser update(AppUser user) {
        return em.merge(user);
    }

    public Optional<AppUser> findById(Long id) {
        return Optional.ofNullable(em.find(AppUser.class, id));
    }

    public Optional<AppUser> findByUsername(String username) {
        try {
            AppUser user = em.createQuery(
                    "SELECT u FROM AppUser u WHERE u.username = :username",
                    AppUser.class)
                .setParameter("username", username)
                .getSingleResult();
            return Optional.of(user);
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    /**
     * Find a user by their email address (case-insensitive).
     * Emails are stored lowercase at registration time.
     *
     * @return an Optional containing the user, or empty if not found
     */
    public Optional<AppUser> findByEmail(String email) {
        try {
            AppUser user = em.createQuery(
                    "SELECT u FROM AppUser u WHERE LOWER(u.email) = LOWER(:email)",
                    AppUser.class)
                .setParameter("email", email)
                .getSingleResult();
            return Optional.of(user);
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    /** Used by AuthFilter to validate a token subject against an active account. */
    public Optional<AppUser> findActiveByUsername(String username) {
        try {
            AppUser user = em.createQuery(
                    "SELECT u FROM AppUser u WHERE u.username = :username AND u.active = true",
                    AppUser.class)
                .setParameter("username", username)
                .getSingleResult();
            return Optional.of(user);
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    public List<AppUser> findAll(int page, int size) {
        return em.createQuery("SELECT u FROM AppUser u ORDER BY u.createdAt DESC", AppUser.class)
                 .setFirstResult(page * size)
                 .setMaxResults(size)
                 .getResultList();
    }

    public boolean existsByUsername(String username) {
        Long count = em.createQuery(
                "SELECT COUNT(u) FROM AppUser u WHERE u.username = :username", Long.class)
            .setParameter("username", username)
            .getSingleResult();
        return count > 0;
    }

    public boolean existsByEmail(String email) {
        Long count = em.createQuery(
                "SELECT COUNT(u) FROM AppUser u WHERE u.email = :email", Long.class)
            .setParameter("email", email.toLowerCase())
            .getSingleResult();
        return count > 0;
    }
}