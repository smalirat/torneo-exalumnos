package com.mjt.Football_Tournament_Management_System.controller;


import com.mjt.Football_Tournament_Management_System.dto.ResponseDtos;
import com.mjt.Football_Tournament_Management_System.dto.TeamDtos;
import com.mjt.Football_Tournament_Management_System.dto.ViewDtos;
import com.mjt.Football_Tournament_Management_System.service.TeamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
@Tag(name="Teams API")
public class TeamController {
    private final TeamService teamService;

    @GetMapping
    @Operation(summary = "Teams of the current user (all teams for admins), with player count")
    public List<ResponseDtos.TeamSummaryRes> list(Authentication auth) {
        return teamService.listFor(auth);
    }

    @PutMapping("/{teamId}")
    public ResponseDtos.TeamRes update(@PathVariable Long teamId, @Valid @RequestBody TeamDtos.RegisterTeamReq req,
                                       Authentication auth) {
        return teamService.updateTeam(teamId, req, auth);
    }

    @DeleteMapping("/{teamId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a team and its roster (409 if registered in a tournament)")
    public void delete(@PathVariable Long teamId, Authentication auth) {
        teamService.deleteTeam(teamId, auth);
    }

    @DeleteMapping("/{teamId}/players/{playerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a player from the roster (409 if they have goals or cards)")
    public void deletePlayer(@PathVariable Long teamId, @PathVariable Long playerId, Authentication auth) {
        teamService.deletePlayer(teamId, playerId, auth);
    }

    @PutMapping(value = "/{teamId}/crest", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload the team's crest (PNG/JPG, up to 2 MB); admin or the team's captain")
    public ResponseDtos.TeamRef setCrest(@PathVariable Long teamId, @RequestParam("file") MultipartFile file, Authentication auth) {
        return teamService.setCrest(teamId, file, auth);
    }

    @DeleteMapping("/{teamId}/crest")
    @Operation(summary = "Remove the team's crest (the UI then shows no badge)")
    public ResponseDtos.TeamRef removeCrest(@PathVariable Long teamId, Authentication auth) {
        return teamService.removeCrest(teamId, auth);
    }

    @GetMapping("/{teamId}/roster")
    @Operation(summary = "Full roster for the team's captain or the admin, players waiting for approval included")
    public List<ResponseDtos.PlayerRes> roster(@PathVariable Long teamId, Authentication auth) {
        return teamService.roster(teamId, auth);
    }

    @PatchMapping("/{teamId}/players/{playerId}")
    @Operation(summary = "Change a player's jersey number (cosmetic: no approval needed)")
    public ResponseDtos.PlayerRes setJersey(@PathVariable Long teamId, @PathVariable Long playerId,
                                            @Valid @RequestBody TeamDtos.JerseyReq req, Authentication auth) {
        return teamService.setJersey(teamId, playerId, req.getJerseyNo(), auth);
    }

    @PostMapping("/register")
    public ResponseDtos.TeamRes register(@Valid @RequestBody TeamDtos.RegisterTeamReq req, Authentication auth) {
        return teamService.createTeam(req, auth);
    }

    @PostMapping("/{teamId}/players")
    public List<ResponseDtos.PlayerRes> addPlayers(@PathVariable Long teamId, @Valid @RequestBody TeamDtos.AddPlayersReq req,
                                                   Authentication auth) {
        return teamService.addPlayers(teamId, req, auth);
    }

    @PostMapping("/{teamId}/tournaments/{tournamentId}/register")
    @Operation(summary = "Register the team in a tournament; the admin then assigns its category/zone")
    public ResponseDtos.RegistrationRes regTeam(@PathVariable Long teamId, @PathVariable Long tournamentId,
                                                Authentication auth) {
        return teamService.registerTeamForTournament(teamId, tournamentId, auth);
    }

    @GetMapping("/{teamId}/tournaments/{tournamentId}/dashboard")
    public ViewDtos.TeamDashboard dashboard(@PathVariable Long teamId, @PathVariable Long tournamentId) {
        return teamService.dashboard(teamId, tournamentId);
    }
}
