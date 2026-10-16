package com.mjt.Football_Tournament_Management_System.controller;

import com.mjt.Football_Tournament_Management_System.dto.AdminDtos;
import com.mjt.Football_Tournament_Management_System.dto.ResponseDtos;
import com.mjt.Football_Tournament_Management_System.service.ExcelImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashSet;
import java.util.List;

@RestController
@RequestMapping("/api/admin/imports")
@RequiredArgsConstructor
@Tag(name="Excel import")
public class ExcelImportController {
    private final ExcelImportService service;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload the league's .xlsx and get a preview (nothing is saved but the file)")
    public ResponseDtos.ImportPreviewRes upload(@RequestParam("file") MultipartFile file, Authentication auth) {
        return service.preview(file, auth.getName());
    }

    @PostMapping("/{id}/apply")
    @Operation(summary = "Apply a previewed upload; `overwrite` lists the conflict keys to replace with the Excel value")
    public ResponseDtos.ImportPreviewRes apply(@PathVariable Long id, @RequestBody(required = false) AdminDtos.ApplyImportReq req) {
        return service.apply(id, req == null || req.getOverwrite() == null ? new HashSet<>() : new HashSet<>(req.getOverwrite()));
    }

    @GetMapping
    @Operation(summary = "Last uploads")
    public List<ResponseDtos.ImportHistoryRes> history() {
        return service.history();
    }
}
