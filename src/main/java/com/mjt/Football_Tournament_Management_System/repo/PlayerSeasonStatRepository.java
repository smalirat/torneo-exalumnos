package com.mjt.Football_Tournament_Management_System.repo;

import com.mjt.Football_Tournament_Management_System.entire.Player;
import com.mjt.Football_Tournament_Management_System.entire.PlayerSeasonStat;
import com.mjt.Football_Tournament_Management_System.entire.Season;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlayerSeasonStatRepository extends JpaRepository<PlayerSeasonStat, Long> {
    List<PlayerSeasonStat> findBySeason(Season season);
    Optional<PlayerSeasonStat> findBySeasonAndPlayer(Season season, Player player);
    boolean existsByPlayer(Player player);
}
