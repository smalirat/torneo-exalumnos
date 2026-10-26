package com.mjt.Football_Tournament_Management_System.entire;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** A captain protests a match of their team (the rules: facts, request, signed by the captain); the admin resolves it */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name = "match_protests")
public class MatchProtest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private MatchGame match;

    @ManyToOne(optional = false)
    private Team team;

    @ManyToOne(optional = false)
    private AppUser author;

    @Column(nullable = false, length = 2000)
    private String facts;

    @Column(nullable = false, length = 1000)
    private String request;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.OPEN;

    @Column(length = 2000)
    private String resolution;

    @Column(nullable = false)
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;

    public enum Status { OPEN, RESOLVED }
}
