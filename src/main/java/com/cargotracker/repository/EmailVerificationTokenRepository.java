package com.cargotracker.repository;

import com.cargotracker.entity.EmailVerificationToken;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;

import java.util.Optional;

@ApplicationScoped
public class EmailVerificationTokenRepository {

    @PersistenceContext(unitName = "cargoTrackerPU")
    private EntityManager em;

    public void save(EmailVerificationToken token) {
        em.persist(token);
    }

    public Optional<EmailVerificationToken> findByToken(String token) {
        try {
            return Optional.of(
                em.createQuery(
                    "SELECT t FROM EmailVerificationToken t WHERE t.token = :token",
                    EmailVerificationToken.class)
                .setParameter("token", token)
                .getSingleResult()
            );
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    public void deleteAllForUser(Long userId) {
        em.createQuery(
            "DELETE FROM EmailVerificationToken t WHERE t.user.id = :userId")
        .setParameter("userId", userId)
        .executeUpdate();
    }
}
