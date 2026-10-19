package com.mjt.Football_Tournament_Management_System.repo;


import com.mjt.Football_Tournament_Management_System.entire.Player;
import com.mjt.Football_Tournament_Management_System.entire.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlayerRepository extends JpaRepository<Player, Long> {
    List<Player> findByTeamOrderByJerseyNoAscFullNameAsc(Team team);
    int countByTeam(Team team);
    List<Player> findByTeamAndApprovedTrueOrderByJerseyNoAscFullNameAsc(Team team);
    List<Player> findByApprovedFalseAndTeam_ApprovedTrue();
    List<Player> findByRemovalRequestedAtNotNull();
}

