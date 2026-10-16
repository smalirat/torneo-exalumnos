package com.mjt.Football_Tournament_Management_System.repo;

import com.mjt.Football_Tournament_Management_System.entire.MatchEvent;
import com.mjt.Football_Tournament_Management_System.entire.MatchGame;
import com.mjt.Football_Tournament_Management_System.entire.Player;
import com.mjt.Football_Tournament_Management_System.entire.Tournament;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface MatchEventRepository extends JpaRepository<MatchEvent, Long> {
    List<MatchEvent> findByMatchOrderByIdAsc(MatchGame match);
    List<MatchEvent> findByMatch_TournamentAndTypeIn(Tournament tournament, Collection<MatchEvent.Type> types);
    void deleteByMatch(MatchGame match);
    boolean existsByPlayer(Player player);
}
