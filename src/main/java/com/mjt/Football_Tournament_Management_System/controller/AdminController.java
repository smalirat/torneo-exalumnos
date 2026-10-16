package com.mjt.Football_Tournament_Management_System.controller;


import com.mjt.Football_Tournament_Management_System.dto.AdminDtos;
import com.mjt.Football_Tournament_Management_System.dto.ResponseDtos;
import com.mjt.Football_Tournament_Management_System.service.CategoryService;
import com.mjt.Football_Tournament_Management_System.service.MatchService;
import com.mjt.Football_Tournament_Management_System.service.TournamentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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
    private final CategoryService cService;

    @PostMapping("/tournaments")
    public ResponseDtos.TournamentRes create(@Valid @RequestBody AdminDtos.CreateTournamentReq req) {
        return tService.create(req);
    }

    @PostMapping("/tournaments/{id}/categories")
    @Operation(summary = "Create a category (with a default zone)")
    public ResponseDtos.CategoryRes createCategory(@PathVariable Long id, @Valid @RequestBody AdminDtos.NameReq req) {
        return cService.create(id, req.getName());
    }

    @PutMapping("/categories/{id}")
    public ResponseDtos.CategoryRes renameCategory(@PathVariable Long id, @Valid @RequestBody AdminDtos.NameReq req) {
        return cService.rename(id, req.getName());
    }

    @DeleteMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a category; 409 if it has teams or matches")
    public void deleteCategory(@PathVariable Long id) {
        cService.delete(id);
    }

    @PostMapping("/categories/{id}/zones")
    public ResponseDtos.ZoneRes createZone(@PathVariable Long id, @Valid @RequestBody AdminDtos.NameReq req) {
        return cService.addZone(id, req.getName());
    }

    @PutMapping("/zones/{id}")
    public ResponseDtos.ZoneRes renameZone(@PathVariable Long id, @Valid @RequestBody AdminDtos.NameReq req) {
        return cService.renameZone(id, req.getName());
    }

    @DeleteMapping("/zones/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a zone; 409 if it has teams or matches, or is the category's last zone")
    public void deleteZone(@PathVariable Long id) {
        cService.deleteZone(id);
    }

    @PatchMapping("/registrations/{id}/zone")
    @Operation(summary = "Assign a registered team to a category zone (or move it, if it has no matches yet)")
    public ResponseDtos.RegistrationRes assignZone(@PathVariable Long id,
                                                         @Valid @RequestBody AdminDtos.AssignZoneReq req) {
        return cService.assignZone(id, req.getZoneId());
    }

    @PostMapping("/zones/{id}/fixtures/round-robin")
    public List<ResponseDtos.MatchRes> rr(@PathVariable Long id,
                                          @RequestParam @DateTimeFormat(iso= DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                                          @RequestParam(defaultValue="90") int minutesBetween) {
        return tService.createRoundRobinFixtures(id, start, minutesBetween);
    }

    @PostMapping("/fixtures/manual")
    public List<ResponseDtos.MatchRes> manual(@Valid @RequestBody AdminDtos.ManualFixtureReq req) {
        return tService.createManualFixtures(req);
    }

    @PatchMapping("/matches/{id}/score")
    @Operation(summary = "Load the result as per-player events (score is derived); absent team = walkover 3-0")
    public ResponseDtos.MatchRes score(@PathVariable Long id, @Valid @RequestBody AdminDtos.ScoreUpdateReq req) {
        return mService.updateScore(id, req);
    }
}
