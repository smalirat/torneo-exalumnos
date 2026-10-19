package com.mjt.Football_Tournament_Management_System.entire;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** The admin's answer to a captain's request (kept so the captain can see it, even if the team is gone). */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name="request_decisions")
public class RequestDecision {
    /** What a captain can ask for. Cosmetic changes (crest, jersey numbers) never need approval. */
    public enum Type { TEAM, RENAME, DELETE_TEAM, PLAYER_ADD, PLAYER_REMOVE, REGISTRATION }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private Team team;

    @Column(nullable = false)
    private String teamName;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser captain;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    /** New name, player name or tournament name */
    private String subject;

    private boolean approved;

    private String reason;

    @Column(nullable = false)
    private LocalDateTime decidedAt;
}
