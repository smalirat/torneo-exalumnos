package com.mjt.Football_Tournament_Management_System.repo;


import com.mjt.Football_Tournament_Management_System.entire.Registration;
import com.mjt.Football_Tournament_Management_System.entire.Team;
import com.mjt.Football_Tournament_Management_System.entire.Tournament;
import org.springframework.data.jpa.repository.JpaRepository;
import com.mjt.Football_Tournament_Management_System.entire.Category;
import com.mjt.Football_Tournament_Management_System.entire.Zone;
import java.util.List;

public interface RegistrationRepository extends JpaRepository<Registration, Long> {
    List<Registration> findByTournament(Tournament t);
    List<Registration> findByTournamentAndApprovedTrue(Tournament t);
    List<Registration> findByApprovedFalse();
    boolean existsByTournament(Tournament t);
    boolean existsByTeam(Team team);
    List<Registration> findByTeamOrderByIdDesc(Team team);
    boolean existsByTournamentAndTeam(Tournament t, Team team);
    List<Registration> findByZone(Zone zone);
    boolean existsByZone(Zone zone);
    boolean existsByZoneAndTeam(Zone zone, Team team);
    boolean existsByZone_Category(Category category);
}
