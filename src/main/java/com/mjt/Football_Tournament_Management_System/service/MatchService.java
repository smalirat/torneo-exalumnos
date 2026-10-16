package com.mjt.Football_Tournament_Management_System.service;


import com.mjt.Football_Tournament_Management_System.dto.AdminDtos;
import com.mjt.Football_Tournament_Management_System.entire.MatchGame;
import com.mjt.Football_Tournament_Management_System.repo.MatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MatchService {
    private final MatchRepository matchRepo;
    public MatchGame updateScore(Long id, AdminDtos.ScoreUpdateReq req) {
        var m = matchRepo.findById(id).orElseThrow();
        m.setHomeGoals(req.getHomeGoals()); m.setAwayGoals(req.getAwayGoals());
        m.setStatus(MatchGame.Status.FINISHED);
        return matchRepo.save(m);
    }
}
