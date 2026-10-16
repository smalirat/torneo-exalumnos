package com.mjt.Football_Tournament_Management_System.repo;


import com.mjt.Football_Tournament_Management_System.entire.Category;
import com.mjt.Football_Tournament_Management_System.entire.MatchGame;
import com.mjt.Football_Tournament_Management_System.entire.Team;
import com.mjt.Football_Tournament_Management_System.entire.Tournament;
import com.mjt.Football_Tournament_Management_System.entire.Zone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;

public interface MatchRepository extends JpaRepository<MatchGame, Long> {
    List<MatchGame> findByTournament(Tournament t);
    boolean existsByTournament(Tournament t);
    List<MatchGame> findByTournamentAndTypeInOrderByScheduledAtAsc(Tournament t, Collection<MatchGame.Type> types);
    List<MatchGame> findByZoneOrderByScheduledAtAsc(Zone zone);
    boolean existsByZone(Zone zone);
    boolean existsByZone_Category(Category category);

    @Query("select count(m) > 0 from MatchGame m where m.zone = :zone"
            + " and (m.homeTeam = :team or m.awayTeam = :team)")
    boolean existsByZoneAndTeam(@Param("zone") Zone zone, @Param("team") Team team);

    /** Matches of a team (home or away) within a single tournament. */
    @Query("select m from MatchGame m where m.tournament = :tournament"
            + " and (m.homeTeam = :team or m.awayTeam = :team) order by m.scheduledAt")
    List<MatchGame> findByTournamentAndTeam(@Param("tournament") Tournament tournament, @Param("team") Team team);
}
