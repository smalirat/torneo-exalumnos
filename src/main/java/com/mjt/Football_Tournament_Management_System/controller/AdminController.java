package com.mjt.Football_Tournament_Management_System.controller;


import com.mjt.Football_Tournament_Management_System.dto.AdminDtos;
import com.mjt.Football_Tournament_Management_System.entire.MatchGame;
import com.mjt.Football_Tournament_Management_System.entire.Tournament;
import com.mjt.Football_Tournament_Management_System.service.MatchService;
import com.mjt.Football_Tournament_Management_System.service.TournamentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name="Admin API (JWT)")
public class AdminController {
    private final TournamentService tService;
    private final MatchService mService;

    @PostMapping("/tournaments")
    public Tournament create(@RequestBody AdminDtos.CreateTournamentReq req) {
        return tService.create(req);
    }

    @PostMapping("/tournaments/{id}/fixtures/round-robin")
    public List<MatchGame> rr(@PathVariable Long id,
                              @RequestParam @DateTimeFormat(iso= DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                              @RequestParam(defaultValue="90") int minutesBetween) {
        return tService.createRoundRobinFixtures(id, start, minutesBetween);
    }

    @PostMapping("/fixtures/manual")
    public List<MatchGame> manual(@RequestBody AdminDtos.ManualFixtureReq req) {
        return tService.createManualFixtures(req);
    }

    @PatchMapping("/matches/{id}/score")
    public MatchGame score(@PathVariable Long id, @RequestBody AdminDtos.ScoreUpdateReq req) {
        return mService.updateScore(id, req);
    }
}
