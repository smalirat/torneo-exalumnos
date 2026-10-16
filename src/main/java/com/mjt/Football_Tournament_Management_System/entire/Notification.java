package com.mjt.Football_Tournament_Management_System.entire;


import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** News of a tournament (fixture published, result loaded, Excel applied), shown to its followers. */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name="notifications")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Tournament tournament;

    @Enumerated(EnumType.STRING)
    private Type type;

    @Column(length=1024)
    private String message;

    private LocalDateTime createdAt = LocalDateTime.now();

    public enum Type { FIXTURE, RESULT, IMPORT }
}
