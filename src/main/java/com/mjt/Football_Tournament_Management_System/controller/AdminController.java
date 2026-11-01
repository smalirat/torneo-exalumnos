package com.mjt.Football_Tournament_Management_System.controller;


import com.mjt.Football_Tournament_Management_System.dto.AdminDtos;
import com.mjt.Football_Tournament_Management_System.dto.ResponseDtos;
import com.mjt.Football_Tournament_Management_System.service.CategoryService;
import com.mjt.Football_Tournament_Management_System.service.EditionBuilderService;
import com.mjt.Football_Tournament_Management_System.service.InterzonalService;
import com.mjt.Football_Tournament_Management_System.service.TournamentImageService;
import com.mjt.Football_Tournament_Management_System.service.MatchService;
import com.mjt.Football_Tournament_Management_System.service.PlayoffService;
import com.mjt.Football_Tournament_Management_System.service.TournamentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
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
    private final PlayoffService pService;
    private final TournamentImageService imageService;
    private final EditionBuilderService builder;
    private final InterzonalService interzonal;

    @PostMapping("/tournaments")
    public ResponseDtos.TournamentRes create(@Valid @RequestBody AdminDtos.CreateTournamentReq req) {
        return tService.create(req);
    }

    @PutMapping("/tournaments/{id}")
    public ResponseDtos.TournamentRes update(@PathVariable Long id, @Valid @RequestBody AdminDtos.UpdateTournamentReq req) {
        return tService.update(id, req);
    }

    @PatchMapping("/tournaments/{id}/status")
    @Operation(summary = "Change the edition's status (DRAFT/OPEN/LIVE/COMPLETE); only one active (OPEN or LIVE): "
            + "409 if another is, unless finishActive=true finishes it in the same step")
    public ResponseDtos.TournamentRes changeStatus(@PathVariable Long id, @Valid @RequestBody AdminDtos.TournamentStatusReq req) {
        return tService.changeStatus(id, req);
    }

    @DeleteMapping("/tournaments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an empty tournament (409 if it has registrations or matches)")
    public void delete(@PathVariable Long id) {
        tService.delete(id);
    }

    @PatchMapping("/matches/{id}")
    @Operation(summary = "Edit match data: date, round, field, pitch, note (not the result)")
    public ResponseDtos.MatchRes updateMatch(@PathVariable Long id, @Valid @RequestBody AdminDtos.MatchDetailsReq req) {
        return mService.updateDetails(id, req);
    }

    @DeleteMapping("/matches/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a match and its goals/cards")
    public void deleteMatch(@PathVariable Long id) {
        mService.delete(id);
    }

    @PutMapping(value = "/tournaments/{id}/images/{kind}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload the edition's banner or logo (kind = banner | logo; PNG/JPG up to 5 MB)")
    public ResponseDtos.TournamentRes setImage(@PathVariable Long id, @PathVariable String kind,
                                               @RequestParam("file") MultipartFile file) {
        return imageService.set(id, kind, file);
    }

    @DeleteMapping("/tournaments/{id}/images/{kind}")
    @Operation(summary = "Remove the edition's banner or logo")
    public ResponseDtos.TournamentRes removeImage(@PathVariable Long id, @PathVariable String kind) {
        return imageService.remove(id, kind);
    }

    @GetMapping("/tournaments/{id}/build/proposal")
    @Operation(summary = "Automatic build: where every team would play, from the results of another edition (nothing is saved)")
    public ResponseDtos.BuildProposal buildProposal(@PathVariable Long id, @RequestParam("from") Long sourceId) {
        return builder.proposal(id, sourceId);
    }

    @PostMapping("/tournaments/{id}/build")
    @Operation(summary = "Automatic build: register the teams in their categories, spread them into zones and optionally generate the fixture (409 if the edition has matches)")
    public ResponseDtos.BuildResult build(@PathVariable Long id, @Valid @RequestBody AdminDtos.BuildReq req) {
        return builder.apply(id, req);
    }

    @PostMapping("/tournaments/{id}/interzonal")
    @Operation(summary = "Interzonal / intercategory match days crossed by the current table positions; preview=true saves nothing")
    public List<ResponseDtos.MatchRes> interzonal(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean preview,
                                                  @Valid @RequestBody AdminDtos.InterzonalReq req) {
        return interzonal.generate(id, req, !preview);
    }

    @PostMapping("/zones/{id}/teams")
    @Operation(summary = "Put a team straight into a zone (existing teamId, or a new teamName with a pending captain)")
    public ResponseDtos.RegistrationRes addZoneTeam(@PathVariable Long id, @Valid @RequestBody AdminDtos.ZoneTeamReq req) {
        return cService.addTeamToZone(id, req.getTeamId(), req.getTeamName());
    }

    @DeleteMapping("/registrations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Take a team out of the edition (409 if it already has matches: withdraw it instead)")
    public void removeRegistration(@PathVariable Long id) {
        cService.removeRegistration(id);
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

    @PostMapping("/registrations/{id}/withdraw")
    @Operation(summary = "The team withdraws from the edition: its unplayed matches become 3-0 walkovers flagged as withdrawal")
    public ResponseDtos.RegistrationRes withdraw(@PathVariable Long id, @Valid @RequestBody(required = false) AdminDtos.WithdrawReq req) {
        return cService.withdraw(id, req == null ? null : req.getDate());
    }

    @DeleteMapping("/registrations/{id}/withdraw")
    @Operation(summary = "Undo a withdrawal: withdrawal walkovers of rounds not played yet go back to pending")
    public ResponseDtos.RegistrationRes undoWithdraw(@PathVariable Long id) {
        return cService.undoWithdraw(id);
    }

    @PatchMapping("/registrations/{id}/zone")
    @Operation(summary = "Assign a registered team to a category zone (or move it, if it has no matches yet)")
    public ResponseDtos.RegistrationRes assignZone(@PathVariable Long id,
                                                         @Valid @RequestBody AdminDtos.AssignZoneReq req) {
        return cService.assignZone(id, req.getZoneId());
    }

    @PostMapping("/categories/{id}/zone-draw")
    @Operation(summary = "Draw unassigned teams into the category's zones at random, keeping zone sizes balanced")
    public List<ResponseDtos.RegistrationRes> zoneDraw(@PathVariable Long id,
                                                       @Valid @RequestBody AdminDtos.ZoneDrawReq req) {
        return cService.drawZones(id, req.getRegistrationIds());
    }

    @PostMapping("/zones/{id}/fixtures/round-robin")
    public List<ResponseDtos.MatchRes> rr(@PathVariable Long id,
                                          @RequestParam @DateTimeFormat(iso= DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                                          @RequestParam(defaultValue="90") int minutesBetween) {
        return tService.createRoundRobinFixtures(id, start, minutesBetween);
    }

    @PostMapping("/fixtures/manual")
    @Operation(summary = "Manual fixtures: in a zone (zoneId) or interzonal between zones (tournamentId)")
    public List<ResponseDtos.MatchRes> manual(@Valid @RequestBody AdminDtos.ManualFixtureReq req) {
        return tService.createManualFixtures(req);
    }

    @PostMapping("/tournaments/{id}/special-matches")
    @Operation(summary = "Create a promotion/final; each side is a teamId or zoneId+position (any tournament)")
    public ResponseDtos.MatchRes createSpecialMatch(@PathVariable Long id,
                                                    @Valid @RequestBody AdminDtos.SpecialMatchReq req) {
        return pService.create(id, req);
    }

    @PostMapping("/matches/{id}/resolve")
    @Operation(summary = "Re-resolve the position rules of a promotion/final from current standings (if not played)")
    public ResponseDtos.MatchRes resolveSpecialMatch(@PathVariable Long id) {
        return pService.resolve(id);
    }

    @PatchMapping("/matches/{id}/suspension")
    @Operation(summary = "The match was suspended while being played: score so far and minutes left (it does not count until finished)")
    public ResponseDtos.MatchRes suspend(@PathVariable Long id, @Valid @RequestBody AdminDtos.SuspendReq req) {
        return mService.suspend(id, req);
    }

    @DeleteMapping("/matches/{id}/suspension")
    @Operation(summary = "Undo a suspension (the match goes back to not played)")
    public ResponseDtos.MatchRes unsuspend(@PathVariable Long id) {
        return mService.unsuspend(id);
    }

    @PostMapping("/matches/{id}/swap-schedule")
    @Operation(summary = "Swap the time, field and pitch of two matches of the same edition")
    public List<ResponseDtos.MatchRes> swapSchedule(@PathVariable Long id, @Valid @RequestBody AdminDtos.SwapScheduleReq req) {
        return mService.swapSchedule(id, req.getOtherMatchId());
    }

    @PatchMapping("/matches/{id}/score")
    @Operation(summary = "Load the result as per-player events (score is derived); absent team = walkover 3-0")
    public ResponseDtos.MatchRes score(@PathVariable Long id, @Valid @RequestBody AdminDtos.ScoreUpdateReq req) {
        return mService.updateScore(id, req);
    }
}
