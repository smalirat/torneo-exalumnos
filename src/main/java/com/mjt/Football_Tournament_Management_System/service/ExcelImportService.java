package com.mjt.Football_Tournament_Management_System.service;

import com.mjt.Football_Tournament_Management_System.dto.ResponseDtos;
import com.mjt.Football_Tournament_Management_System.entire.ExcelImport;
import com.mjt.Football_Tournament_Management_System.excel.ExcelImporter;
import com.mjt.Football_Tournament_Management_System.excel.ImportReport;
import com.mjt.Football_Tournament_Management_System.excel.ParsedWorkbook;
import com.mjt.Football_Tournament_Management_System.excel.WorkbookParser;
import com.mjt.Football_Tournament_Management_System.exception.BadRequestException;
import com.mjt.Football_Tournament_Management_System.exception.ConflictException;
import com.mjt.Football_Tournament_Management_System.exception.NotFoundException;
import com.mjt.Football_Tournament_Management_System.repo.ExcelImportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Upload -> preview (the import runs in a transaction that is rolled back, so the report shows exactly what
 * would change) -> apply (same code, committed). The file is stored so applying does not need a new upload.
 */
@Service
@RequiredArgsConstructor
public class ExcelImportService {
    private final ExcelImportRepository importRepo;
    private final ExcelImporter importer;
    private final TransactionTemplate tx;

    public ResponseDtos.ImportPreviewRes preview(MultipartFile file, String username) {
        if (file == null || file.isEmpty()) throw new BadRequestException("Falta el archivo");
        var name = file.getOriginalFilename() == null ? "planilla.xlsx" : file.getOriginalFilename();
        if (!name.toLowerCase().endsWith(".xlsx")) throw new BadRequestException("El archivo tiene que ser un Excel .xlsx");
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("No se pudo leer el archivo subido");
        }
        var parsed = parse(bytes);
        var report = run(parsed, Set.of(), true);
        var saved = importRepo.save(new ExcelImport(null, name, username, LocalDateTime.now(), null,
                ExcelImport.Status.PREVIEW, parsed.year, report.summary(), bytes));
        return ResponseDtos.ImportPreviewRes.of(saved, report);
    }

    /** Applies a previewed upload. `overwrite`: conflict keys the admin chose to replace with the Excel value. */
    public ResponseDtos.ImportPreviewRes apply(Long importId, Set<String> overwrite) {
        var imp = importRepo.findById(importId).orElseThrow(() -> NotFoundException.of("Importación", importId));
        if (imp.getStatus() == ExcelImport.Status.APPLIED) throw new ConflictException("Esa importación ya se aplicó");
        var report = run(parse(imp.getFileData()), overwrite, false);
        imp.setStatus(ExcelImport.Status.APPLIED);
        imp.setAppliedAt(LocalDateTime.now());
        imp.setSummary(report.summary());
        return ResponseDtos.ImportPreviewRes.of(importRepo.save(imp), report);
    }

    public List<ResponseDtos.ImportHistoryRes> history() {
        return importRepo.findTop30ByOrderByCreatedAtDesc().stream().map(ResponseDtos.ImportHistoryRes::from).toList();
    }

    private ParsedWorkbook parse(byte[] bytes) {
        return new WorkbookParser().parse(new ByteArrayInputStream(bytes));
    }

    private ImportReport run(ParsedWorkbook parsed, Set<String> overwrite, boolean dryRun) {
        var report = new ImportReport();
        tx.executeWithoutResult(status -> {
            importer.run(parsed, overwrite, report);
            if (dryRun) status.setRollbackOnly();
        });
        return report;
    }
}
