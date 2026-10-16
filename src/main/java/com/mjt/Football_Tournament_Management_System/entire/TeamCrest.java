package com.mjt.Football_Tournament_Management_System.entire;

import jakarta.persistence.*;
import lombok.*;

/** Crest image of a team (one per team, keyed by the team id). */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name="team_crests")
public class TeamCrest {
    @Id
    private Long teamId;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private byte[] imageData;
}
