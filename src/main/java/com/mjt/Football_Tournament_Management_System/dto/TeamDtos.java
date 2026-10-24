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

    /** Jersey number and/or DNI of a player, changed directly (no approval). Missing field = unchanged; blank DNI = none */
    @Data
    public static class PlayerUpdateReq {
        @Min(0) @Max(999) private Integer jerseyNo;
        @Size(max = 20) private String dni;
    }

    @Data
    public static class AddPlayersReq {
        @NotEmpty private List<@Valid PlayerItem> players;
    }
    @Data
    public static class PlayerItem {
        @NotBlank private String fullName;
        @Min(0) private int jerseyNo;
        /** Optional for now ("30.123.456" or "30123456") */
        @Size(max = 20) private String dni;
    }
}
