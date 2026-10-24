package com.mjt.Football_Tournament_Management_System.repo;


import com.mjt.Football_Tournament_Management_System.entire.Player;
import com.mjt.Football_Tournament_Management_System.entire.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlayerRepository extends JpaRepository<Player, Long> {
    /** Every file of the team, the ones that left included (history, deletion, Excel matching) */
    List<Player> findByTeamOrderByJerseyNoAscFullNameAsc(Team team);
    /** The roster now: still in the team (pending additions included) */
    List<Player> findByTeamAndLeftAtIsNullOrderByJerseyNoAscFullNameAsc(Team team);
    int countByTeamAndLeftAtIsNullAndApprovedTrue(Team team);
    List<Player> findByTeamAndApprovedTrueAndLeftAtIsNullOrderByJerseyNoAscFullNameAsc(Team team);
    List<Player> findByPerson(com.mjt.Football_Tournament_Management_System.entire.Person person);
    List<Player> findByApprovedFalseAndTeam_ApprovedTrue();
    List<Player> findByRemovalRequestedAtNotNull();

}

