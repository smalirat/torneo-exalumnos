package com.mjt.Football_Tournament_Management_System.entire;

import jakarta.persistence.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name="users")
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.CAPTAIN;

    /** Notifications created after this instant are unread (null = never opened them). */
    private java.time.LocalDateTime notificationsSeenAt;

    // ADMIN manages everything; CAPTAIN can only edit the teams they own
    public enum Role { ADMIN, CAPTAIN }
}
