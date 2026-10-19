package com.mjt.Football_Tournament_Management_System.repo;

import com.mjt.Football_Tournament_Management_System.entire.AppUser;
import com.mjt.Football_Tournament_Management_System.entire.RequestDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RequestDecisionRepository extends JpaRepository<RequestDecision, Long> {
    List<RequestDecision> findTop20ByCaptainOrderByDecidedAtDescIdDesc(AppUser captain);
}
