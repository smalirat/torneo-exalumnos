package com.mjt.Football_Tournament_Management_System.controller;


import com.mjt.Football_Tournament_Management_System.dto.TeamDtos;
import com.mjt.Football_Tournament_Management_System.dto.ViewDtos;
import com.mjt.Football_Tournament_Management_System.entire.Player;
import com.mjt.Football_Tournament_Management_System.entire.Registration;
import com.mjt.Football_Tournament_Management_System.entire.Team;
import com.mjt.Football_Tournament_Management_System.service.TeamService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
@Tag(name="Teams API")
public class TeamController {
    private final TeamService teamService;

    @PostMapping("/register")
    public Team register(@RequestBody TeamDtos.RegisterTeamReq req) {
        return teamService.createTeam(req);
    }

    @PostMapping("/{teamId}/players")
    public List<Player> addPlayers(@PathVariable Long teamId, @RequestBody TeamDtos.AddPlayersReq req) {
        return teamService.addPlayers(teamId, req);
    }

    @PostMapping("/{teamId}/tournaments/{tournamentId}/register")
    public Registration regTeam(@PathVariable Long teamId, @PathVariable Long tournamentId) {
        return teamService.registerTeamForTournament(teamId, tournamentId);
    }

    @GetMapping("/{teamId}/tournaments/{tournamentId}/dashboard")
    public ViewDtos.TeamDashboard dashboard(@PathVariable Long teamId, @PathVariable Long tournamentId) {
        return teamService.dashboard(teamId, tournamentId);
    }
}
