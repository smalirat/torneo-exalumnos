package com.mjt.Football_Tournament_Management_System.controller;

import com.mjt.Football_Tournament_Management_System.dto.AdminDtos;
import com.mjt.Football_Tournament_Management_System.dto.ResponseDtos;
import com.mjt.Football_Tournament_Management_System.service.ChampionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name="Champions history")
public class ChampionController {
    private final ChampionService service;

    @GetMapping("/api/public/champions")
    @Operation(summary = "Champions by category and year, with the most-titles ranking")
    public List<ResponseDtos.CategoryHistory> history() { return service.history(); }

    @PutMapping("/api/admin/champions")
    @Operation(summary = "Set the champion of a year and category")
    public ResponseDtos.ChampionRes upsert(@Valid @RequestBody AdminDtos.ChampionReq req) { return service.upsert(req); }

    @DeleteMapping("/api/admin/champions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { service.delete(id); }
}
