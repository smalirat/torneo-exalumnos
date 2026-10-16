package com.mjt.Football_Tournament_Management_System.dto;


import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class AdminDtos {
    @Data
    public static class CreateTournamentReq {
        private String name, location;
        private LocalDate registrationStart, registrationEnd, matchStart, matchEnd;
    }
    @Data
    public static class ManualFixtureReq {
        private Long tournamentId; private List<FixtureItem> fixtures;
    }
    @Data
    public static class FixtureItem {
        private Long homeTeamId, awayTeamId;
        private LocalDateTime scheduledAt;
    }
    @Data
    public static class ScoreUpdateReq {
        private Integer homeGoals, awayGoals;
    }
}
