package com.mjt.Football_Tournament_Management_System.repo;


import com.mjt.Football_Tournament_Management_System.entire.AppUser;
import com.mjt.Football_Tournament_Management_System.entire.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {
    Optional<Team> findByName(String name);
    List<Team> findByCaptain(AppUser captain);
    List<Team> findAllByOrderByNameAsc();
    List<Team> findByCaptain_UsernameOrderByNameAsc(String username);
    List<Team> findByApprovedFalse();
    List<Team> findByPendingNameNotNull();
    List<Team> findByDeletionRequestedAtNotNull();
    List<Team> findByClaimUserNotNull();
    List<Team> findByClaimUser(AppUser user);
    /** Approved teams whose captain is still pending and nobody asked for: a new user can claim them */
    List<Team> findByApprovedTrueAndCaptain_PendingTrueAndClaimUserIsNullOrderByNameAsc();
}
