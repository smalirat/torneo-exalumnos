package com.mjt.Football_Tournament_Management_System.repo;

import com.mjt.Football_Tournament_Management_System.entire.ExcelImport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExcelImportRepository extends JpaRepository<ExcelImport, Long> {
    List<ExcelImport> findTop30ByOrderByCreatedAtDesc();
}
