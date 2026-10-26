package com.mjt.Football_Tournament_Management_System.service;


import com.mjt.Football_Tournament_Management_System.dto.AdminDtos;
import com.mjt.Football_Tournament_Management_System.dto.ResponseDtos;
import com.mjt.Football_Tournament_Management_System.entire.DataSource;
import com.mjt.Football_Tournament_Management_System.entire.MatchAppearance;
import com.mjt.Football_Tournament_Management_System.entire.MatchEvent;
import com.mjt.Football_Tournament_Management_System.entire.MatchGame;
import com.mjt.Football_Tournament_Management_System.entire.Player;
import com.mjt.Football_Tournament_Management_System.repo.MatchAppearanceRepository;
import com.mjt.Football_Tournament_Management_System.repo.MatchEventRepository;
import com.mjt.Football_Tournament_Management_System.repo.MatchRepository;
import com.mjt.Football_Tournament_Management_System.repo.PlayerRepository;
import com.mjt.Football_Tournament_Management_System.exception.BadRequestException;
import com.mjt.Football_Tournament_Management_System.exception.ConflictException;
import com.mjt.Football_Tournament_Management_System.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MatchService {
    private final MatchRepository matchRepo;
    private final MatchEventRepository eventRepo;
    private final PlayerRepository playerRepo;
    private final PlayoffService playoffs;
    private final MatchAppearanceRepository appearanceRepo;
    private final com.mjt.Football_Tournament_Management_System.repo.MatchProtestRepository protestRepo;

    /**
     * Loads the result from per-player events (goals, own goals, cards); the score is derived from them.
     * Walkover (a team absent): no events allowed, the team that showed up wins 3-0.
     * Re-scoring a match replaces its previous events.
     */
    @Transactional
    public ResponseDtos.MatchRes updateScore(Long id, AdminDtos.ScoreUpdateReq req) {
        var m = matchRepo.findById(id).orElseThrow(() -> NotFoundException.of("Partido", id));
        boolean home = req.isHomePresent(), away = req.isAwayPresent();
        var events = toEvents(m, req.getEvents());
        if ((!home || !away) && !events.isEmpty()) {
            throw new BadRequestException("En un abandono no se cargan goles ni tarjetas");
        }

        eventRepo.deleteByMatch(m);
        eventRepo.flush();
        eventRepo.saveAll(events);

        if (home && away) {
            int homeFromEvents = count(events, m, true, MatchEvent.Type.GOAL) + count(events, m, false, MatchEvent.Type.OWN_GOAL);
            int awayFromEvents = count(events, m, false, MatchEvent.Type.GOAL) + count(events, m, true, MatchEvent.Type.OWN_GOAL);
            boolean explicit = req.getHomeGoals() != null || req.getAwayGoals() != null;
            if (explicit && (req.getHomeGoals() == null || req.getAwayGoals() == null)) {
                throw new BadRequestException("Indicá los goles de los dos equipos");
            }
            if (explicit && (homeFromEvents > req.getHomeGoals() || awayFromEvents > req.getAwayGoals())) {
                throw new BadRequestException("Hay más goles cargados por jugador que en el resultado");
            }
            m.setHomeGoals(explicit ? req.getHomeGoals() : homeFromEvents);
            m.setAwayGoals(explicit ? req.getAwayGoals() : awayFromEvents);
        } else {
            // Abandono: whoever showed up wins 3-0; if neither did, 0-0 and nobody scores points
            m.setHomeGoals(home ? MatchGame.WALKOVER_GOALS : 0);
            m.setAwayGoals(away ? MatchGame.WALKOVER_GOALS : 0);
        }
        m.setHomePresent(home); m.setAwayPresent(away);
        m.setHomeYellowCards(count(events, m, true, MatchEvent.Type.YELLOW_CARD));
        m.setHomeRedCards(count(events, m, true, MatchEvent.Type.RED_CARD));
        m.setAwayYellowCards(count(events, m, false, MatchEvent.Type.YELLOW_CARD));
        m.setAwayRedCards(count(events, m, false, MatchEvent.Type.RED_CARD));
        applyPenalties(m, req, home && away);
        m.setStatus(MatchGame.Status.FINISHED);
        m.setSuspended(false);
        m.setRemainingMinutes(null);
        m.setSource(DataSource.WEB);
        var saved = matchRepo.save(m);
        saveAppearances(saved, req.getPlayed(), events, home && away);
        // The tables changed: definitions by position follow them
        if (!saved.isSpecial()) playoffs.refreshRules(saved.getTournament());
        return ResponseDtos.MatchRes.from(saved, MatchGame.pendingWalkovers(matchRepo.findByTournament(saved.getTournament())));
    }

    /** A drawn promotion/final must be decided on penalties; any other match cannot have them. */
    private static void applyPenalties(MatchGame m, AdminDtos.ScoreUpdateReq req, boolean bothPresent) {
        boolean given = req.getHomePenalties() != null || req.getAwayPenalties() != null;
        boolean needed = m.isSpecial() && bothPresent && m.getHomeGoals().equals(m.getAwayGoals());
        if (!needed) {
            if (given) throw new BadRequestException("Solo una promoción o final empatada se define por penales");
            m.setHomePenalties(null); m.setAwayPenalties(null);
            return;
        }
        if (req.getHomePenalties() == null || req.getAwayPenalties() == null) {
            throw new BadRequestException("El partido terminó empatado: hay que cargar los penales");
        }
        if (req.getHomePenalties().equals(req.getAwayPenalties())) {
            throw new BadRequestException("Los penales no pueden terminar empatados");
        }
        m.setHomePenalties(req.getHomePenalties()); m.setAwayPenalties(req.getAwayPenalties());
    }

    @Transactional
    public ResponseDtos.MatchRes updateDetails(Long id, AdminDtos.MatchDetailsReq req) {
        var m = matchRepo.findById(id).orElseThrow(() -> NotFoundException.of("Partido", id));
        m.setScheduledAt(req.getScheduledAt());
        m.setRound(req.getRound());
        m.setRoundLabel(TournamentService.blankToNull(req.getRoundLabel()));
        m.setVenue(TournamentService.blankToNull(req.getVenue()));
        m.setPitch(TournamentService.blankToNull(req.getPitch()));
        m.setNotes(TournamentService.blankToNull(req.getNotes()));
        m.setSource(DataSource.WEB);
        return ResponseDtos.MatchRes.from(m, MatchGame.pendingWalkovers(matchRepo.findByTournament(m.getTournament())));
    }

    /**
     * Who played: the ticked players (each must be of one of the two teams) plus everyone with a goal or card.
     * An abandono has nobody. played null keeps the ones already saved.
     */
    private void saveAppearances(MatchGame m, List<Long> played, List<MatchEvent> events, boolean bothPresent) {
        var ids = new LinkedHashSet<Long>();
        if (bothPresent) {
            if (played != null) ids.addAll(played);
            else appearanceRepo.findByMatch(m).forEach(a -> ids.add(a.getPlayer().getId()));
            events.forEach(e -> ids.add(e.getPlayer().getId()));
        }
        appearanceRepo.deleteByMatch(m);
        appearanceRepo.flush();
        for (var id : ids) {
            var p = playerRepo.findById(id).orElseThrow(() -> NotFoundException.of("Jugador", id));
            var team = p.getTeam();
            if (team == null || !(team.getId().equals(m.getHomeTeam().getId()) || team.getId().equals(m.getAwayTeam().getId()))) {
                throw new BadRequestException("El jugador " + p.getFullName() + " no pertenece a ninguno de los dos equipos");
            }
            appearanceRepo.save(new MatchAppearance(null, m, p, team));
        }
    }

    public List<ResponseDtos.AppearanceRes> appearances(Long matchId) {
        var m = matchRepo.findById(matchId).orElseThrow(() -> NotFoundException.of("Partido", matchId));
        return appearanceRepo.findByMatch(m).stream()
                .map(a -> new ResponseDtos.AppearanceRes(a.getPlayer().getId(), a.getTeam().getId())).toList();
    }

    /**
     * The match was suspended while being played (the rules: the minutes left are played another day). It keeps the
     * score so far and does not count; loading its final result ends the suspension.
     */
    @Transactional
    public ResponseDtos.MatchRes suspend(Long matchId, AdminDtos.SuspendReq req) {
        var m = matchRepo.findById(matchId).orElseThrow(() -> NotFoundException.of("Partido", matchId));
        if (m.isFinished()) throw new ConflictException("El partido ya tiene resultado final; para suspenderlo, borrá el resultado");
        m.setSuspended(true);
        m.setHomeGoals(req.getHomeGoals());
        m.setAwayGoals(req.getAwayGoals());
        m.setRemainingMinutes(req.getRemainingMinutes());
        m.setSource(DataSource.WEB);
        return ResponseDtos.MatchRes.from(m);
    }

    @Transactional
    public ResponseDtos.MatchRes unsuspend(Long matchId) {
        var m = matchRepo.findById(matchId).orElseThrow(() -> NotFoundException.of("Partido", matchId));
        if (m.isSuspended()) {
            m.setSuspended(false);
            m.setRemainingMinutes(null);
            m.setHomeGoals(0);
            m.setAwayGoals(0);
        }
        return ResponseDtos.MatchRes.from(m);
    }

    /** Two matches swap their time, field and pitch (captains agreed and the organizer makes it official) */
    @Transactional
    public List<ResponseDtos.MatchRes> swapSchedule(Long matchId, Long otherId) {
        var a = matchRepo.findById(matchId).orElseThrow(() -> NotFoundException.of("Partido", matchId));
        var b = matchRepo.findById(otherId).orElseThrow(() -> NotFoundException.of("Partido", otherId));
        if (a.getId().equals(b.getId())) throw new BadRequestException("Elegí otro partido");
        if (!a.getTournament().getId().equals(b.getTournament().getId())) throw new BadRequestException("Los partidos son de distintas ediciones");
        var when = a.getScheduledAt(); var venue = a.getVenue(); var pitch = a.getPitch();
        a.setScheduledAt(b.getScheduledAt()); a.setVenue(b.getVenue()); a.setPitch(b.getPitch());
        b.setScheduledAt(when); b.setVenue(venue); b.setPitch(pitch);
        return List.of(ResponseDtos.MatchRes.from(a), ResponseDtos.MatchRes.from(b));
    }

    /** Deletes a match together with its goals, cards and who played. */
    @Transactional
    public void delete(Long matchId) {
        var m = matchRepo.findById(matchId).orElseThrow(() -> NotFoundException.of("Partido", matchId));
        eventRepo.deleteByMatch(m);
        appearanceRepo.deleteByMatch(m);
        protestRepo.deleteByMatch(m);
        matchRepo.delete(m);
    }

    public List<ResponseDtos.MatchEventRes> events(Long matchId) {
        var m = matchRepo.findById(matchId).orElseThrow(() -> NotFoundException.of("Partido", matchId));
        return eventRepo.findByMatchOrderByIdAsc(m).stream().map(ResponseDtos.MatchEventRes::from).toList();
    }

    private List<MatchEvent> toEvents(MatchGame m, List<AdminDtos.EventItem> items) {
        var out = new ArrayList<MatchEvent>();
        for (var item : items) {
            Player p = playerRepo.findById(item.getPlayerId())
                    .orElseThrow(() -> NotFoundException.of("Jugador", item.getPlayerId()));
            var team = p.getTeam();
            if (team == null || !(team.getId().equals(m.getHomeTeam().getId()) || team.getId().equals(m.getAwayTeam().getId()))) {
                throw new BadRequestException("El jugador " + p.getFullName() + " no pertenece a ninguno de los dos equipos");
            }
            out.add(new MatchEvent(null, m, p, team, item.getType()));
        }
        return out;
    }

    private static int count(List<MatchEvent> events, MatchGame m, boolean home, MatchEvent.Type type) {
        var teamId = (home ? m.getHomeTeam() : m.getAwayTeam()).getId();
        return (int) events.stream()
                .filter(e -> e.getType() == type && e.getTeam().getId().equals(teamId)).count();
    }
}
