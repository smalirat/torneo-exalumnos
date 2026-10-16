package com.mjt.Football_Tournament_Management_System.entire;

import jakarta.persistence.*;
import lombok.*;

/** A year of the league; groups its tournaments (Apertura, Clausura). Annual rankings hang from it. */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name="seasons")
public class Season {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "season_year", nullable = false, unique = true)
    private int year;
}
