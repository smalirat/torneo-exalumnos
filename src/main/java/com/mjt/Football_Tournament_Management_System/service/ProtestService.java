package com.mjt.Football_Tournament_Management_System.service;

import com.mjt.Football_Tournament_Management_System.dto.AdminDtos;
import com.mjt.Football_Tournament_Management_System.dto.ResponseDtos;
import com.mjt.Football_Tournament_Management_System.entire.MatchProtest;
import com.mjt.Football_Tournament_Management_System.exception.ConflictException;
import com.mjt.Football_Tournament_Management_System.exception.NotFoundException;
import com.mjt.Football_Tournament_Management_System.repo.AppUserRepository;
import com.mjt.Football_Tournament_Management_System.repo.MatchProtestRepository;
import com.mjt.Football_Tournament_Management_System.repo.MatchRepository;
import com.mjt.Football_Tournament_Management_System.repo.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Protests of matches (the rules: after the match, with the facts and a precise request, by the captain). The
 * organizer (Comisión / Tribunal) answers before the next match day; the answer cannot be appealed.
 */
@Service
@RequiredArgsConstructor
public class ProtestService {
    private final MatchProtestRepository protestRepo;
    private final MatchRepository matchRepo;
    private final TeamRepository teamRepo;
    private final AppUserRepository userRepo;

    @Transactional
    public ResponseDtos.ProtestRes create(String username, AdminDtos.ProtestReq req) {
        var user = userRepo.findByUsername(username).orElseThrow(() -> NotFoundException.of("Usuario", username));
        var m = matchRepo.findById(req.getMatchId()).orElseThrow(() -> NotFoundException.of("Partido", req.getMatchId()));
        var team = teamRepo.findByCaptain(user).stream()
                .filter(t -> t.getId().equals(m.getHomeTeam().getId()) || t.getId().equals(m.getAwayTeam().getId()))
                .findFirst().orElseThrow(() -> new AccessDeniedException("Solo el capitán de uno de los equipos puede protestar el partido"));
        if (!m.isFinished() && !m.isSuspended()) throw new ConflictException("El partido todavía no se jugó");
        if (protestRepo.existsByMatchAndTeamAndStatus(m, team, MatchProtest.Status.OPEN)) {
            throw new ConflictException("Ya hay una protesta de tu equipo por este partido esperando respuesta");
        }
        var p = new MatchProtest(null, m, team, user, req.getFacts().trim(), req.getRequest().trim(),
                MatchProtest.Status.OPEN, null, LocalDateTime.now(), null);
        return ResponseDtos.ProtestRes.from(protestRepo.save(p));
    }

    @Transactional(readOnly = true)
    public List<ResponseDtos.ProtestRes> mine(String username) {
        var user = userRepo.findByUsername(username).orElseThrow(() -> NotFoundException.of("Usuario", username));
        var teams = teamRepo.findByCaptain(user);
        if (teams.isEmpty()) return List.of();
        return protestRepo.findByTeamInOrderByCreatedAtDesc(teams).stream().map(ResponseDtos.ProtestRes::from).toList();
    }

    /** Every protest, the open ones first */
    @Transactional(readOnly = true)
    public List<ResponseDtos.ProtestRes> all() {
        return protestRepo.findAllByOrderByStatusAscCreatedAtDesc().stream().map(ResponseDtos.ProtestRes::from).toList();
    }

    @Transactional
    public ResponseDtos.ProtestRes resolve(Long id, AdminDtos.ProtestResolutionReq req) {
        var p = protestRepo.findById(id).orElseThrow(() -> NotFoundException.of("Protesta", id));
        if (p.getStatus() == MatchProtest.Status.RESOLVED) throw new ConflictException("La protesta ya fue resuelta");
        p.setStatus(MatchProtest.Status.RESOLVED);
        p.setResolution(req.getResolution().trim());
        p.setResolvedAt(LocalDateTime.now());
        return ResponseDtos.ProtestRes.from(p);
    }

    public long openCount() { return protestRepo.countByStatus(MatchProtest.Status.OPEN); }
}
