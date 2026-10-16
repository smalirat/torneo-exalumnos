package com.mjt.Football_Tournament_Management_System.service;


import com.mjt.Football_Tournament_Management_System.dto.AdminDtos;
import com.mjt.Football_Tournament_Management_System.dto.ResponseDtos;
import com.mjt.Football_Tournament_Management_System.entire.*;
import com.mjt.Football_Tournament_Management_System.repo.*;
import com.mjt.Football_Tournament_Management_System.exception.BadRequestException;
import com.mjt.Football_Tournament_Management_System.exception.ConflictException;
import com.mjt.Football_Tournament_Management_System.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class TournamentService {
    private final TournamentRepository tournamentRepo;
    private final TeamRepository teamRepo;
    private final RegistrationRepository regRepo;
    private final MatchRepository matchRepo;
    private final ZoneRepository zoneRepo;
    private final CategoryRepository categoryRepo;
    private final SeasonRepository seasonRepo;

    public ResponseDtos.TournamentRes create(AdminDtos.CreateTournamentReq req) {
        var t = new Tournament();
        t.setName(req.getName()); t.setLocation(req.getLocation());
        t.setRegistrationStart(req.getRegistrationStart()); t.setRegistrationEnd(req.getRegistrationEnd());
        t.setMatchStart(req.getMatchStart()); t.setMatchEnd(req.getMatchEnd());
        setSeason(t, req.getSeasonYear(), req.getStage());
        return ResponseDtos.TournamentRes.from(tournamentRepo.save(t));
    }

    @Transactional
    public ResponseDtos.TournamentRes update(Long id, AdminDtos.UpdateTournamentReq req) {
        var t = findTournament(id);
        t.setName(req.getName()); t.setLocation(req.getLocation());
        t.setRegistrationStart(req.getRegistrationStart()); t.setRegistrationEnd(req.getRegistrationEnd());
        t.setMatchStart(req.getMatchStart()); t.setMatchEnd(req.getMatchEnd());
        t.setStatus(req.getStatus());
        setSeason(t, req.getSeasonYear(), req.getStage());
        return ResponseDtos.TournamentRes.from(t);
    }

    /** Only empty tournaments (no registrations, no matches) can be deleted; categories and zones go with it. */
    @Transactional
    public void delete(Long id) {
        var t = findTournament(id);
        if (regRepo.existsByTournament(t) || matchRepo.existsByTournament(t)) {
            throw new ConflictException("El campeonato tiene equipos o partidos; no se puede eliminar");
        }
        for (var c : categoryRepo.findByTournamentOrderByDisplayOrderAscIdAsc(t)) {
            zoneRepo.deleteAll(zoneRepo.findByCategoryOrderByDisplayOrderAscIdAsc(c));
            categoryRepo.delete(c);
        }
        tournamentRepo.delete(t);
    }

    /** Seasons, newest first, with their tournaments. */
    public List<ResponseDtos.SeasonRes> seasons() {
        return seasonRepo.findAllByOrderByYearDesc().stream()
                .map(s -> new ResponseDtos.SeasonRes(s.getId(), s.getYear(),
                        tournamentRepo.findBySeasonOrderByStageAscIdAsc(s).stream()
                                .map(ResponseDtos.TournamentRes::from).toList()))
                .toList();
    }

    /** Links the tournament to the season of that year (created on demand). One Apertura/Clausura per season. */
    private void setSeason(Tournament t, Integer year, Tournament.Stage stage) {
        if (year == null) {
            if (stage != null) throw new BadRequestException("Para indicar Apertura/Clausura hay que indicar el año");
            t.setSeason(null); t.setStage(null);
            return;
        }
        var season = seasonRepo.findByYear(year).orElseGet(() -> seasonRepo.save(new Season(null, year)));
        boolean changed = t.getSeason() == null || !t.getSeason().getId().equals(season.getId()) || t.getStage() != stage;
        if (stage != null && changed && tournamentRepo.existsBySeasonAndStage(season, stage)) {
            throw new ConflictException("La temporada " + year + " ya tiene " + (stage == Tournament.Stage.APERTURA ? "Apertura" : "Clausura"));
        }
        t.setSeason(season); t.setStage(stage);
    }

    public List<ResponseDtos.TournamentRes> findAll() {
        return tournamentRepo.findAll().stream().map(ResponseDtos.TournamentRes::from).toList();
    }

    public List<ResponseDtos.RegistrationRes> registrations(Long tournamentId) {
        return regRepo.findByTournament(findTournament(tournamentId)).stream()
                .map(ResponseDtos.RegistrationRes::from).toList();
    }

    public List<ResponseDtos.MatchRes> fixtures(Long tournamentId) {
        var all = matchRepo.findByTournament(findTournament(tournamentId));
        var pending = MatchGame.pendingWalkovers(all);
        return all.stream().sorted(MatchGame.BY_DATE).map(m -> ResponseDtos.MatchRes.from(m, pending)).toList();
    }

    @Transactional
    public List<ResponseDtos.MatchRes> createRoundRobinFixtures(Long zoneId, LocalDateTime start, int minutesBetween) {
        var zone = findZone(zoneId);
        var t = zone.getCategory().getTournament();
        var regs = regRepo.findByZone(zone);
        var teams = regs.stream().map(Registration::getTeam).toList();
        List<MatchGame> out = new ArrayList<>();
        if (teams.size() < 2) return List.of();

        var list = new ArrayList<>(teams);
        if (list.size() % 2 == 1) list.add(null); // null slot = BYE (team rests that round)
        int n = list.size(), rounds = n - 1;
        var cursor = start;

        for (int r = 0; r < rounds; r++) {
            for (int i = 0; i < n/2; i++) {
                var home = list.get(i); var away = list.get(n-1-i);
                if (home == null || away == null) continue;
                var m = new MatchGame();
                m.setTournament(t); m.setZone(zone); m.setRound(r + 1); m.setHomeTeam(home); m.setAwayTeam(away); m.setScheduledAt(cursor);
                out.add(m); cursor = cursor.plusMinutes(minutesBetween);
            }
            var fixed = list.get(0);
            var rot = new ArrayList<>(list.subList(1, n));
            java.util.Collections.rotate(rot, 1);
            list = new ArrayList<>(); list.add(fixed); list.addAll(rot);
        }
        var saved = matchRepo.saveAll(out);
        return saved.stream().map(ResponseDtos.MatchRes::from).toList();
    }

    @Transactional
    public List<ResponseDtos.MatchRes> createManualFixtures(AdminDtos.ManualFixtureReq req) {
        var zone = req.getZoneId() == null ? null : findZone(req.getZoneId());
        var t = zone != null ? zone.getCategory().getTournament() : findTournament(req.getTournamentId());
        List<MatchGame> ms = new ArrayList<>();
        for (var f : req.getFixtures()) {
            if (f.getHomeTeamId().equals(f.getAwayTeamId())) {
                throw new ConflictException("Un equipo no puede jugar contra sí mismo");
            }
            var m = new MatchGame();
            m.setTournament(t);
            m.setZone(zone);
            m.setHomeTeam(zone != null ? teamInZone(zone, f.getHomeTeamId()) : teamInTournament(t, f.getHomeTeamId()));
            m.setAwayTeam(zone != null ? teamInZone(zone, f.getAwayTeamId()) : teamInTournament(t, f.getAwayTeamId()));
            m.setScheduledAt(f.getScheduledAt());
            m.setRound(f.getRound()); m.setRoundLabel(blankToNull(f.getRoundLabel()));
            m.setVenue(blankToNull(f.getVenue())); m.setPitch(blankToNull(f.getPitch()));
            m.setNotes(blankToNull(f.getNotes()));
            ms.add(m);
        }
        var saved = matchRepo.saveAll(ms);
        return saved.stream().map(ResponseDtos.MatchRes::from).toList();
    }

    private Zone findZone(Long id) {
        return zoneRepo.findById(id).orElseThrow(() -> NotFoundException.of("Zona", id));
    }

    private Team teamInZone(Zone zone, Long teamId) {
        var team = teamRepo.findById(teamId).orElseThrow(() -> NotFoundException.of("Equipo", teamId));
        if (!regRepo.existsByZoneAndTeam(zone, team)) {
            throw new ConflictException("El equipo " + team.getName() + " no está inscripto en la zona");
        }
        return team;
    }

    /** Interzonal matches: the team must be registered (and placed in a zone) in the tournament. */
    private Team teamInTournament(Tournament t, Long teamId) {
        var team = teamRepo.findById(teamId).orElseThrow(() -> NotFoundException.of("Equipo", teamId));
        var placed = regRepo.findByTournament(t).stream()
                .anyMatch(r -> r.getTeam().getId().equals(teamId) && r.getZone() != null);
        if (!placed) throw new ConflictException("El equipo " + team.getName() + " no tiene zona en este campeonato");
        return team;
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private Tournament findTournament(Long id) {
        return tournamentRepo.findById(id).orElseThrow(() -> NotFoundException.of("Campeonato", id));
    }
}
