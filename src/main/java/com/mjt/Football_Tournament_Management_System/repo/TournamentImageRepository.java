package com.mjt.Football_Tournament_Management_System.repo;

import com.mjt.Football_Tournament_Management_System.entire.TournamentImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TournamentImageRepository extends JpaRepository<TournamentImage, Long> {
    Optional<TournamentImage> findByTournamentIdAndKind(Long tournamentId, TournamentImage.Kind kind);
}
