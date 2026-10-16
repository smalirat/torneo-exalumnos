package com.mjt.Football_Tournament_Management_System.entire;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** An uploaded workbook: previewed first, applied when the admin confirms. */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name="excel_imports")
public class ExcelImport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fileName;

    private String uploadedBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime appliedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    private Integer seasonYear;

    private String summary;

    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false)
    private byte[] fileData;

    public enum Status { PREVIEW, APPLIED }
}
