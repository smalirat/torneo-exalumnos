package com.mjt.Football_Tournament_Management_System.service;


import com.mjt.Football_Tournament_Management_System.dto.ResponseDtos;
import com.mjt.Football_Tournament_Management_System.dto.TeamDtos;
import com.mjt.Football_Tournament_Management_System.dto.ViewDtos;
import com.mjt.Football_Tournament_Management_System.entire.AppUser;
import com.mjt.Football_Tournament_Management_System.entire.MatchGame;
import com.mjt.Football_Tournament_Management_System.entire.Person;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TeamService {
    private final TeamRepository teamRepo;
    private final PlayerRepository playerRepo;
    private final PersonRepository personRepo;
    private final BannedService banned;
    private final TournamentRepository tournamentRepo;
    private final RegistrationRepository regRepo;
    private final MatchRepository matchRepo;
    private final AppUserRepository userRepo;
    private final MatchEventRepository eventRepo;
    private final MatchAppearanceRepository appearanceRepo;
    private final PlayerSeasonStatRepository statRepo;
    private final SanctionRepository sanctionRepo;
    private final YellowCardTallyRepository yellowRepo;
    private final ChampionRepository championRepo;
    private final TeamCrestRepository crestRepo;

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
        return teams.stream().map(t -> ResponseDtos.TeamSummaryRes.of(t, playerRepo.countByTeamAndLeftAtIsNullAndApprovedTrue(t))).toList();
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

    /** Only the admin deletes a team (with its roster), and only while it is not registered in any tournament. */
    @Transactional
    public void deleteTeam(Long teamId, Authentication auth) {
        if (!isAdmin(auth)) throw new AccessDeniedException("Solo el organizador puede eliminar equipos");
        removeTeam(ownedTeam(teamId, auth));
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
        var files = playerRepo.findByTeamOrderByJerseyNoAscFullNameAsc(t);
        var people = files.stream().map(Player::getPerson).distinct().toList();
        playerRepo.deleteAll(files);
        playerRepo.flush();
        for (var person : people) if (playerRepo.findByPerson(person).isEmpty()) personRepo.delete(person);
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
        var img = SafeImages.read(file, CREST_MAX_BYTES, "El escudo");
        crestRepo.save(new TeamCrest(t.getId(), img.contentType(), img.data()));
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
        if (player.getTeam() == null || !player.getTeam().getId().equals(team.getId()) || player.getLeftAt() != null) {
            throw NotFoundException.of("Jugador", playerId);
        }
        if (!player.isApproved()) { // withdrawing an addition that was waiting for the admin
            deleteFile(player);
            return;
        }
        if (!isAdmin(auth) && team.isApproved()) {
            // The rules: a player leaves only if the team keeps the minimum; otherwise it is a replacement (add one first)
            long staying = playerRepo.findByTeamAndApprovedTrueAndLeftAtIsNullOrderByJerseyNoAscFullNameAsc(team).stream()
                    .filter(p -> p.getRemovalRequestedAt() == null && !p.getId().equals(player.getId())).count();
            if (staying < MIN_ROSTER) {
                throw new ConflictException("El equipo quedaría con menos de " + MIN_ROSTER
                        + " jugadores: para darlo de baja, agregá antes a su reemplazo");
            }
            player.setRemovalRequestedAt(LocalDateTime.now()); // the admin decides
            return;
        }
        removePlayer(player);
    }

    /** The rules: from 11 (save exceptions) up to 24 players per team */
    static final int MIN_ROSTER = 11;
    static final int MAX_ROSTER = 24;

    /**
     * The team's active edition (registration open or being played) whose next match day to play is one of its last
     * two (no additions then); its name, else empty.
     */
    private Optional<String> lastMatchDays(Team team) {
        for (var reg : regRepo.findByTeamOrderByIdDesc(team)) {
            var t = reg.getTournament();
            if (reg.getZone() == null || !(t.getStatus() == Tournament.Status.OPEN || t.getStatus() == Tournament.Status.LIVE)) continue;
            var rounds = matchRepo.findByZoneOrderByScheduledAtAsc(reg.getZone()).stream().filter(m -> m.getRound() != null).toList();
            int last = rounds.stream().mapToInt(MatchGame::getRound).max().orElse(0);
            var next = rounds.stream().filter(m -> !m.isFinished()).mapToInt(MatchGame::getRound).min();
            if (last > 0 && next.isPresent() && next.getAsInt() >= last - 1) return Optional.of(t.getName());
        }
        return Optional.empty();
    }

    /**
     * The rules: additions asked for by Thursday 18 hs play that weekend's match day; later ones, the next. For a player
     * added while their team plays an edition, the first match day (of their zone) they can play; null otherwise.
     */
    public Integer eligibleFromRound(Player p) {
        if (p.getRegisteredAt() == null || p.getLeftAt() != null) return null;
        for (var reg : regRepo.findByTeamOrderByIdDesc(p.getTeam())) {
            var t = reg.getTournament();
            if (reg.getZone() == null || !(t.getStatus() == Tournament.Status.OPEN || t.getStatus() == Tournament.Status.LIVE)) continue;
            var byRound = new TreeMap<Integer, LocalDateTime>();
            for (var m : matchRepo.findByZoneOrderByScheduledAtAsc(reg.getZone())) {
                if (m.getRound() == null || m.getScheduledAt() == null) continue;
                byRound.merge(m.getRound(), m.getScheduledAt(), (a, b) -> a.isBefore(b) ? a : b);
            }
            if (byRound.isEmpty()) return null;
            for (var e : byRound.entrySet()) {
                if (!p.getRegisteredAt().isAfter(thursdayCutoff(e.getValue()))) {
                    // Only worth saying when they joined after the edition started
                    return e.getKey().equals(byRound.firstKey()) ? null : e.getKey();
                }
            }
            return null; // after the last match day's deadline
        }
        return null;
    }

    /** 18 hs of the Thursday before a match (a match on a Thursday: the one of the week before) */
    static LocalDateTime thursdayCutoff(LocalDateTime match) {
        var day = match.toLocalDate();
        int back = (day.getDayOfWeek().getValue() - java.time.DayOfWeek.THURSDAY.getValue() + 7) % 7;
        if (back == 0) back = 7;
        return day.minusDays(back).atTime(18, 0);
    }

    /**
     * The player leaves the team (removal approved or done by the admin). With goals, cards, stats or sanctions the
     * file is closed and kept (that history belongs to the team); without any, it is deleted.
     */
    @Transactional
    public void removePlayer(Player player) {
        if (eventRepo.existsByPlayer(player) || appearanceRepo.existsByPlayer(player) || statRepo.existsByPlayer(player)
                || sanctionRepo.existsByPlayer(player) || yellowRepo.existsByPlayer(player)) {
            player.setLeftAt(LocalDate.now());
            player.setRemovalRequestedAt(null);
        } else {
            deleteFile(player);
        }
    }

    /** Deletes a file without history; the person goes too if it has no other file */
    @Transactional
    public void deleteFile(Player player) {
        var person = player.getPerson();
        playerRepo.delete(player);
        playerRepo.flush();
        if (playerRepo.findByPerson(person).isEmpty()) personRepo.delete(person);
    }

    /** Jersey number and DNI: the captain changes them directly. */
    @Transactional
    public ResponseDtos.PlayerRes updatePlayer(Long teamId, Long playerId, TeamDtos.PlayerUpdateReq req, Authentication auth) {
        var team = ownedTeam(teamId, auth);
        var player = playerRepo.findById(playerId).orElseThrow(() -> NotFoundException.of("Jugador", playerId));
        if (player.getTeam() == null || !player.getTeam().getId().equals(team.getId())) throw NotFoundException.of("Jugador", playerId);
        if (req.getJerseyNo() != null) player.setJerseyNo(req.getJerseyNo());
        if (req.getDni() != null) setDni(player, req.getDni());
        return ResponseDtos.PlayerRes.withDni(player, transferFrom(player), banned.isBanned(player.getDni()), eligibleFromRound(player));
    }

    /**
     * The DNI is the person's. If another person already has it, it is the same person: both are merged (e.g. the
     * same player imported in two seasons without DNI), unless that leaves them in two rosters at once.
     */
    private void setDni(Player player, String raw) {
        var dni = normalizeDni(raw);
        var person = player.getPerson();
        if (Objects.equals(dni, person.getDni())) return;
        banned.checkAllowed(dni);
        var other = dni == null ? null : personRepo.findByDni(dni).orElse(null);
        if (other == null) {
            person.setDni(dni);
            return;
        }
        for (var o : playerRepo.findByPerson(other)) {
            if (o.getLeftAt() != null || !o.isApproved()) continue;
            if (o.getTeam().getId().equals(player.getTeam().getId())) {
                throw new ConflictException("El DNI " + dni + " es de " + o.getFullName() + ", que ya está en este plantel");
            }
            if (player.getLeftAt() == null) {
                throw new ConflictException("El DNI " + dni + " es de " + o.getFullName() + ", que está en el plantel de "
                        + o.getTeam().getName() + ". Para pasarlo, agregalo con su DNI (queda como pase)");
            }
        }
        for (var mine : playerRepo.findByPerson(person)) mine.setPerson(other);
        playerRepo.flush();
        personRepo.delete(person);
    }

    private static String normalizeDni(String raw) { return Dni.normalize(raw); }

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

    /**
     * Public roster: approved players still in the team (one with a removal request still plays until approved).
     * formerToo adds the ones that left, to edit old results.
     */
    public List<ResponseDtos.PlayerRes> players(Long teamId, boolean formerToo) {
        var t = findTeam(teamId);
        if (!t.isApproved()) throw NotFoundException.of("Equipo", teamId);
        var list = formerToo
                ? playerRepo.findByTeamOrderByJerseyNoAscFullNameAsc(t).stream().filter(Player::isApproved).toList()
                : playerRepo.findByTeamAndApprovedTrueAndLeftAtIsNullOrderByJerseyNoAscFullNameAsc(t);
        return list.stream().map(ResponseDtos.PlayerRes::from).toList();
    }

    /** Roster now for its captain or the admin: pending additions (and transfers) included, the ones that left not. */
    @Transactional(readOnly = true)
    public List<ResponseDtos.PlayerRes> roster(Long teamId, Authentication auth) {
        return playerRepo.findByTeamAndLeftAtIsNullOrderByJerseyNoAscFullNameAsc(ownedTeam(teamId, auth)).stream()
                .map(p -> ResponseDtos.PlayerRes.withDni(p, transferFrom(p), banned.isBanned(p.getDni()), eligibleFromRound(p))).toList();
    }

    /**
     * New files in the roster. A DNI that is already someone's is that person: if they are in another team it is a
     * transfer, allowed while they did not play for that team in an edition this one is registered in. A captain's
     * additions (transfers included) wait for the admin; the admin's apply right away and close the old file.
     */
    @Transactional
    public List<ResponseDtos.PlayerRes> addPlayers(Long teamId, TeamDtos.AddPlayersReq req, Authentication auth) {
        var team = ownedTeam(teamId, auth);
        boolean admin = isAdmin(auth);
        int inRoster = playerRepo.findByTeamAndLeftAtIsNullOrderByJerseyNoAscFullNameAsc(team).size();
        if (inRoster + req.getPlayers().size() > MAX_ROSTER) {
            throw new ConflictException("Un plantel puede tener hasta " + MAX_ROSTER + " jugadores (tiene " + inRoster + ")");
        }
        // The rules: players can be added until 2 match days before the end of the tournament (the admin can make exceptions)
        if (!admin) lastMatchDays(team).ifPresent(edition -> {
            throw new ConflictException("No se pueden incorporar jugadores en las últimas 2 fechas del " + edition);
        });
        var dnis = new HashSet<String>();
        var out = new ArrayList<ResponseDtos.PlayerRes>();
        for (var item : req.getPlayers()) {
            var dni = item.getDni() == null ? null : normalizeDni(item.getDni());
            banned.checkAllowed(dni);
            if (dni != null && !dnis.add(dni)) throw new ConflictException("El DNI " + dni + " está repetido");
            var person = dni == null ? null : personRepo.findByDni(dni).orElse(null);
            if (person != null) {
                for (var f : playerRepo.findByPerson(person)) {
                    if (f.getLeftAt() == null && f.getTeam().getId().equals(team.getId())) {
                        throw new ConflictException(f.getFullName() + " (DNI " + dni + ") ya está en el plantel");
                    }
                }
            } else {
                person = personRepo.save(new Person(null, item.getFullName().trim(), dni));
            }
            var pl = new Player();
            pl.setTeam(team); pl.setPerson(person); pl.setJerseyNo(item.getJerseyNo());
            pl.setFullName(dni != null && person.getFullName() != null ? person.getFullName() : item.getFullName().trim());
            pl.setJoinedAt(LocalDate.now());
            pl.setRegisteredAt(LocalDateTime.now(MatchGame.LEAGUE_ZONE));
            checkTransfer(pl);
            if (!admin) { pl.setApproved(false); pl.setRequestedAt(LocalDateTime.now()); }
            playerRepo.save(pl);
            if (admin) closeOtherFiles(pl);
            out.add(ResponseDtos.PlayerRes.withDni(pl, transferFrom(pl), false, eligibleFromRound(pl)));
        }
        return out;
    }

    /** The admin approves an addition: if it is a transfer it is checked again and the old file is closed */
    @Transactional
    public void approveAddition(Player p) {
        checkTransfer(p);
        p.setApproved(true);
        p.setRequestedAt(null);
        p.setJoinedAt(LocalDate.now());
        closeOtherFiles(p);
    }

    /** For an addition still waiting: the team the person would leave (their file in another roster), else null */
    public String transferFrom(Player p) {
        if (p.isApproved()) return null;
        return playerRepo.findByPerson(p.getPerson()).stream()
                .filter(o -> !Objects.equals(o.getId(), p.getId()) && o.isActive() && !o.getTeam().getId().equals(p.getTeam().getId()))
                .map(o -> o.getTeam().getName()).findFirst().orElse(null);
    }

    /**
     * Nobody plays the same edition for two teams: no transfer if they already played it for another team (ticked as
     * played in a match sheet, or with a goal or card).
     */
    private void checkTransfer(Player newFile) {
        var editions = regRepo.findByTeamOrderByIdDesc(newFile.getTeam()).stream().map(Registration::getTournament).toList();
        for (var other : playerRepo.findByPerson(newFile.getPerson())) {
            if (Objects.equals(other.getId(), newFile.getId()) || other.getTeam().getId().equals(newFile.getTeam().getId())) continue;
            for (var edition : editions) {
                if (appearanceRepo.existsByPlayerAndMatch_Tournament(other, edition) || eventRepo.existsByPlayerAndMatch_Tournament(other, edition)) {
                    throw new ConflictException(newFile.getFullName() + " ya jugó el " + edition.getName() + " con "
                            + other.getTeam().getName() + ": esa edición no puede jugarla en otro equipo");
                }
            }
        }
    }

    /** The person keeps a single file in a roster: the others (another team's) are closed */
    private void closeOtherFiles(Player kept) {
        for (var o : playerRepo.findByPerson(kept.getPerson())) {
            if (!Objects.equals(o.getId(), kept.getId()) && o.getLeftAt() == null && o.isApproved()) {
                o.setLeftAt(LocalDate.now());
                o.setRemovalRequestedAt(null);
            }
        }
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
