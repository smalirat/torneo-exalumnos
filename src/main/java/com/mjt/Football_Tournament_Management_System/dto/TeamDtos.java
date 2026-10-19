package com.mjt.Football_Tournament_Management_System.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;

public class TeamDtos {
    @Data
    public static class RegisterTeamReq {
        @NotBlank @Size(max = 100) private String name;
        private String managerName, phone;
        @Email private String email;
    }

    /** Jersey number: cosmetic, changed directly (no approval) */
    @Data
    public static class JerseyReq {
        @Min(0) @Max(999) private int jerseyNo;
    }

    @Data
    public static class AddPlayersReq {
        @NotEmpty private List<@Valid PlayerItem> players;
    }
    @Data
    public static class PlayerItem {
        @NotBlank private String fullName;
        @Min(0) private int jerseyNo;
    }
}
