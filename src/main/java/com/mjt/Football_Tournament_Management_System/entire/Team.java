package com.mjt.Football_Tournament_Management_System.entire;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name="teams")
public class Team {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String name;

    private String managerName;
    private String phone;
    private String email;

    // Owner of the team; not serialized (would expose the user)
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser captain;
}
