package com.cargotracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a physical port or depot in the cargo network.
 *
 * <p>Professional notes on design decisions made here:
 *
 * <ul>
 *   <li>{@code @SequenceGenerator} — PostgreSQL sequences are more performant than
 *       IDENTITY columns under concurrent insert load. The sequence is defined in the
 *       database and EclipseLink calls {@code NEXTVAL} before the INSERT, which allows
 *       batch inserts without round-tripping per row.</li>
 *   <li>{@code @Column(nullable = false)} — enforced at the database level, not just
 *       Bean Validation. Both layers must agree; the DB constraint is the safety net.</li>
 *   <li>{@code mappedBy} on {@code @OneToMany} — the {@code Cargo} side owns the foreign
 *       key column. The Location entity is the inverse side; it does not control the join.</li>
 * </ul>
 */
@Entity
@Table(
    name = "locations",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_location_code",
        columnNames = "unlocode"
    )
)
public class Location {

    /**
     * UN/LOCODE format: 2-letter ISO country code followed by 3 alphanumeric
     * location characters, all uppercase (e.g. "ZAJNB" for Johannesburg).
     * Centralised here so every DTO and resource references one definition.
     */
    public static final String UNLOCODE_PATTERN = "[A-Z]{2}[A-Z0-9]{3}";

    /*
     * SEQUENCE strategy vs IDENTITY:
     *   IDENTITY  →  DB generates the ID after INSERT  →  Hibernate/EclipseLink
     *                cannot batch INSERTs because it needs the ID immediately.
     *   SEQUENCE  →  JPA fetches the next value BEFORE INSERT  →  batching works.
     *   Rule of thumb: always use SEQUENCE on PostgreSQL for production systems.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "location_seq")
    @SequenceGenerator(name = "location_seq", sequenceName = "location_id_seq",
                       allocationSize = 50)
    private Long id;

    /**
     * UN/LOCODE — the international 5-character port code (e.g. "ZAJNB" for
     * Johannesburg). Unique, immutable once assigned.
     */
    @NotBlank
    @Size(min = 5, max = 5)
    @Column(name = "unlocode", nullable = false, length = 5)
    private String unlocode;

    @NotBlank
    @Size(max = 100)
    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @NotBlank
    @Size(max = 100)
    @Column(name = "country", nullable = false, length = 100)
    private String country;

    /*
     * mappedBy = "origin" means: the foreign key column lives on the Cargo table
     * (in the field named "origin"), not here. This side is read-only from a
     * schema perspective.
     *
     * cascade = {} (empty, default) — we never want deleting a Location to
     * cascade-delete all cargo that ever shipped through it.
     *
     * orphanRemoval is deliberately absent for the same reason.
     */
    @OneToMany(mappedBy = "origin")
    private List<Cargo> cargosOriginating = new ArrayList<>();

    @OneToMany(mappedBy = "destination")
    private List<Cargo> cargosDestined = new ArrayList<>();

    // ── Constructors ──────────────────────────────────────────────────────────

    /** JPA requires a no-arg constructor. Keep it protected to discourage direct use. */
    protected Location() {}

    public Location(String unlocode, String city, String country) {
        this.unlocode = unlocode;
        this.city     = city;
        this.country  = country;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public Long getId()        { return id; }
    public String getUnlocode(){ return unlocode; }
    public String getCity()    { return city; }
    public String getCountry() { return country; }

    public void setCity(String city)       { this.city = city; }
    public void setCountry(String country) { this.country = country; }

    public List<Cargo> getCargosOriginating() { return cargosOriginating; }
    public List<Cargo> getCargosDestined()    { return cargosDestined; }

    // ── Object contract ───────────────────────────────────────────────────────

    /*
     * equals/hashCode based on business key (unlocode), NOT on id.
     *
     * Why: JPA entities cycle through states — transient (no id), managed,
     * detached. If you base equals on id, two new (transient) Location objects
     * with the same unlocode appear unequal until persisted. That breaks Sets
     * and causes duplicate-insert bugs. Business keys are stable across states.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Location other)) return false;
        return unlocode != null && unlocode.equals(other.unlocode);
    }

    @Override
    public int hashCode() {
        return unlocode == null ? 0 : unlocode.hashCode();
    }

    @Override
    public String toString() {
        return "Location{id=" + id + ", unlocode='" + unlocode + "', city='" + city + "'}";
    }
}