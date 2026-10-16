package com.mjt.Football_Tournament_Management_System.controller;

import com.mjt.Football_Tournament_Management_System.dto.AdminDtos;
import com.mjt.Football_Tournament_Management_System.dto.ResponseDtos;
import com.mjt.Football_Tournament_Management_System.service.CollageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name="Champions collage")
public class CollageController {
    private final CollageService service;

    @GetMapping("/api/public/collage")
    @Operation(summary = "Collage photos with their layout (fractions of the board width)")
    public List<ResponseDtos.CollagePhotoRes> list() { return service.list(); }

    @GetMapping("/api/public/collage/{id}/image")
    @Operation(summary = "Image of a collage photo (never changes: cached for a year)")
    public ResponseEntity<byte[]> image(@PathVariable Long id) {
        var img = service.image(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(img.getContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(img.getData());
    }

    @PostMapping(value = "/api/admin/collage", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Add a photo (JPG/PNG, up to 5 MB); it starts on top, in the next spot of a loose grid")
    public ResponseDtos.CollagePhotoRes upload(@RequestParam("file") MultipartFile file) { return service.upload(file); }

    @PutMapping("/api/admin/collage/layout")
    @Operation(summary = "Save position, size and layer of some photos")
    public List<ResponseDtos.CollagePhotoRes> layout(@Valid @RequestBody AdminDtos.CollageLayoutReq req) {
        return service.updateLayout(req);
    }

    @PutMapping("/api/admin/collage/{id}/featured")
    @Operation(summary = "Mark or unmark the featured photo (only one)")
    public List<ResponseDtos.CollagePhotoRes> featured(@PathVariable Long id, @Valid @RequestBody AdminDtos.FeaturedReq req) {
        return service.setFeatured(id, req.getFeatured());
    }

    @DeleteMapping("/api/admin/collage/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { service.delete(id); }
}
