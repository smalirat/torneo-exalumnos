package com.mjt.Football_Tournament_Management_System.service;


import com.mjt.Football_Tournament_Management_System.dto.AdminDtos;
import com.mjt.Football_Tournament_Management_System.dto.ResponseDtos;
import com.mjt.Football_Tournament_Management_System.entire.*;
import com.mjt.Football_Tournament_Management_System.repo.*;
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
    private final NotificationRepository notificationRepo;

    public ResponseDtos.TournamentRes create(AdminDtos.CreateTournamentReq req) {
        var t = new Tournament();
        t.setName(req.getName()); t.setLocation(req.getLocation());
        t.setRegistrationStart(req.getRegistrationStart()); t.setRegistrationEnd(req.getRegistrationEnd());
        t.setMatchStart(req.getMatchStart()); t.setMatchEnd(req.getMatchEnd());
        return ResponseDtos.TournamentRes.from(tournamentRepo.save(t));
    }

    public List<ResponseDtos.TournamentRes> findAll() {
        return tournamentRepo.findAll().stream().map(ResponseDtos.TournamentRes::from).toList();
    }

    public List<ResponseDtos.RegistrationRes> registrations(Long tournamentId) {
        return regRepo.findByTournament(findTournament(tournamentId)).stream()
                .map(ResponseDtos.RegistrationRes::from).toList();
    }

    public List<ResponseDtos.MatchRes> fixtures(Long tournamentId) {
        return matchRepo.findByTournament(findTournament(tournamentId)).stream()
                .map(ResponseDtos.MatchRes::from).toList();
    }

    @Transactional
    public List<ResponseDtos.MatchRes> createRoundRobinFixtures(Long tournamentId, LocalDateTime start, int minutesBetween) {
        var t = findTournament(tournamentId);
        var regs = regRepo.findByTournament(t);
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
                m.setTournament(t); m.setHomeTeam(home); m.setAwayTeam(away); m.setScheduledAt(cursor);
                out.add(m); cursor = cursor.plusMinutes(minutesBetween);
            }
            var fixed = list.get(0);
            var rot = new ArrayList<>(list.subList(1, n));
            java.util.Collections.rotate(rot, 1);
            list = new ArrayList<>(); list.add(fixed); list.addAll(rot);
        }
        var saved = matchRepo.saveAll(out);
        notificationRepo.save(new Notification(null, "tournament:"+t.getId(), "FIXTURE",
                "Fixtures published: "+saved.size(), null));
        return saved.stream().map(ResponseDtos.MatchRes::from).toList();
    }

    @Transactional
    public List<ResponseDtos.MatchRes> createManualFixtures(AdminDtos.ManualFixtureReq req) {
        var t = findTournament(req.getTournamentId());
        List<MatchGame> ms = new ArrayList<>();
        for (var f : req.getFixtures()) {
            var m = new MatchGame();
            m.setTournament(t);
            m.setHomeTeam(teamRepo.findById(f.getHomeTeamId()).orElseThrow(() -> NotFoundException.of("Equipo", f.getHomeTeamId())));
            m.setAwayTeam(teamRepo.findById(f.getAwayTeamId()).orElseThrow(() -> NotFoundException.of("Equipo", f.getAwayTeamId())));
            m.setScheduledAt(f.getScheduledAt());
            ms.add(m);
        }
        var saved = matchRepo.saveAll(ms);
        notificationRepo.save(new Notification(null, "tournament:"+t.getId(), "FIXTURE",
                "Fixtures published: "+saved.size(), null));
        return saved.stream().map(ResponseDtos.MatchRes::from).toList();
    }

    private Tournament findTournament(Long id) {
        return tournamentRepo.findById(id).orElseThrow(() -> NotFoundException.of("Campeonato", id));
    }
}
