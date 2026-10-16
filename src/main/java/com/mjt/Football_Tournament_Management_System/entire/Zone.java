package com.mjt.Football_Tournament_Management_System.entire;

import jakarta.persistence.*;
import lombok.*;

/** Group of teams inside a category; standings and regular fixtures are per zone. */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name="zones")
public class Zone {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional=false)
    private Category category;

    @Column(nullable = false, length = 60)
    private String name;

    private int displayOrder;
}
