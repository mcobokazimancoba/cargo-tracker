package com.cargotracker.repository;

import com.cargotracker.entity.PasswordResetToken;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;

import java.util.Optional;

@ApplicationScoped
public class PasswordResetTokenRepository {

    @PersistenceContext(unitName = "cargoTrackerPU")  // ← must be @PersistenceContext, NOT @Inject
    private EntityManager em;

    public void save(PasswordResetToken token) {
        em.persist(token);
    }

    public Optional<PasswordResetToken> findByToken(String token) {
        try {
            return Optional.of(
                em.createQuery(
                    "SELECT t FROM PasswordResetToken t WHERE t.token = :token",
                    PasswordResetToken.class)
                .setParameter("token", token)
                .getSingleResult()
            );
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    public void deleteAllForUser(Long userId) {
        em.createQuery(
            "DELETE FROM PasswordResetToken t WHERE t.user.id = :userId")
        .setParameter("userId", userId)
        .executeUpdate();
    }
}