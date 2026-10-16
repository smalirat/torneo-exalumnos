package com.mjt.Football_Tournament_Management_System.entire;

import jakarta.persistence.*;
import lombok.*;

/** Suspension of a player: red card, double yellow or yellow-card accumulation. */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name="sanctions")
public class Sanction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional=false)
    private Season season;

    // Tournament where it happened (optional: the Excel only says it in the notes)
    @ManyToOne
    private Tournament tournament;

    @ManyToOne(optional=false)
    private Player player;

    @ManyToOne(optional=false)
    private Team team;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    private int matchesSuspended;
    private int matchesServed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    private String notes;

    @Enumerated(EnumType.STRING)
    private DataSource source;

    public enum Type { DIRECT_RED, DOUBLE_YELLOW, YELLOW_ACCUMULATION, OTHER }

    /** TRIBUNAL: under review, the served count does not decide it. */
    public enum Status { PENDING, SERVED, TRIBUNAL }

    public int pending() { return Math.max(matchesSuspended - matchesServed, 0); }
}
