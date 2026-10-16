package com.mjt.Football_Tournament_Management_System.entire;

import jakarta.persistence.*;
import lombok.*;

/** Accumulated yellow cards of a player in a tournament (Excel "Amonestados" blocks). */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name="yellow_card_tallies")
public class YellowCardTally {
    /** Every 4 accumulated yellow cards mean one match of suspension. */
    public static final int YELLOWS_PER_SUSPENSION = 4;

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional=false)
    private Tournament tournament;

    @ManyToOne(optional=false)
    private Player player;

    @ManyToOne(optional=false)
    private Team team;

    @Column(name = "yellow_cards")
    private int yellowCards;

    @Enumerated(EnumType.STRING)
    private DataSource source;

    public int suspensions() { return yellowCards / YELLOWS_PER_SUSPENSION; }
}
