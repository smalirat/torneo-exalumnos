package com.mjt.Football_Tournament_Management_System.service;


import com.mjt.Football_Tournament_Management_System.dto.TeamDtos;
import com.mjt.Football_Tournament_Management_System.dto.ViewDtos;
import com.mjt.Football_Tournament_Management_System.entire.AppUser;
import com.mjt.Football_Tournament_Management_System.entire.MatchGame;
import com.mjt.Football_Tournament_Management_System.entire.Player;
import com.mjt.Football_Tournament_Management_System.entire.Registration;
import com.mjt.Football_Tournament_Management_System.entire.Team;
import com.mjt.Football_Tournament_Management_System.entire.Tournament;
import com.mjt.Football_Tournament_Management_System.exception.ConflictException;
import com.mjt.Football_Tournament_Management_System.exception.NotFoundException;
import com.mjt.Football_Tournament_Management_System.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TeamService {
    private final TeamRepository teamRepo;
    private final PlayerRepository playerRepo;
    private final TournamentRepository tournamentRepo;
    private final RegistrationRepository regRepo;
    private final MatchRepository matchRepo;
    private final AppUserRepository userRepo;

    /** A captain creating a team becomes its owner; teams created by an admin have no captain. */
    @Transactional
    public Team createTeam(TeamDtos.RegisterTeamReq req, Authentication auth) {
        var t = new Team();
        t.setName(req.getName()); t.setManagerName(req.getManagerName());
        t.setPhone(req.getPhone()); t.setEmail(req.getEmail());
        if (!isAdmin(auth)) {
            t.setCaptain(userRepo.findByUsername(auth.getName())
                    .orElseThrow(() -> NotFoundException.of("Usuario", auth.getName())));
        }
        return teamRepo.save(t);
    }

    @Transactional
    public List<Player> addPlayers(Long teamId, TeamDtos.AddPlayersReq req, Authentication auth) {
        var team = ownedTeam(teamId, auth);
        List<Player> list = new ArrayList<>();
        for (var p : req.getPlayers()) {
            var pl = new Player(); pl.setTeam(team); pl.setFullName(p.getFullName()); pl.setJerseyNo(p.getJerseyNo());
            list.add(pl);
        }
        return playerRepo.saveAll(list);
    }

    @Transactional
    public Registration registerTeamForTournament(Long teamId, Long tournamentId, Authentication auth) {
        var team = ownedTeam(teamId, auth);
        var t = findTournament(tournamentId);
        var today = java.time.LocalDate.now();
        if (t.getRegistrationStart() == null || t.getRegistrationEnd() == null
                || today.isBefore(t.getRegistrationStart()) || today.isAfter(t.getRegistrationEnd())) {
            throw new ConflictException("La inscripción al campeonato no está abierta");
        }
        if (regRepo.existsByTournamentAndTeam(t, team)) {
            throw new ConflictException("El equipo ya está inscripto en este campeonato");
        }
        var r = new Registration(); r.setTournament(t); r.setTeam(team); r.setApproved(true);
        return regRepo.save(r);
    }

    public ViewDtos.TeamDashboard dashboard(Long teamId, Long tournamentId) {
        var team = findTeam(teamId);
        var t = findTournament(tournamentId);
        var matches = matchRepo.findByTournamentAndHomeTeamOrAwayTeam(t, team, team);
        int played=0,w=0,d=0,l=0,gf=0,ga=0,pts=0;
        List<ViewDtos.Fixture> fixtures = new ArrayList<>();
        for (var m : matches) {
            boolean finished = m.getStatus() == MatchGame.Status.FINISHED;
            if (finished) {
                played++;
                boolean home = m.getHomeTeam().getId().equals(teamId);
                int f = home? m.getHomeGoals(): m.getAwayGoals();
                int a = home? m.getAwayGoals(): m.getHomeGoals();
                gf += f; ga += a;
                if (f>a) { w++; pts+=3; } else if (f==a) { d++; pts+=1; } else { l++; }
            }
            fixtures.add(ViewDtos.Fixture.builder()
                    .matchId(m.getId()).vs(m.getHomeTeam().getName()+" vs "+m.getAwayTeam().getName())
                    .at(m.getScheduledAt()).status(m.getStatus().name())
                    .score(m.getHomeGoals()+":"+m.getAwayGoals()).build());
        }
        return ViewDtos.TeamDashboard.builder()
                .teamId(team.getId()).teamName(team.getName())
                .played(played).wins(w).draws(d).losses(l).goalsFor(gf).goalsAgainst(ga).points(pts)
                .fixtures(fixtures).build();
    }

    private Team findTeam(Long id) {
        return teamRepo.findById(id).orElseThrow(() -> NotFoundException.of("Equipo", id));
    }

    private Tournament findTournament(Long id) {
        return tournamentRepo.findById(id).orElseThrow(() -> NotFoundException.of("Campeonato", id));
    }

    /** Team that the current user may modify: admins any, captains only their own. */
    private Team ownedTeam(Long teamId, Authentication auth) {
        var team = findTeam(teamId);
        if (isAdmin(auth)) return team;
        AppUser captain = team.getCaptain();
        if (captain == null || !captain.getUsername().equals(auth.getName())) {
            throw new AccessDeniedException("Not the captain of team " + teamId);
        }
        return team;
    }

    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
