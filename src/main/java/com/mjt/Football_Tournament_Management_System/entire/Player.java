package com.mjt.Football_Tournament_Management_System.entire;

import jakarta.persistence.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name="players")
public class Player {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fullName;
    private int jerseyNo;

    @ManyToOne(fetch = FetchType.LAZY)
    private Team team;

    /** false while a player added by a captain waits for the admin (not shown publicly, cannot score) */
    private boolean approved = true;
    private java.time.LocalDateTime requestedAt;
    /** The captain asked to remove the player; null when not */
    private java.time.LocalDateTime removalRequestedAt;
}

