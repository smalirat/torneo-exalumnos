package com.mjt.Football_Tournament_Management_System.dto;


import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.mjt.Football_Tournament_Management_System.entire.MatchEvent;
import com.mjt.Football_Tournament_Management_System.entire.Tournament;
import com.mjt.Football_Tournament_Management_System.entire.MatchGame;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AdminDtos {
    @Data
    public static class CreateTournamentReq {
        @NotBlank private String name;
        private String location;
        @NotNull private LocalDate registrationStart, registrationEnd;
        private LocalDate matchStart, matchEnd;
        /** Season year (created if missing) and stage; both optional. */
        @Min(1900) @Max(2100) private Integer seasonYear;
        private Tournament.Stage stage;
    }
    @Data
    public static class UpdateTournamentReq {
        @NotBlank private String name;
        private String location;
        @NotNull private LocalDate registrationStart, registrationEnd;
        private LocalDate matchStart, matchEnd;
        @NotNull private Tournament.Status status;
        @Min(1900) @Max(2100) private Integer seasonYear;
        private Tournament.Stage stage;
    }

    /**
     * Regular matches. With zoneId both teams must be in that zone; without it (tournamentId instead) it is an
     * interzonal match between teams of the tournament, which counts in each team's zone table.
     */
    @Data
    public static class ManualFixtureReq {
        private Long zoneId;
        private Long tournamentId;
        @NotEmpty private List<@Valid FixtureItem> fixtures;

        @AssertTrue(message = "indicar zoneId o tournamentId")
        public boolean isTargetGiven() { return zoneId != null || tournamentId != null; }
    }

    @Data
    public static class FixtureItem {
        @NotNull private Long homeTeamId, awayTeamId;
        private LocalDateTime scheduledAt;
        @Min(1) private Integer round;
        @Size(max = 80) private String roundLabel;
        @Size(max = 60) private String venue;
        @Size(max = 20) private String pitch;
        @Size(max = 500) private String notes;
    }

    /** Editable match data (not the result). */
    @Data
    public static class MatchDetailsReq {
        private LocalDateTime scheduledAt;
        @Min(1) private Integer round;
        @Size(max = 80) private String roundLabel;
        @Size(max = 60) private String venue;
        @Size(max = 20) private String pitch;
        @Size(max = 500) private String notes;
    }
    /** Name for a category or zone. */
    @Data
    public static class NameReq {
        @NotBlank @Size(max = 60) private String name;
    }

    @Data
    public static class AssignZoneReq {
        @NotNull private Long zoneId;
    }

    /**
     * Match result as per-player events; the score is derived from them (0-0 = no goal events).
     * If a team did not show up (present=false) no events are allowed and the other team wins 3-0.
     */
    @Data
    public static class ScoreUpdateReq {
        private boolean homePresent = true, awayPresent = true;
        @NotNull private List<@Valid EventItem> events = new ArrayList<>();
        /** Only for a promotion/final that ends in a draw; required then, and cannot be tied. */
        @Min(0) private Integer homePenalties, awayPenalties;
    }

    /** Promotion or final; each side is a fixed team or "position N of zone X". */
    @Data
    public static class SpecialMatchReq {
        @NotNull private MatchGame.Type type;
        private LocalDateTime scheduledAt;
        @NotNull @Valid private SlotReq home;
        @NotNull @Valid private SlotReq away;

        @AssertTrue(message = "debe ser PROMOTION o FINAL")
        public boolean isSpecialType() { return type == null || type != MatchGame.Type.REGULAR; }
    }

    @Data
    public static class SlotReq {
        private Long teamId;
        private Long zoneId;
        @Min(1) private Integer position;
    }

    @Data
    public static class EventItem {
        @NotNull private Long playerId;
        @NotNull private MatchEvent.Type type;
    }
}
