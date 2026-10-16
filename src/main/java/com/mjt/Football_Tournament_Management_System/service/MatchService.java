package com.mjt.Football_Tournament_Management_System.service;


import com.mjt.Football_Tournament_Management_System.dto.AdminDtos;
import com.mjt.Football_Tournament_Management_System.dto.ResponseDtos;
import com.mjt.Football_Tournament_Management_System.entire.MatchGame;
import com.mjt.Football_Tournament_Management_System.repo.MatchRepository;
import com.mjt.Football_Tournament_Management_System.exception.BadRequestException;
import com.mjt.Football_Tournament_Management_System.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MatchService {
    private final MatchRepository matchRepo;
    public ResponseDtos.MatchRes updateScore(Long id, AdminDtos.ScoreUpdateReq req) {
        var m = matchRepo.findById(id).orElseThrow(() -> NotFoundException.of("Partido", id));
        boolean home = req.isHomePresent(), away = req.isAwayPresent();
        if (home && away) {
            if (req.getHomeGoals() == null || req.getAwayGoals() == null) {
                throw new BadRequestException("Faltan los goles del partido");
            }
            m.setHomeGoals(req.getHomeGoals()); m.setAwayGoals(req.getAwayGoals());
        } else {
            // Walkover: whoever showed up wins 3-0; if neither did, 0-0 and nobody scores points
            m.setHomeGoals(home ? MatchGame.WALKOVER_GOALS : 0);
            m.setAwayGoals(away ? MatchGame.WALKOVER_GOALS : 0);
        }
        m.setHomePresent(home); m.setAwayPresent(away);
        m.setHomeYellowCards(req.getHomeYellowCards()); m.setHomeRedCards(req.getHomeRedCards());
        m.setAwayYellowCards(req.getAwayYellowCards()); m.setAwayRedCards(req.getAwayRedCards());
        m.setStatus(MatchGame.Status.FINISHED);
        return ResponseDtos.MatchRes.from(matchRepo.save(m));
    }
}
