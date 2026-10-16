package com.mjt.Football_Tournament_Management_System.controller;


import com.mjt.Football_Tournament_Management_System.dto.ResponseDtos;
import com.mjt.Football_Tournament_Management_System.service.TournamentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@Tag(name="Public API")
public class PublicApiController {
    private final TournamentService tService;

    @GetMapping("/tournaments")
    public List<ResponseDtos.TournamentRes> tournaments() {
        return tService.findAll();
    }

    @GetMapping("/tournaments/{id}/registrations")
    public List<ResponseDtos.RegistrationRes> regs(@PathVariable Long id) {
        return tService.registrations(id);
    }

    @GetMapping("/tournaments/{id}/fixtures")
    public List<ResponseDtos.MatchRes> fixtures(@PathVariable Long id) {
        return tService.fixtures(id);
    }
}
