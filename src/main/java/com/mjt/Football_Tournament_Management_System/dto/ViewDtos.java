package com.mjt.Football_Tournament_Management_System.dto;


import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

public class ViewDtos {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TeamDashboard {
        private Long teamId;
        private String teamName;
        private int played;
        private int wins;
        private int draws;
        private int losses;
        private int goalsFor;
        private int goalsAgainst;
        private int points;
        private List<Fixture> fixtures;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Fixture {
        private Long matchId;
        private String vs;
        private LocalDateTime at;
        private String status;
        private String score;
    }
}
