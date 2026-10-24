package com.mjt.Football_Tournament_Management_System.entire;

import jakarta.persistence.*;
import lombok.*;

/**
 * Who a player is: name and DNI (unique: the same DNI is the same person). Their files in teams' rosters are
 * {@link Player}s; a person has at most one active file at a time (a transfer closes the old one).
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name = "people")
public class Person {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fullName;

    /** Only digits; null while it has none (players imported from the Excel) */
    @Column(length = 10)
    private String dni;
}
