package com.mjt.Football_Tournament_Management_System.repo;

import com.mjt.Football_Tournament_Management_System.entire.Champion;
import com.mjt.Football_Tournament_Management_System.entire.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChampionRepository extends JpaRepository<Champion, Long> {
    List<Champion> findAllByOrderByCategoryNameAscYearDesc();
    Optional<Champion> findByYearAndCategoryName(int year, String categoryName);
    boolean existsByTeam(Team team);
}
