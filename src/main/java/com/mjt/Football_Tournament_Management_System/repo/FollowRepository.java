package com.mjt.Football_Tournament_Management_System.repo;

import com.mjt.Football_Tournament_Management_System.entire.AppUser;
import com.mjt.Football_Tournament_Management_System.entire.Follow;
import com.mjt.Football_Tournament_Management_System.entire.Tournament;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FollowRepository extends JpaRepository<Follow, Long> {
    List<Follow> findByUserOrderByCreatedAtDesc(AppUser user);
    Optional<Follow> findByUserAndTournament(AppUser user, Tournament tournament);
    boolean existsByUserAndTournament(AppUser user, Tournament tournament);
    long countByTournament(Tournament tournament);
}
