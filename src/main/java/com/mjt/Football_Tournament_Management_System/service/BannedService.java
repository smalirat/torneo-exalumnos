package com.mjt.Football_Tournament_Management_System.service;

import com.mjt.Football_Tournament_Management_System.dto.AdminDtos;
import com.mjt.Football_Tournament_Management_System.dto.ResponseDtos;
import com.mjt.Football_Tournament_Management_System.entire.BannedPerson;
import com.mjt.Football_Tournament_Management_System.entire.Player;
import com.mjt.Football_Tournament_Management_System.exception.BadRequestException;
import com.mjt.Football_Tournament_Management_System.exception.ConflictException;
import com.mjt.Football_Tournament_Management_System.exception.NotFoundException;
import com.mjt.Football_Tournament_Management_System.repo.BannedPersonRepository;
import com.mjt.Football_Tournament_Management_System.repo.PersonRepository;
import com.mjt.Football_Tournament_Management_System.repo.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The list of players the Asociación de Exalumnos does not allow (rules: "no deben estar incluidos en la lista de
 * jugadores inhabilitados"). Admin only. A DNI on it cannot be added to a roster; someone already in one is flagged.
 */
@Service
@RequiredArgsConstructor
public class BannedService {
    private final BannedPersonRepository bannedRepo;
    private final PersonRepository personRepo;
    private final PlayerRepository playerRepo;

    @Transactional(readOnly = true)
    public List<ResponseDtos.BannedRes> list() {
        return bannedRepo.findAllByOrderByFullNameAscDniAsc().stream().map(this::toRes).toList();
    }

    @Transactional
    public ResponseDtos.BannedRes add(AdminDtos.BannedReq req) {
        var dni = Dni.normalize(req.getDni());
        if (dni == null) throw new BadRequestException("Indicá el DNI");
        if (bannedRepo.existsByDni(dni)) throw new ConflictException("El DNI " + dni + " ya está en la lista");
        var name = req.getFullName() == null || req.getFullName().isBlank()
                ? personRepo.findByDni(dni).map(p -> p.getFullName()).orElse(null) : req.getFullName().trim();
        var reason = req.getReason() == null || req.getReason().isBlank() ? null : req.getReason().trim();
        return toRes(bannedRepo.save(new BannedPerson(null, dni, name, reason, LocalDateTime.now())));
    }

    @Transactional
    public void remove(Long id) {
        bannedRepo.delete(bannedRepo.findById(id).orElseThrow(() -> NotFoundException.of("Inhabilitado", id)));
    }

    /** 409 if the DNI is on the list (adding a player, or giving one this DNI) */
    public void checkAllowed(String dni) {
        if (dni == null) return;
        if (bannedRepo.existsByDni(dni)) {
            throw new ConflictException("El DNI " + dni + " está en la lista de jugadores inhabilitados de la Asociación");
        }
    }

    public boolean isBanned(String dni) { return dni != null && bannedRepo.existsByDni(dni); }

    /** With the team the person is in now, if any (banned after being added: the admin has to act) */
    private ResponseDtos.BannedRes toRes(BannedPerson b) {
        var team = personRepo.findByDni(b.getDni()).flatMap(person -> playerRepo.findByPerson(person).stream()
                .filter(Player::isActive).findFirst()).map(p -> p.getTeam().getName()).orElse(null);
        return new ResponseDtos.BannedRes(b.getId(), b.getDni(), b.getFullName(), b.getReason(), b.getCreatedAt(), team);
    }
}
