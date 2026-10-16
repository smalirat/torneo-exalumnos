package com.mjt.Football_Tournament_Management_System.entire;


import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name="notifications")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String target; // email/phone/teamId
    private String type;   // INFO/FIXTURE/RESULT

    @Column(length=1024)
    private String message;

    private LocalDateTime createdAt = LocalDateTime.now();
}
