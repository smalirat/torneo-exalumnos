package com.mjt.Football_Tournament_Management_System.entire;

import jakarta.persistence.*;
import lombok.*;

/** Champion of a category in a year (Excel HISTORIA sheet). Category by name: historic ones may not exist as rows. */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name="champions")
public class Champion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "champion_year", nullable = false)
    private int year;

    @Column(nullable = false, length = 20)
    private String categoryName;

    @ManyToOne(optional=false)
    private Team team;

    @Enumerated(EnumType.STRING)
    private DataSource source;
}
