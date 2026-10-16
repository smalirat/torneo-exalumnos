package com.mjt.Football_Tournament_Management_System.service;


import com.mjt.Football_Tournament_Management_System.dto.AdminDtos;
import com.mjt.Football_Tournament_Management_System.dto.ResponseDtos;
import com.mjt.Football_Tournament_Management_System.entire.MatchGame;
import com.mjt.Football_Tournament_Management_System.repo.MatchRepository;
import com.mjt.Football_Tournament_Management_System.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MatchService {
    private final MatchRepository matchRepo;
    public ResponseDtos.MatchRes updateScore(Long id, AdminDtos.ScoreUpdateReq req) {
        var m = matchRepo.findById(id).orElseThrow(() -> NotFoundException.of("Partido", id));
        m.setHomeGoals(req.getHomeGoals()); m.setAwayGoals(req.getAwayGoals());
        m.setStatus(MatchGame.Status.FINISHED);
        return ResponseDtos.MatchRes.from(matchRepo.save(m));
    }
}
