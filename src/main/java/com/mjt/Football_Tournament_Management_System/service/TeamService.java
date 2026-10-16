package com.mjt.Football_Tournament_Management_System.service;


import com.mjt.Football_Tournament_Management_System.dto.ResponseDtos;
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
    private final MatchEventRepository eventRepo;
    private final PlayerSeasonStatRepository statRepo;
    private final SanctionRepository sanctionRepo;
    private final YellowCardTallyRepository yellowRepo;
    private final ChampionRepository championRepo;

    /**
     * A captain creating a team becomes its owner (one team per captain). A team created by an admin gets a
     * pending captain, claimed later with an invitation link.
     */
    @Transactional
    public ResponseDtos.TeamRes createTeam(TeamDtos.RegisterTeamReq req, Authentication auth) {
        var t = new Team();
        t.setName(req.getName()); t.setManagerName(req.getManagerName());
        t.setPhone(req.getPhone()); t.setEmail(req.getEmail());
        if (isAdmin(auth)) {
            t.setCaptain(userRepo.save(AppUser.pendingCaptain()));
        } else {
            var captain = userRepo.findByUsername(auth.getName())
                    .orElseThrow(() -> NotFoundException.of("Usuario", auth.getName()));
            if (!teamRepo.findByCaptain(captain).isEmpty()) throw new ConflictException("Ya sos capitán de un equipo");
            t.setCaptain(captain);
        }
        return ResponseDtos.TeamRes.from(teamRepo.save(t));
    }

    /** Admins see every team; captains only the one they own. */
    @Transactional(readOnly = true)
    public List<ResponseDtos.TeamSummaryRes> listFor(Authentication auth) {
        var teams = isAdmin(auth) ? teamRepo.findAllByOrderByNameAsc()
                : teamRepo.findByCaptain_UsernameOrderByNameAsc(auth.getName());
        return teams.stream().map(t -> ResponseDtos.TeamSummaryRes.of(t, playerRepo.countByTeam(t))).toList();
    }

    @Transactional
    public ResponseDtos.TeamRes updateTeam(Long teamId, TeamDtos.RegisterTeamReq req, Authentication auth) {
        var t = ownedTeam(teamId, auth);
        t.setName(req.getName()); t.setManagerName(req.getManagerName());
        t.setPhone(req.getPhone()); t.setEmail(req.getEmail());
        return ResponseDtos.TeamRes.from(t);
    }

    /** A team can be deleted (with its roster) only while it is not registered in any tournament. */
    @Transactional
    public void deleteTeam(Long teamId, Authentication auth) {
        var t = ownedTeam(teamId, auth);
        if (championRepo.existsByTeam(t)) {
            throw new ConflictException("El equipo figura en la historia de campeones; no se puede eliminar");
        }
        if (regRepo.existsByTeam(t)) {
            throw new ConflictException("El equipo está inscripto en un campeonato; no se puede eliminar");
        }
        playerRepo.deleteAll(playerRepo.findByTeamOrderByJerseyNoAscFullNameAsc(t));
        var captain = t.getCaptain();
        teamRepo.delete(t);
        // A pending captain only existed for this team; a real account stays (it may register another team)
        if (captain.isPending()) {
            teamRepo.flush();
            userRepo.delete(captain);
        }
    }

    /** Removes a player from the roster; not allowed once they have goals or cards. */
    @Transactional
    public void deletePlayer(Long teamId, Long playerId, Authentication auth) {
        var team = ownedTeam(teamId, auth);
        var player = playerRepo.findById(playerId).orElseThrow(() -> NotFoundException.of("Jugador", playerId));
        if (player.getTeam() == null || !player.getTeam().getId().equals(team.getId())) {
            throw NotFoundException.of("Jugador", playerId);
        }
        if (eventRepo.existsByPlayer(player) || statRepo.existsByPlayer(player)
                || sanctionRepo.existsByPlayer(player) || yellowRepo.existsByPlayer(player)) {
            throw new ConflictException("El jugador tiene goles, tarjetas, estadísticas o sanciones cargadas; no se puede eliminar");
        }
        playerRepo.delete(player);
    }

    public ResponseDtos.TeamRef team(Long teamId) {
        return ResponseDtos.TeamRef.from(findTeam(teamId));
    }

    /** Tournaments a team is registered in, newest first. */
    public List<ResponseDtos.TeamRegistrationRes> registrations(Long teamId) {
        return regRepo.findByTeamOrderByIdDesc(findTeam(teamId)).stream()
                .map(ResponseDtos.TeamRegistrationRes::from).toList();
    }

    public List<ResponseDtos.PlayerRes> players(Long teamId) {
        return playerRepo.findByTeamOrderByJerseyNoAscFullNameAsc(findTeam(teamId)).stream()
                .map(ResponseDtos.PlayerRes::from).toList();
    }

    @Transactional
    public List<ResponseDtos.PlayerRes> addPlayers(Long teamId, TeamDtos.AddPlayersReq req, Authentication auth) {
        var team = ownedTeam(teamId, auth);
        List<Player> list = new ArrayList<>();
        for (var p : req.getPlayers()) {
            var pl = new Player(); pl.setTeam(team); pl.setFullName(p.getFullName()); pl.setJerseyNo(p.getJerseyNo());
            list.add(pl);
        }
        return playerRepo.saveAll(list).stream().map(ResponseDtos.PlayerRes::from).toList();
    }

    @Transactional
    /** The team joins the tournament unassigned; the admin later places it in a category/zone. */
    public ResponseDtos.RegistrationRes registerTeamForTournament(Long teamId, Long tournamentId, Authentication auth) {
        var team = ownedTeam(teamId, auth);
        var t = findTournament(tournamentId);
        var today = java.time.LocalDate.now();
        // The registration window applies to captains; the organizer can register teams at any time
        if (!isAdmin(auth) && (t.getRegistrationStart() == null || t.getRegistrationEnd() == null
                || today.isBefore(t.getRegistrationStart()) || today.isAfter(t.getRegistrationEnd()))) {
            throw new ConflictException("La inscripción al campeonato no está abierta");
        }
        if (regRepo.existsByTournamentAndTeam(t, team)) {
            throw new ConflictException("El equipo ya está inscripto en este campeonato");
        }
        var r = new Registration(); r.setTournament(t); r.setTeam(team); r.setApproved(true);
        return ResponseDtos.RegistrationRes.from(regRepo.save(r));
    }

    public ViewDtos.TeamDashboard dashboard(Long teamId, Long tournamentId) {
        var team = findTeam(teamId);
        var t = findTournament(tournamentId);
        var matches = matchRepo.findByTournamentAndTeam(t, team);
        var pending = MatchGame.pendingWalkovers(matchRepo.findByTournament(t));
        int played=0,w=0,d=0,l=0,gf=0,ga=0,pts=0;
        List<ViewDtos.Fixture> fixtures = new ArrayList<>();
        for (var m : matches) {
            if (m.counts(pending)) {
                played++;
                boolean home = m.isHome(team);
                gf += m.goalsFor(home); ga += m.goalsAgainst(home);
                int result = m.resultPoints(home);
                if (result == 3) w++; else if (result == 1) d++; else l++;
                pts += result + m.presencePoints(home);
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
        if (!captain.getUsername().equals(auth.getName())) {
            throw new AccessDeniedException("Not the captain of team " + teamId);
        }
        return team;
    }

    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
