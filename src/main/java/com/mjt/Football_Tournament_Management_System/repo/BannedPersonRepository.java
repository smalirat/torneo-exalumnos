package com.mjt.Football_Tournament_Management_System.repo;

import com.mjt.Football_Tournament_Management_System.entire.BannedPerson;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BannedPersonRepository extends JpaRepository<BannedPerson, Long> {
    Optional<BannedPerson> findByDni(String dni);
    boolean existsByDni(String dni);
    List<BannedPerson> findAllByOrderByFullNameAscDniAsc();
}
