package com.mjt.Football_Tournament_Management_System.repo;

import com.mjt.Football_Tournament_Management_System.entire.MatchGame;
import com.mjt.Football_Tournament_Management_System.entire.MatchProtest;
import com.mjt.Football_Tournament_Management_System.entire.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface MatchProtestRepository extends JpaRepository<MatchProtest, Long> {
    List<MatchProtest> findAllByOrderByStatusAscCreatedAtDesc();
    List<MatchProtest> findByTeamInOrderByCreatedAtDesc(Collection<Team> teams);
    boolean existsByMatchAndTeamAndStatus(MatchGame match, Team team, MatchProtest.Status status);
    long countByStatus(MatchProtest.Status status);
    void deleteByMatch(MatchGame match);
}
