package com.mjt.Football_Tournament_Management_System.entire;

import jakarta.persistence.*;
import lombok.*;

/** Annual totals of a player (from the Excel's Goleadores / Figuras / Imbatibles sheets). Each value is optional. */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name="player_season_stats")
public class PlayerSeasonStat {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional=false)
    private Season season;

    @ManyToOne(optional=false)
    private Player player;

    // Team the player played for that season
    @ManyToOne(optional=false)
    private Team team;

    private Integer goals;
    private Integer figureCount;
    private Integer goalsConceded;

    @Enumerated(EnumType.STRING)
    private DataSource source;
}
