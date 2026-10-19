package com.mjt.Football_Tournament_Management_System.controller;


import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.time.Duration;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
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

    @Value("${app.league.title:TORNEO DE EXALUMNOS - COLEGIO MARIANISTA}")
    private String leagueTitle;

    @GetMapping("/league")
    @Operation(summary = "League name shown on the public site (property app.league.title)")
    public ResponseDtos.LeagueRes league() {
        return new ResponseDtos.LeagueRes(leagueTitle);
    }

    @GetMapping("/seasons")
    @Operation(summary = "Seasons (years), newest first, with their tournaments")
    public List<ResponseDtos.SeasonRes> seasons() {
        return tService.seasons();
    }

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

    @GetMapping("/teams/unclaimed")
    @Operation(summary = "Teams without captain yet (approved, captain pending, nobody asked): a new user can ask for one")
    public List<ResponseDtos.TeamRef> unclaimedTeams() {
        return teamService.claimable();
    }

    @GetMapping("/teams/{id}")
    @Operation(summary = "A team (one in review only for its captain and the admin)")
    public ResponseDtos.TeamRef team(@PathVariable Long id, Authentication auth) {
        return teamService.team(id, auth);
    }

    @GetMapping("/teams/{id}/crest")
    @Operation(summary = "Crest image of a team (its URL carries the version: cached for a year); 404 if it has none")
    public ResponseEntity<byte[]> crest(@PathVariable Long id) {
        var c = teamService.crest(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(c.getContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(c.getImageData());
    }

    @GetMapping("/teams/{id}/registrations")
    @Operation(summary = "Tournaments a team is registered in, with its category/zone (null until assigned)")
    public List<ResponseDtos.TeamRegistrationRes> teamRegistrations(@PathVariable Long id) {
        return teamService.registrations(id);
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
