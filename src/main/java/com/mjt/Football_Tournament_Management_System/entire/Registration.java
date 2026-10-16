package com.mjt.Football_Tournament_Management_System.entire;

import jakarta.persistence.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Entity
@Table(name="registrations",
        uniqueConstraints=@UniqueConstraint(columnNames={"tournament_id","team_id"}))
public class Registration {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional=false)
    private Tournament tournament;

    @ManyToOne(optional=false)
    private Team team;

    private boolean approved = true;
}
