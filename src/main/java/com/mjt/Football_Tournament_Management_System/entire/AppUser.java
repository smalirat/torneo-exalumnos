package com.mjt.Football_Tournament_Management_System.entire;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name="users")
public class AppUser {
    /** Username prefix of pending captains; nobody can pick it when signing up. */
    public static final String PENDING_PREFIX = "pendiente-";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.CAPTAIN;

    /** Captain created with its team (Excel import / admin) that nobody has claimed yet: cannot log in. */
    private boolean pending;

    /** Secret of the invitation link that lets the real captain claim this account; null when there is none. */
    @Column(unique = true, length = 64)
    private String inviteToken;

    // ADMIN manages everything; CAPTAIN can only edit the team they own
    public enum Role { ADMIN, CAPTAIN }

    public AppUser(Long id, String username, String passwordHash, Role role) {
        this(id, username, passwordHash, role, false, null);
    }

    /** Placeholder captain for a team created without one; "!" is not a valid hash, so no password matches. */
    public static AppUser pendingCaptain() {
        return new AppUser(null, PENDING_PREFIX + UUID.randomUUID().toString().substring(0, 12), "!", Role.CAPTAIN, true, null);
    }
}
