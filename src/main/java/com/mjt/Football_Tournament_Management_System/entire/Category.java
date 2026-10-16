package com.mjt.Football_Tournament_Management_System.entire;

import jakarta.persistence.*;
import lombok.*;

/** Division inside a tournament (A, B, C…). Always has at least one zone. */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name="categories")
public class Category {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional=false)
    private Tournament tournament;

    @Column(nullable = false, length = 60)
    private String name;

    private int displayOrder;
}
