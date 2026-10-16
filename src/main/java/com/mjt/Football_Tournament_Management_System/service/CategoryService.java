package com.mjt.Football_Tournament_Management_System.service;

import com.mjt.Football_Tournament_Management_System.dto.ResponseDtos;
import com.mjt.Football_Tournament_Management_System.entire.Category;
import com.mjt.Football_Tournament_Management_System.entire.MatchGame;
import com.mjt.Football_Tournament_Management_System.entire.Tournament;
import com.mjt.Football_Tournament_Management_System.entire.Zone;
import com.mjt.Football_Tournament_Management_System.exception.ConflictException;
import com.mjt.Football_Tournament_Management_System.exception.NotFoundException;
import com.mjt.Football_Tournament_Management_System.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Categories and zones of a tournament. */
@Service
@RequiredArgsConstructor
public class CategoryService {
    static final String DEFAULT_ZONE_NAME = "Zona 1";

    private final TournamentRepository tournamentRepo;
    private final CategoryRepository categoryRepo;
    private final ZoneRepository zoneRepo;
    private final RegistrationRepository regRepo;
    private final MatchRepository matchRepo;

    public List<ResponseDtos.CategoryRes> listByTournament(Long tournamentId) {
        return categoryRepo.findByTournamentOrderByDisplayOrderAscIdAsc(findTournament(tournamentId)).stream()
                .map(this::toRes).toList();
    }

    /** Creates the category together with its default zone. */
    @Transactional
    public ResponseDtos.CategoryRes create(Long tournamentId, String name) {
        var t = findTournament(tournamentId);
        var c = categoryRepo.save(new Category(null, t, name.trim(), categoryRepo.countByTournament(t)));
        zoneRepo.save(new Zone(null, c, DEFAULT_ZONE_NAME, 0));
        return toRes(c);
    }

    @Transactional
    public ResponseDtos.CategoryRes rename(Long categoryId, String name) {
        var c = findCategory(categoryId);
        c.setName(name.trim());
        return toRes(c);
    }

    @Transactional
    public void delete(Long categoryId) {
        var c = findCategory(categoryId);
        if (regRepo.existsByZone_Category(c) || matchRepo.existsByZone_Category(c)) {
            throw new ConflictException("La categoría tiene equipos o partidos; no se puede eliminar");
        }
        zoneRepo.deleteAll(zoneRepo.findByCategoryOrderByDisplayOrderAscIdAsc(c));
        categoryRepo.delete(c);
    }

    @Transactional
    public ResponseDtos.ZoneRes addZone(Long categoryId, String name) {
        var c = findCategory(categoryId);
        return ResponseDtos.ZoneRes.from(zoneRepo.save(new Zone(null, c, name.trim(), zoneRepo.countByCategory(c))));
    }

    @Transactional
    public ResponseDtos.ZoneRes renameZone(Long zoneId, String name) {
        var z = findZone(zoneId);
        z.setName(name.trim());
        return ResponseDtos.ZoneRes.from(z);
    }

    @Transactional
    public void deleteZone(Long zoneId) {
        var z = findZone(zoneId);
        if (zoneRepo.countByCategory(z.getCategory()) <= 1) {
            throw new ConflictException("Una categoría debe tener al menos una zona");
        }
        if (regRepo.existsByZone(z) || matchRepo.existsByZone(z)) {
            throw new ConflictException("La zona tiene equipos o partidos; no se puede eliminar");
        }
        zoneRepo.delete(z);
    }

    /** Assigns (or reassigns) a registered team to a zone of the same tournament, if it has no matches yet. */
    @Transactional
    public ResponseDtos.RegistrationRes assignZone(Long registrationId, Long zoneId) {
        var r = regRepo.findById(registrationId)
                .orElseThrow(() -> NotFoundException.of("Inscripción", registrationId));
        var target = findZone(zoneId);
        if (!target.getCategory().getTournament().getId().equals(r.getTournament().getId())) {
            throw new ConflictException("La zona no pertenece al campeonato de la inscripción");
        }
        if (r.getZone() != null && matchRepo.existsByZoneAndTeam(r.getZone(), r.getTeam())) {
            throw new ConflictException("El equipo ya tiene partidos en su zona actual");
        }
        r.setZone(target);
        return ResponseDtos.RegistrationRes.from(r);
    }

    public List<ResponseDtos.RegistrationRes> zoneRegistrations(Long zoneId) {
        return regRepo.findByZone(findZone(zoneId)).stream().map(ResponseDtos.RegistrationRes::from).toList();
    }

    public List<ResponseDtos.MatchRes> zoneFixtures(Long zoneId) {
        return matchRepo.findByZoneOrderByScheduledAtAsc(findZone(zoneId)).stream()
                .sorted(MatchGame.BY_DATE).map(ResponseDtos.MatchRes::from).toList();
    }

    private Zone findZone(Long id) {
        return zoneRepo.findById(id).orElseThrow(() -> NotFoundException.of("Zona", id));
    }

    private Category findCategory(Long id) {
        return categoryRepo.findById(id).orElseThrow(() -> NotFoundException.of("Categoría", id));
    }

    private Tournament findTournament(Long id) {
        return tournamentRepo.findById(id).orElseThrow(() -> NotFoundException.of("Campeonato", id));
    }

    private ResponseDtos.CategoryRes toRes(Category c) {
        var zones = zoneRepo.findByCategoryOrderByDisplayOrderAscIdAsc(c).stream()
                .map(ResponseDtos.ZoneRes::from).toList();
        return new ResponseDtos.CategoryRes(c.getId(), c.getTournament().getId(), c.getName(),
                c.getDisplayOrder(), zones.size(), zones);
    }
}
