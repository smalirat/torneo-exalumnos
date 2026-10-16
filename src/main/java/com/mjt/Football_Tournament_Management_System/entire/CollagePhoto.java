package com.mjt.Football_Tournament_Management_System.entire;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A photo of the champions' collage. posX/posY/size are fractions of the board width (top-left corner and width),
 * so the layout keeps its shape at any screen size. layer: higher is on top.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name="collage_photos")
public class CollagePhoto {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String contentType;

    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false)
    private byte[] imageData;

    /** Pixel size of the stored image, for the aspect ratio */
    private int width;
    private int height;

    @Column(name = "pos_x")
    private double posX;
    @Column(name = "pos_y")
    private double posY;
    private double size;
    private int layer;

    /** Shown big above the collage; at most one */
    private boolean featured;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
