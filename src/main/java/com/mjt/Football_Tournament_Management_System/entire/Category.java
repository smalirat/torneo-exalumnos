package com.mjt.Football_Tournament_Management_System.entire;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Formula;

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

    /** Read-only: number of zones, so the UI can hide the zone level when there is only one. */
    @Formula("(select count(*) from zones z where z.category_id = id)")
    private int zoneCount;

    public Category(Long id, Tournament tournament, String name, int displayOrder) {
        this(id, tournament, name, displayOrder, 0);
    }
}
