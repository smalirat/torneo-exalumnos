package com.mjt.Football_Tournament_Management_System.controller;


import com.mjt.Football_Tournament_Management_System.dto.TeamDtos;
import com.mjt.Football_Tournament_Management_System.dto.ViewDtos;
import com.mjt.Football_Tournament_Management_System.entire.Player;
import com.mjt.Football_Tournament_Management_System.entire.Registration;
import com.mjt.Football_Tournament_Management_System.entire.Team;
import com.mjt.Football_Tournament_Management_System.service.TeamService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
@Tag(name="Teams API")
public class TeamController {
    private final TeamService teamService;

    @PostMapping("/register")
    public Team register(@Valid @RequestBody TeamDtos.RegisterTeamReq req, Authentication auth) {
        return teamService.createTeam(req, auth);
    }

    @PostMapping("/{teamId}/players")
    public List<Player> addPlayers(@PathVariable Long teamId, @Valid @RequestBody TeamDtos.AddPlayersReq req,
                                   Authentication auth) {
        return teamService.addPlayers(teamId, req, auth);
    }

    @PostMapping("/{teamId}/tournaments/{tournamentId}/register")
    public Registration regTeam(@PathVariable Long teamId, @PathVariable Long tournamentId, Authentication auth) {
        return teamService.registerTeamForTournament(teamId, tournamentId, auth);
    }

    @GetMapping("/{teamId}/tournaments/{tournamentId}/dashboard")
    public ViewDtos.TeamDashboard dashboard(@PathVariable Long teamId, @PathVariable Long tournamentId) {
        return teamService.dashboard(teamId, tournamentId);
    }
}
