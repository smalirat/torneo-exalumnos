package com.mjt.Football_Tournament_Management_System.entire;

import jakarta.persistence.*;
import lombok.*;

/**
 * A person's file in a team's roster ("ficha"). Goals, cards, sanctions and stats point to it, so they stay with the
 * team. A person has one active file at a time: a transfer closes the old one (leftAt) and opens a new one.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name="players")
public class Player {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fullName;
    private int jerseyNo;

    /** Who it is (name and DNI); the same person can have files in several teams over time */
    @ManyToOne(optional = false)
    private Person person;

    /** When the file opened, and closed (left the team: transfer or removal with history); null = in the roster */
    private java.time.LocalDate joinedAt;
    private java.time.LocalDate leftAt;
    /** When the addition was asked for (captain) or made (admin): the deadline of the rules counts from it */
    private java.time.LocalDateTime registeredAt;

    @ManyToOne(fetch = FetchType.LAZY)
    private Team team;

    /** false while a player added by a captain waits for the admin (not shown publicly, cannot score) */
    private boolean approved = true;
    private java.time.LocalDateTime requestedAt;
    /** The captain asked to remove the player; null when not */
    private java.time.LocalDateTime removalRequestedAt;

    public String getDni() { return person == null ? null : person.getDni(); }

    /** In the roster now: approved and not gone */
    public boolean isActive() { return approved && leftAt == null; }
}

