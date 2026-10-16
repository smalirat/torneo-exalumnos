package com.mjt.Football_Tournament_Management_System.controller;


import com.mjt.Football_Tournament_Management_System.entire.MatchGame;
import com.mjt.Football_Tournament_Management_System.entire.Registration;
import com.mjt.Football_Tournament_Management_System.entire.Tournament;
import com.mjt.Football_Tournament_Management_System.repo.MatchRepository;
import com.mjt.Football_Tournament_Management_System.repo.RegistrationRepository;
import com.mjt.Football_Tournament_Management_System.repo.TournamentRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@Tag(name="Public API")
public class PublicApiController {
    private final TournamentRepository tRepo;
    private final RegistrationRepository rRepo; private final MatchRepository mRepo;

    @GetMapping("/tournaments")
    public List<Tournament> tournaments() {
        return tRepo.findAll();
    }

    @GetMapping("/tournaments/{id}/registrations")
    public List<Registration> regs(@PathVariable Long id) {
        var t = tRepo.findById(id).orElseThrow();
        return rRepo.findByTournament(t);
    }

    @GetMapping("/tournaments/{id}/fixtures")
    public List<MatchGame> fixtures(@PathVariable Long id) {
        var t = tRepo.findById(id).orElseThrow();
        return mRepo.findByTournament(t);
    }
}
