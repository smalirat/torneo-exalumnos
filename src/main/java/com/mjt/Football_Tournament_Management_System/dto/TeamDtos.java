package com.mjt.Football_Tournament_Management_System.dto;

import lombok.Data;
import java.util.List;

public class TeamDtos {
    @Data
    public static class RegisterTeamReq {
        private String name, managerName, phone, email;
    }

    @Data
    public static class AddPlayersReq {
        private List<PlayerItem> players;
    }
    @Data
    public static class PlayerItem {
        private String fullName; private int jerseyNo;
    }
}
