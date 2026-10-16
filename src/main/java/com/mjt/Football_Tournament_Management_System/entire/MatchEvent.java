package com.mjt.Football_Tournament_Management_System.entire;

import jakarta.persistence.*;
import lombok.*;

/** Something a player did in a match: a goal, an own goal or a card. */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name="match_events")
public class MatchEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional=false)
    private MatchGame match;

    @ManyToOne(optional=false)
    private Player player;

    // Team the player played for in this match (the player's team may change later)
    @ManyToOne(optional=false)
    private Team team;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    /** OWN_GOAL counts for the rival team and not in the player's scoring. */
    public enum Type { GOAL, OWN_GOAL, YELLOW_CARD, RED_CARD }
}
