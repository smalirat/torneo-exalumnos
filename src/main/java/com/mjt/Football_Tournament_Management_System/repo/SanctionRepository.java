package com.mjt.Football_Tournament_Management_System.repo;

import com.mjt.Football_Tournament_Management_System.entire.Player;
import com.mjt.Football_Tournament_Management_System.entire.Sanction;
import com.mjt.Football_Tournament_Management_System.entire.Season;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SanctionRepository extends JpaRepository<Sanction, Long> {
    List<Sanction> findBySeasonOrderByIdAsc(Season season);
    boolean existsByPlayer(Player player);
}
