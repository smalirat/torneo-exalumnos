package com.mjt.Football_Tournament_Management_System.service;


import com.mjt.Football_Tournament_Management_System.dto.ResponseDtos;
import com.mjt.Football_Tournament_Management_System.dto.TeamDtos;
import com.mjt.Football_Tournament_Management_System.dto.ViewDtos;
import com.mjt.Football_Tournament_Management_System.entire.AppUser;
import com.mjt.Football_Tournament_Management_System.entire.MatchGame;
import com.mjt.Football_Tournament_Management_System.entire.Player;
import com.mjt.Football_Tournament_Management_System.entire.Registration;
import com.mjt.Football_Tournament_Management_System.entire.Team;
import com.mjt.Football_Tournament_Management_System.entire.TeamCrest;
import com.mjt.Football_Tournament_Management_System.exception.BadRequestException;
import com.mjt.Football_Tournament_Management_System.entire.Tournament;
import com.mjt.Football_Tournament_Management_System.exception.ConflictException;
import com.mjt.Football_Tournament_Management_System.exception.NotFoundException;
import com.mjt.Football_Tournament_Management_System.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
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
    private final TeamCrestRepository crestRepo;

    private static final Set<String> CREST_TYPES = Set.of("image/png", "image/jpeg");
    private static final long CREST_MAX_BYTES = 2 * 1024 * 1024;

    /**
     * A captain creating a team becomes its owner (one team per captain). A team created by an admin gets a
     * pending captain, claimed later with an invitation link.
     */
    @Transactional
    public ResponseDtos.TeamRes createTeam(TeamDtos.RegisterTeamReq req, Authentication auth) {
        var t = new Team();
        t.setName(req.getName()); t.setManagerName(req.getManagerName());
        t.setPhone(req.getPhone()); t.setEmail(req.getEmail());
        t.setCreatedAt(LocalDateTime.now());
        if (isAdmin(auth)) {
            t.setCaptain(userRepo.save(AppUser.pendingCaptain()));
        } else {
            var captain = userRepo.findByUsername(auth.getName())
                    .orElseThrow(() -> NotFoundException.of("Usuario", auth.getName()));
            if (!teamRepo.findByCaptain(captain).isEmpty()) throw new ConflictException("Ya sos capitán de un equipo");
            t.setCaptain(captain);
            t.setApproved(false); // in review until the admin approves it (the captain can already build the roster)
        }
        return ResponseDtos.TeamRes.from(teamRepo.save(t));
    }

    /** Sign-up with a new team: it starts in review, owned by the new captain. */
    @Transactional
    public void createTeamFor(AppUser captain, String name) {
        if (teamRepo.findByName(name).isPresent()) throw new ConflictException("Ya hay un equipo llamado " + name);
        var t = new Team();
        t.setName(name); t.setCaptain(captain); t.setApproved(false); t.setCreatedAt(LocalDateTime.now());
        teamRepo.save(t);
    }

    /**
     * A user without team asks to be the captain of a team that has none yet (its captain is pending).
     * One request per team at a time; the admin approves (the user takes over) or rejects it.
     */
    @Transactional
    public void requestClaim(AppUser user, Long teamId) {
        if (!teamRepo.findByCaptain(user).isEmpty()) throw new ConflictException("Ya sos capitán de un equipo");
        if (!teamRepo.findByClaimUser(user).isEmpty()) throw new ConflictException("Ya pediste ser capitán de un equipo; esperá la respuesta");
        var t = findTeam(teamId);
        if (!t.isApproved() || !t.getCaptain().isPending()) throw new ConflictException("Ese equipo ya tiene capitán");
        if (t.getClaimUser() != null) throw new ConflictException("Alguien ya pidió ser el capitán de ese equipo");
        t.setClaimUser(user);
        t.setClaimRequestedAt(LocalDateTime.now());
    }

    @Transactional
    public void requestClaim(String username, Long teamId) {
        requestClaim(userRepo.findByUsername(username).orElseThrow(() -> NotFoundException.of("Usuario", username)), teamId);
    }

    /** Teams a new captain can ask for: approved, without a real captain and without a pending request */
    @Transactional(readOnly = true)
    public List<ResponseDtos.TeamRef> claimable() {
        return teamRepo.findByApprovedTrueAndCaptain_PendingTrueAndClaimUserIsNullOrderByNameAsc().stream()
                .map(ResponseDtos.TeamRef::from).toList();
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
        t.setManagerName(req.getManagerName());
        t.setPhone(req.getPhone()); t.setEmail(req.getEmail());
        if (isAdmin(auth) || !t.isApproved()) {
            t.setName(req.getName());
        } else if (req.getName().equals(t.getName())) {
            t.setPendingName(null); t.setPendingNameAt(null);
        } else {
            // A newer request replaces the previous one
            t.setPendingName(req.getName()); t.setPendingNameAt(LocalDateTime.now());
        }
        return ResponseDtos.TeamRes.from(t);
    }

    /** A team can be deleted (with its roster) only while it is not registered in any tournament. */
    @Transactional
    public void deleteTeam(Long teamId, Authentication auth) {
        var t = ownedTeam(teamId, auth);
        checkDeletable(t);
        if (!isAdmin(auth) && t.isApproved()) {
            t.setDeletionRequestedAt(LocalDateTime.now()); // the admin decides (a team in review is just withdrawn)
            return;
        }
        removeTeam(t);
    }

    private void checkDeletable(Team t) {
        if (championRepo.existsByTeam(t)) {
            throw new ConflictException("El equipo figura en la historia de campeones; no se puede eliminar");
        }
        if (regRepo.existsByTeam(t)) {
            throw new ConflictException("El equipo está inscripto en un campeonato; no se puede eliminar");
        }
    }

    /** Deletes the team with its roster (and its captain, if still pending). Also used when approving a request. */
    @Transactional
    public void removeTeam(Team t) {
        checkDeletable(t);
        playerRepo.deleteAll(playerRepo.findByTeamOrderByJerseyNoAscFullNameAsc(t));
        var captain = t.getCaptain();
        teamRepo.delete(t);
        // A pending captain only existed for this team; a real account stays (it may register another team)
        if (captain.isPending()) {
            teamRepo.flush();
            userRepo.delete(captain);
        }
    }

    /** Sets (or replaces) the team's crest: PNG or JPG up to 2 MB. Admin or the team's own captain. */
    @Transactional
    public ResponseDtos.TeamRef setCrest(Long teamId, MultipartFile file, Authentication auth) {
        var t = ownedTeam(teamId, auth);
        if (file == null || file.isEmpty()) throw new BadRequestException("Elegí una imagen");
        if (!CREST_TYPES.contains(file.getContentType())) throw new BadRequestException("El escudo tiene que ser PNG o JPG");
        if (file.getSize() > CREST_MAX_BYTES) throw new BadRequestException("El escudo pesa más de 2 MB");
        byte[] data;
        try {
            data = file.getBytes();
            if (ImageIO.read(new ByteArrayInputStream(data)) == null) throw new IOException("not an image");
        } catch (IOException e) {
            throw new BadRequestException("No se pudo leer la imagen");
        }
        crestRepo.save(new TeamCrest(t.getId(), file.getContentType(), data));
        // Seconds since epoch: a new value on every upload (also after removing one), so cached URLs never go stale
        t.setCrestVersion((int) (System.currentTimeMillis() / 1000));
        return ResponseDtos.TeamRef.from(t);
    }

    @Transactional
    public ResponseDtos.TeamRef removeCrest(Long teamId, Authentication auth) {
        var t = ownedTeam(teamId, auth);
        crestRepo.deleteById(t.getId());
        t.setCrestVersion(null);
        return ResponseDtos.TeamRef.from(t);
    }

    public TeamCrest crest(Long teamId) {
        return crestRepo.findById(teamId).orElseThrow(() -> new NotFoundException("El equipo no tiene escudo"));
    }

    /** Removes a player from the roster; not allowed once they have goals or cards. */
    @Transactional
    public void deletePlayer(Long teamId, Long playerId, Authentication auth) {
        var team = ownedTeam(teamId, auth);
        var player = playerRepo.findById(playerId).orElseThrow(() -> NotFoundException.of("Jugador", playerId));
        if (player.getTeam() == null || !player.getTeam().getId().equals(team.getId())) {
            throw NotFoundException.of("Jugador", playerId);
        }
        checkRemovable(player);
        if (!isAdmin(auth) && player.isApproved() && team.isApproved()) {
            player.setRemovalRequestedAt(LocalDateTime.now()); // the admin decides
            return;
        }
        playerRepo.delete(player);
    }

    private void checkRemovable(Player player) {
        if (eventRepo.existsByPlayer(player) || statRepo.existsByPlayer(player)
                || sanctionRepo.existsByPlayer(player) || yellowRepo.existsByPlayer(player)) {
            throw new ConflictException("El jugador tiene goles, tarjetas, estadísticas o sanciones cargadas; no se puede eliminar");
        }
    }

    /** Removes a player (used when approving a removal request). */
    @Transactional
    public void removePlayer(Player player) {
        checkRemovable(player);
        playerRepo.delete(player);
    }

    /** Jersey number: cosmetic, the captain changes it directly. */
    @Transactional
    public ResponseDtos.PlayerRes setJersey(Long teamId, Long playerId, int jerseyNo, Authentication auth) {
        var team = ownedTeam(teamId, auth);
        var player = playerRepo.findById(playerId).orElseThrow(() -> NotFoundException.of("Jugador", playerId));
        if (player.getTeam() == null || !player.getTeam().getId().equals(team.getId())) throw NotFoundException.of("Jugador", playerId);
        player.setJerseyNo(jerseyNo);
        return ResponseDtos.PlayerRes.from(player);
    }

    /** A team in review is only visible to its captain and the admin. */
    @Transactional(readOnly = true)
    public ResponseDtos.TeamRef team(Long teamId, Authentication auth) {
        var t = findTeam(teamId);
        if (!t.isApproved() && !canSee(t, auth)) throw NotFoundException.of("Equipo", teamId);
        return ResponseDtos.TeamRef.from(t);
    }

    private boolean canSee(Team t, Authentication auth) {
        return auth != null && auth.isAuthenticated()
                && (isAdmin(auth) || t.getCaptain().getUsername().equals(auth.getName()));
    }

    /** Tournaments a team is registered in, newest first. */
    public List<ResponseDtos.TeamRegistrationRes> registrations(Long teamId) {
        return regRepo.findByTeamOrderByIdDesc(findTeam(teamId)).stream()
                .map(ResponseDtos.TeamRegistrationRes::from).toList();
    }

    /** Public roster: approved players only (one with a removal request still plays until approved). */
    public List<ResponseDtos.PlayerRes> players(Long teamId) {
        var t = findTeam(teamId);
        if (!t.isApproved()) throw NotFoundException.of("Equipo", teamId);
        return playerRepo.findByTeamAndApprovedTrueOrderByJerseyNoAscFullNameAsc(t).stream()
                .map(ResponseDtos.PlayerRes::from).toList();
    }

    /** Full roster for its captain or the admin, pending players included. */
    @Transactional(readOnly = true)
    public List<ResponseDtos.PlayerRes> roster(Long teamId, Authentication auth) {
        return playerRepo.findByTeamOrderByJerseyNoAscFullNameAsc(ownedTeam(teamId, auth)).stream()
                .map(ResponseDtos.PlayerRes::from).toList();
    }

    @Transactional
    public List<ResponseDtos.PlayerRes> addPlayers(Long teamId, TeamDtos.AddPlayersReq req, Authentication auth) {
        var team = ownedTeam(teamId, auth);
        List<Player> list = new ArrayList<>();
        boolean needsApproval = !isAdmin(auth);
        for (var p : req.getPlayers()) {
            var pl = new Player(); pl.setTeam(team); pl.setFullName(p.getFullName()); pl.setJerseyNo(p.getJerseyNo());
            if (needsApproval) { pl.setApproved(false); pl.setRequestedAt(LocalDateTime.now()); }
            list.add(pl);
        }
        return playerRepo.saveAll(list).stream().map(ResponseDtos.PlayerRes::from).toList();
    }

    @Transactional
    /** The team joins the tournament unassigned; the admin later places it in a category/zone. */
    public ResponseDtos.RegistrationRes registerTeamForTournament(Long teamId, Long tournamentId, Authentication auth) {
        var team = ownedTeam(teamId, auth);
        var t = findTournament(tournamentId);
        // Captains register only while the admin keeps registration open; the organizer can register at any time
        if (!isAdmin(auth) && t.getStatus() != Tournament.Status.OPEN) {
            throw new ConflictException("La inscripción al campeonato no está abierta");
        }
        if (!team.isApproved()) throw new ConflictException("Tu equipo todavía está en revisión; podrás inscribirlo cuando el organizador lo apruebe");
        if (regRepo.existsByTournamentAndTeam(t, team)) {
            throw new ConflictException("El equipo ya está inscripto en este campeonato");
        }
        var r = new Registration(); r.setTournament(t); r.setTeam(team);
        r.setApproved(isAdmin(auth));
        if (!isAdmin(auth)) r.setRequestedAt(LocalDateTime.now());
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
