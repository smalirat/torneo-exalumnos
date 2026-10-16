package com.mjt.Football_Tournament_Management_System.entire;


import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name="matches")
public class MatchGame {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional=false)
    private Tournament tournament;

    @ManyToOne(optional=false) @JoinColumn(name="home_team_id")
    private Team homeTeam;

    @ManyToOne(optional=false) @JoinColumn(name="away_team_id")
    private Team awayTeam;

    private LocalDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    private Status status = Status.SCHEDULED;

    private Integer homeGoals = 0;
    private Integer awayGoals = 0;

    public enum Status { SCHEDULED, FINISHED }
}
