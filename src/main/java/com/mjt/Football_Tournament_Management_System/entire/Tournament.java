package com.mjt.Football_Tournament_Management_System.entire;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name="tournaments")
public class Tournament {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String location;

    /** Registration period: informative only (shown to the public); the status decides who can register. */
    private LocalDate registrationStart;
    private LocalDate registrationEnd;

    private LocalDate matchStart;
    private LocalDate matchEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.DRAFT;

    // Year it belongs to and whether it is the Apertura or the Clausura (both optional)
    @ManyToOne
    private Season season;

    @Enumerated(EnumType.STRING)
    private Stage stage;

    /** Read-only: how many categories it has (a single one is not named in labels, only its zones) */
    @org.hibernate.annotations.Formula("(select count(*) from categories c where c.tournament_id = id)")
    private int categoryCount;

    /**
     * Moved by hand by the admin: DRAFT -> OPEN (captains can register) -> LIVE (being played; registration
     * closed) -> COMPLETE (can be reopened). Only one edition can be active (OPEN or LIVE) at a time.
     */
    public enum Status {
        DRAFT, OPEN, LIVE, COMPLETE;

        public boolean isActive() { return this == OPEN || this == LIVE; }
    }

    public enum Stage { APERTURA, CLAUSURA }
}
