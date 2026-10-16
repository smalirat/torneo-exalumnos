package com.mjt.Football_Tournament_Management_System.entire;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** A user following a tournament: it shows up in "Campeonatos que sigo" and its news in the notifications. */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Entity
@Table(name="follows", uniqueConstraints=@UniqueConstraint(columnNames={"user_id","tournament_id"}))
public class Follow {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private AppUser user;

    @ManyToOne(optional = false)
    private Tournament tournament;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
