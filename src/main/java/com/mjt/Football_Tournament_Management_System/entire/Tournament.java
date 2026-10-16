package com.mjt.Football_Tournament_Management_System.entire;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name="tournaments")
public class Tournament {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String location;

    private LocalDate registrationStart;
    private LocalDate registrationEnd;

    private LocalDate matchStart;
    private LocalDate matchEnd;

    @Enumerated(EnumType.STRING)
    private Status status = Status.OPEN;

    // Year it belongs to and whether it is the Apertura or the Clausura (both optional)
    @ManyToOne
    private Season season;

    @Enumerated(EnumType.STRING)
    private Stage stage;

    public enum Status { DRAFT, OPEN, CLOSED, LIVE, COMPLETE }

    public enum Stage { APERTURA, CLAUSURA }
}
