package com.mjt.Football_Tournament_Management_System.controller;

import com.mjt.Football_Tournament_Management_System.dto.AdminDtos;
import com.mjt.Football_Tournament_Management_System.dto.ResponseDtos;
import com.mjt.Football_Tournament_Management_System.service.SeasonDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Annual rankings, sanctions and accumulated yellow cards. Public reads, admin writes (by path). */
@RestController
@RequiredArgsConstructor
@Tag(name="Season data (rankings, sanctions)")
public class SeasonDataController {
    private final SeasonDataService service;

    @GetMapping("/api/public/seasons/{id}/scorers")
    @Operation(summary = "Annual top scorers (totals, e.g. imported from the Excel)")
    public List<ResponseDtos.SeasonStatRow> scorers(@PathVariable Long id) { return service.scorers(id); }

    @GetMapping("/api/public/seasons/{id}/figures")
    @Operation(summary = "Annual ranking of times chosen figure of the match")
    public List<ResponseDtos.SeasonStatRow> figures(@PathVariable Long id) { return service.figures(id); }

    @GetMapping("/api/public/seasons/{id}/goalkeepers")
    @Operation(summary = "Annual goalkeepers ranking (imbatibles): fewest goals conceded first")
    public List<ResponseDtos.SeasonStatRow> goalkeepers(@PathVariable Long id) { return service.goalkeepers(id); }

    @PutMapping("/api/admin/seasons/{id}/player-stats")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Set a player's annual totals (goals, figure count, goals conceded)")
    public void upsertStat(@PathVariable Long id, @Valid @RequestBody AdminDtos.PlayerStatReq req) {
        service.upsertPlayerStat(id, req);
    }

    @DeleteMapping("/api/admin/seasons/{id}/player-stats/{playerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteStat(@PathVariable Long id, @PathVariable Long playerId) { service.deletePlayerStat(id, playerId); }

    @GetMapping("/api/public/seasons/{id}/sanctions")
    @Operation(summary = "Sanctions of the season (pending first)")
    public List<ResponseDtos.SanctionRes> sanctions(@PathVariable Long id) { return service.sanctions(id); }

    @PostMapping("/api/admin/seasons/{id}/sanctions")
    public ResponseDtos.SanctionRes createSanction(@PathVariable Long id, @Valid @RequestBody AdminDtos.SanctionReq req) {
        return service.createSanction(id, req);
    }

    @PutMapping("/api/admin/sanctions/{id}")
    public ResponseDtos.SanctionRes updateSanction(@PathVariable Long id, @Valid @RequestBody AdminDtos.SanctionReq req) {
        return service.updateSanction(id, req);
    }

    @DeleteMapping("/api/admin/sanctions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSanction(@PathVariable Long id) { service.deleteSanction(id); }

    @GetMapping("/api/public/tournaments/{id}/yellow-cards")
    @Operation(summary = "Accumulated yellow cards per player in a tournament (4 = 1 match suspension)")
    public List<ResponseDtos.YellowTallyRes> yellowCards(@PathVariable Long id) { return service.yellowCards(id); }

    @PutMapping("/api/admin/tournaments/{id}/yellow-cards")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void upsertYellow(@PathVariable Long id, @Valid @RequestBody AdminDtos.YellowTallyReq req) {
        service.upsertYellowCards(id, req);
    }

    @DeleteMapping("/api/admin/tournaments/{id}/yellow-cards/{playerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteYellow(@PathVariable Long id, @PathVariable Long playerId) { service.deleteYellowCards(id, playerId); }
}
