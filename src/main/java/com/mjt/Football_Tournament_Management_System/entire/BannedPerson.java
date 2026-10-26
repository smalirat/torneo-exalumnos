package com.mjt.Football_Tournament_Management_System.entire;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Someone the Asociación de Exalumnos does not allow in the tournament (by DNI) */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name = "banned_people")
public class BannedPerson {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String dni;

    private String fullName;

    @Column(length = 500)
    private String reason;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
