package com.mjt.Football_Tournament_Management_System.controller;

import com.mjt.Football_Tournament_Management_System.dto.AdminDtos;
import com.mjt.Football_Tournament_Management_System.dto.ResponseDtos;
import com.mjt.Football_Tournament_Management_System.service.ProtestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Protests of matches: captains file them, the admin resolves them */
@RestController
@RequiredArgsConstructor
@Tag(name = "Protests")
public class ProtestController {
    private final ProtestService service;

    @PostMapping("/api/me/protests")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Captain: protest a played match of their team (facts and request)")
    public ResponseDtos.ProtestRes create(@Valid @RequestBody AdminDtos.ProtestReq req, Authentication auth) {
        return service.create(auth.getName(), req);
    }

    @GetMapping("/api/me/protests")
    @Operation(summary = "Captain: the protests of their team, with the answer when resolved")
    public List<ResponseDtos.ProtestRes> mine(Authentication auth) { return service.mine(auth.getName()); }

    @GetMapping("/api/admin/protests")
    @Operation(summary = "Every protest, open ones first")
    public List<ResponseDtos.ProtestRes> all() { return service.all(); }

    @PostMapping("/api/admin/protests/{id}/resolve")
    @Operation(summary = "Resolve a protest with the organizer's answer (final)")
    public ResponseDtos.ProtestRes resolve(@PathVariable Long id, @Valid @RequestBody AdminDtos.ProtestResolutionReq req) {
        return service.resolve(id, req);
    }
}
