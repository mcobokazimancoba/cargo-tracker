package com.cargotracker.repository;

import com.cargotracker.entity.TrackingEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.util.List;

@ApplicationScoped
public class TrackingEventRepository {

    @PersistenceContext(unitName = "cargoTrackerPU")
    private EntityManager em;

    @Transactional
    public void save(TrackingEvent event) {
        em.persist(event);
    }

    public List<TrackingEvent> findByCargoId(Long cargoId) {
        return em.createQuery(
                "SELECT e FROM TrackingEvent e WHERE e.cargo.id = :cargoId ORDER BY e.occurredAt DESC",
                TrackingEvent.class)
                .setParameter("cargoId", cargoId)
                .getResultList();
    }
}