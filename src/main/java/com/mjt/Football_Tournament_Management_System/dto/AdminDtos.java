package com.mjt.Football_Tournament_Management_System.dto;


import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.mjt.Football_Tournament_Management_System.entire.MatchEvent;
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
    }
    @Data
    public static class ManualFixtureReq {
        @NotNull private Long zoneId;
        @NotEmpty private List<@Valid FixtureItem> fixtures;
    }
    @Data
    public static class FixtureItem {
        @NotNull private Long homeTeamId, awayTeamId;
        private LocalDateTime scheduledAt;
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
