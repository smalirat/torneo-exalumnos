package com.mjt.Football_Tournament_Management_System.repo;


import com.mjt.Football_Tournament_Management_System.entire.MatchGame;
import com.mjt.Football_Tournament_Management_System.entire.Team;
import com.mjt.Football_Tournament_Management_System.entire.Tournament;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MatchRepository extends JpaRepository<MatchGame, Long> {
    List<MatchGame> findByTournament(Tournament t);
    List<MatchGame> findByTournamentAndHomeTeamOrAwayTeam(Tournament t, Team home, Team away);
}
