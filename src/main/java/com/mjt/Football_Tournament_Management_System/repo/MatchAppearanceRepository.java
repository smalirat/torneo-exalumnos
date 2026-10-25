package com.mjt.Football_Tournament_Management_System.repo;

import com.mjt.Football_Tournament_Management_System.entire.MatchAppearance;
import com.mjt.Football_Tournament_Management_System.entire.MatchGame;
import com.mjt.Football_Tournament_Management_System.entire.Player;
import com.mjt.Football_Tournament_Management_System.entire.Tournament;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MatchAppearanceRepository extends JpaRepository<MatchAppearance, Long> {
    List<MatchAppearance> findByMatch(MatchGame match);
    void deleteByMatch(MatchGame match);
    boolean existsByPlayer(Player player);
    boolean existsByPlayerAndMatch_Tournament(Player player, Tournament tournament);
}
