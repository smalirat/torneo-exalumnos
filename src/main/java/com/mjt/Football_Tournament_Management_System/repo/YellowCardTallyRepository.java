package com.mjt.Football_Tournament_Management_System.repo;

import com.mjt.Football_Tournament_Management_System.entire.Player;
import com.mjt.Football_Tournament_Management_System.entire.Tournament;
import com.mjt.Football_Tournament_Management_System.entire.YellowCardTally;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface YellowCardTallyRepository extends JpaRepository<YellowCardTally, Long> {
    List<YellowCardTally> findByTournament(Tournament tournament);
    Optional<YellowCardTally> findByTournamentAndPlayer(Tournament tournament, Player player);
    boolean existsByPlayer(Player player);
    boolean existsByTournament(Tournament tournament);
}
