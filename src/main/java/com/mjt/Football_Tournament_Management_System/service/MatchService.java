package com.mjt.Football_Tournament_Management_System.service;


import com.mjt.Football_Tournament_Management_System.dto.AdminDtos;
import com.mjt.Football_Tournament_Management_System.dto.ResponseDtos;
import com.mjt.Football_Tournament_Management_System.entire.DataSource;
import com.mjt.Football_Tournament_Management_System.entire.MatchEvent;
import com.mjt.Football_Tournament_Management_System.entire.MatchGame;
import com.mjt.Football_Tournament_Management_System.entire.Player;
import com.mjt.Football_Tournament_Management_System.repo.MatchEventRepository;
import com.mjt.Football_Tournament_Management_System.repo.MatchRepository;
import com.mjt.Football_Tournament_Management_System.repo.PlayerRepository;
import com.mjt.Football_Tournament_Management_System.exception.BadRequestException;
import com.mjt.Football_Tournament_Management_System.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MatchService {
    private final MatchRepository matchRepo;
    private final MatchEventRepository eventRepo;
    private final PlayerRepository playerRepo;

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
            throw new BadRequestException("En un walkover no se cargan goles ni tarjetas");
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
            // Walkover: whoever showed up wins 3-0; if neither did, 0-0 and nobody scores points
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
        m.setSource(DataSource.WEB);
        var saved = matchRepo.save(m);
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

    /** Deletes a match together with its goals and cards. */
    @Transactional
    public void delete(Long matchId) {
        var m = matchRepo.findById(matchId).orElseThrow(() -> NotFoundException.of("Partido", matchId));
        eventRepo.deleteByMatch(m);
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
