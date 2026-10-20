package com.mjt.Football_Tournament_Management_System.entire;

import jakarta.persistence.*;
import lombok.*;

/** Banner or logo of an edition (one of each per edition). */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name = "tournament_images")
public class TournamentImage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long tournamentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Kind kind;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private byte[] imageData;

    public enum Kind { BANNER, LOGO }
}
