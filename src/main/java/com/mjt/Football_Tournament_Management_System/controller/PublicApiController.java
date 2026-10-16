package com.mjt.Football_Tournament_Management_System.controller;


import com.mjt.Football_Tournament_Management_System.dto.ResponseDtos;
import com.mjt.Football_Tournament_Management_System.service.CategoryService;
import com.mjt.Football_Tournament_Management_System.service.MatchService;
import com.mjt.Football_Tournament_Management_System.service.PlayoffService;
import com.mjt.Football_Tournament_Management_System.service.RankingService;
import com.mjt.Football_Tournament_Management_System.service.StandingsService;
import com.mjt.Football_Tournament_Management_System.service.TeamService;
import com.mjt.Football_Tournament_Management_System.service.TournamentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@Tag(name="Public API")
public class PublicApiController {
    private final TournamentService tService;
    private final CategoryService cService;
    private final StandingsService sService;
    private final MatchService mService;
    private final RankingService rService;
    private final TeamService teamService;
    private final PlayoffService pService;

    @GetMapping("/tournaments")
    public List<ResponseDtos.TournamentRes> tournaments() {
        return tService.findAll();
    }

    @GetMapping("/tournaments/{id}/categories")
    @Operation(summary = "Categories of a tournament with their zones (zoneCount = 1 -> hide the zone level in the UI)")
    public List<ResponseDtos.CategoryRes> categories(@PathVariable Long id) {
        return cService.listByTournament(id);
    }

    @GetMapping("/zones/{id}/standings")
    @Operation(summary = "Zone table: 3/1/0 + presence point; tie-break DIF, GF, head-to-head, fair play, presence, name")
    public List<ResponseDtos.StandingRow> standings(@PathVariable Long id) {
        return sService.zoneStandings(id);
    }

    @GetMapping("/matches/{id}/events")
    @Operation(summary = "Goals, own goals and cards of a match")
    public List<ResponseDtos.MatchEventRes> matchEvents(@PathVariable Long id) {
        return mService.events(id);
    }

    @GetMapping("/tournaments/{id}/special-matches")
    @Operation(summary = "Promotions and finals of a tournament")
    public List<ResponseDtos.MatchRes> specialMatches(@PathVariable Long id) {
        return pService.list(id);
    }

    @GetMapping("/tournaments/{id}/scorers")
    @Operation(summary = "Top scorers of a tournament (own goals excluded)")
    public List<ResponseDtos.ScorerRow> scorers(@PathVariable Long id) {
        return rService.scorers(id);
    }

    @GetMapping("/tournaments/{id}/cards")
    @Operation(summary = "Booked players of a tournament (reds first, then yellows)")
    public List<ResponseDtos.CardRow> cards(@PathVariable Long id) {
        return rService.cards(id);
    }

    @GetMapping("/teams/{id}/players")
    public List<ResponseDtos.PlayerRes> players(@PathVariable Long id) {
        return teamService.players(id);
    }

    @GetMapping("/zones/{id}/registrations")
    public List<ResponseDtos.RegistrationRes> zoneRegs(@PathVariable Long id) {
        return cService.zoneRegistrations(id);
    }

    @GetMapping("/zones/{id}/fixtures")
    public List<ResponseDtos.MatchRes> zoneFixtures(@PathVariable Long id) {
        return cService.zoneFixtures(id);
    }

    @GetMapping("/tournaments/{id}/registrations")
    public List<ResponseDtos.RegistrationRes> regs(@PathVariable Long id) {
        return tService.registrations(id);
    }

    @GetMapping("/tournaments/{id}/fixtures")
    public List<ResponseDtos.MatchRes> fixtures(@PathVariable Long id) {
        return tService.fixtures(id);
    }
}
