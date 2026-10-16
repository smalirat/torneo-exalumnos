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
}
