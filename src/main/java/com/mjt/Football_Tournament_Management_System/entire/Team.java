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

    /** false while a team created by a captain waits for the admin (in review: not public, cannot register) */
    private boolean approved = true;
    private java.time.LocalDateTime createdAt;
    /** Name the captain asked for, waiting for the admin; null when there is none */
    private String pendingName;
    private java.time.LocalDateTime pendingNameAt;
    /** The captain asked to delete the team; null when not */
    private java.time.LocalDateTime deletionRequestedAt;

    /** Changes on every crest upload (goes in the image URL); null = no crest */
    private Integer crestVersion;

    // Owner of the team (required, one team per captain; may be a pending captain); not serialized
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private AppUser captain;
}
