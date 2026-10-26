package com.mjt.Football_Tournament_Management_System.controller;

import com.mjt.Football_Tournament_Management_System.dto.AdminDtos;
import com.mjt.Football_Tournament_Management_System.dto.ResponseDtos;
import com.mjt.Football_Tournament_Management_System.service.BannedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Players not allowed by the Asociación de Exalumnos (admin only: /api/admin/**) */
@RestController
@RequestMapping("/api/admin/banned")
@RequiredArgsConstructor
@Tag(name = "Admin API (JWT)")
public class BannedController {
    private final BannedService service;

    @GetMapping
    @Operation(summary = "List of players not allowed in the tournament (by DNI), with the team they are in, if any")
    public List<ResponseDtos.BannedRes> list() { return service.list(); }

    @PostMapping
    @Operation(summary = "Add a DNI to the list (409 if already there)")
    public ResponseDtos.BannedRes add(@Valid @RequestBody AdminDtos.BannedReq req) { return service.add(req); }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Take a DNI off the list")
    public void remove(@PathVariable Long id) { service.remove(id); }
}
