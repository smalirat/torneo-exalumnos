package com.mjt.Football_Tournament_Management_System.entire;

import jakarta.persistence.*;
import lombok.*;

/** A player (file) played a match for a team: ticked from the match sheet, or because they scored / were booked */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name = "match_appearances")
public class MatchAppearance {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private MatchGame match;

    @ManyToOne(optional = false)
    private Player player;

    @ManyToOne(optional = false)
    private Team team;
}
