package com.mjt.Football_Tournament_Management_System.dto;


import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    public static class MoveRegistrationReq {
        @NotNull private Long zoneId;
    }

    @Data
    public static class ScoreUpdateReq {
        @NotNull @Min(0) private Integer homeGoals, awayGoals;
    }
}
