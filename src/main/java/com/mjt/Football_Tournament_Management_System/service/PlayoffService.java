package com.mjt.Football_Tournament_Management_System.service;

import com.mjt.Football_Tournament_Management_System.dto.AdminDtos;
import com.mjt.Football_Tournament_Management_System.dto.ResponseDtos;
import com.mjt.Football_Tournament_Management_System.entire.MatchGame;
import com.mjt.Football_Tournament_Management_System.entire.Team;
import com.mjt.Football_Tournament_Management_System.entire.Zone;
import com.mjt.Football_Tournament_Management_System.exception.BadRequestException;
import com.mjt.Football_Tournament_Management_System.exception.ConflictException;
import com.mjt.Football_Tournament_Management_System.exception.NotFoundException;
import com.mjt.Football_Tournament_Management_System.repo.MatchRepository;
import com.mjt.Football_Tournament_Management_System.repo.TeamRepository;
import com.mjt.Football_Tournament_Management_System.repo.TournamentRepository;
import com.mjt.Football_Tournament_Management_System.repo.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Promotion and final matches. Each side is either a fixed team or a rule "position N of zone X"
 * (any tournament), resolved against the zone standings when created or re-resolved.
 */
@Service
@RequiredArgsConstructor
public class PlayoffService {
    private final TournamentRepository tournamentRepo;
    private final ZoneRepository zoneRepo;
    private final TeamRepository teamRepo;
    private final MatchRepository matchRepo;
    private final StandingsService standingsService;

    @Transactional
    public ResponseDtos.MatchRes create(Long tournamentId, AdminDtos.SpecialMatchReq req) {
        var t = tournamentRepo.findById(tournamentId).orElseThrow(() -> NotFoundException.of("Campeonato", tournamentId));
        var m = new MatchGame();
        m.setTournament(t);
        m.setType(req.getType());
        m.setScheduledAt(req.getScheduledAt());
        applySlot(m, true, req.getHome());
        applySlot(m, false, req.getAway());
        checkDifferentTeams(m);
        return ResponseDtos.MatchRes.from(matchRepo.save(m));
    }

    /** Recomputes the rule-based sides from the current standings, while the match is not played. */
    @Transactional
    public ResponseDtos.MatchRes resolve(Long matchId) {
        var m = matchRepo.findById(matchId).orElseThrow(() -> NotFoundException.of("Partido", matchId));
        if (!m.isSpecial()) throw new ConflictException("Solo se recalculan promociones y finales");
        if (m.isFinished()) throw new ConflictException("El partido ya se jugó; no se puede recalcular");
        if (m.getHomeSourceZone() != null) m.setHomeTeam(teamAt(m.getHomeSourceZone(), m.getHomeSourcePosition()));
        if (m.getAwaySourceZone() != null) m.setAwayTeam(teamAt(m.getAwaySourceZone(), m.getAwaySourcePosition()));
        checkDifferentTeams(m);
        return ResponseDtos.MatchRes.from(m);
    }

    public List<ResponseDtos.MatchRes> list(Long tournamentId) {
        var t = tournamentRepo.findById(tournamentId).orElseThrow(() -> NotFoundException.of("Campeonato", tournamentId));
        return matchRepo.findByTournamentAndTypeInOrderByScheduledAtAsc(t,
                        List.of(MatchGame.Type.PROMOTION, MatchGame.Type.FINAL)).stream()
                .sorted(MatchGame.BY_DATE).map(ResponseDtos.MatchRes::from).toList();
    }

    private void applySlot(MatchGame m, boolean home, AdminDtos.SlotReq slot) {
        boolean byTeam = slot.getTeamId() != null;
        boolean byRule = slot.getZoneId() != null || slot.getPosition() != null;
        if (byTeam == byRule || (byRule && (slot.getZoneId() == null || slot.getPosition() == null))) {
            throw new BadRequestException("Cada lado lleva un equipo, o una zona y una posición");
        }
        Team team;
        Zone zone = null;
        if (byTeam) {
            team = teamRepo.findById(slot.getTeamId()).orElseThrow(() -> NotFoundException.of("Equipo", slot.getTeamId()));
        } else {
            zone = zoneRepo.findById(slot.getZoneId()).orElseThrow(() -> NotFoundException.of("Zona", slot.getZoneId()));
            team = teamAt(zone, slot.getPosition());
        }
        if (home) {
            m.setHomeTeam(team); m.setHomeSourceZone(zone); m.setHomeSourcePosition(byTeam ? null : slot.getPosition());
        } else {
            m.setAwayTeam(team); m.setAwaySourceZone(zone); m.setAwaySourcePosition(byTeam ? null : slot.getPosition());
        }
    }

    private Team teamAt(Zone zone, int position) {
        var table = standingsService.zoneStandings(zone.getId());
        if (position > table.size()) {
            throw new ConflictException("La zona " + zone.getName() + " tiene " + table.size()
                    + " equipos; no hay posición " + position);
        }
        var teamId = table.get(position - 1).team().id();
        return teamRepo.findById(teamId).orElseThrow(() -> NotFoundException.of("Equipo", teamId));
    }

    private static void checkDifferentTeams(MatchGame m) {
        if (m.getHomeTeam().getId().equals(m.getAwayTeam().getId())) {
            throw new ConflictException("Un equipo no puede jugar contra sí mismo");
        }
    }
}
