package com.mjt.Football_Tournament_Management_System.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;

public class TeamDtos {
    @Data
    public static class RegisterTeamReq {
        @NotBlank @Size(max = 100) private String name;
        @Size(max = 100) private String managerName;
        /** Optional: empty or a phone as people type it */
        @Pattern(regexp = "^(" + AuthDtos.PHONE_BODY + ")?$", message = "solo números, espacios, guiones, paréntesis y +")
        private String phone;
        @Email @Size(max = 120) private String email;
    }

    /** Jersey number and/or DNI of a player, changed directly (no approval). Missing field = unchanged; blank DNI = none */
    @Data
    public static class PlayerUpdateReq {
        @Min(0) @Max(999) private Integer jerseyNo;
        @Size(max = 20) private String dni;
    }

    @Data
    public static class AddPlayersReq {
        @NotEmpty @Size(max = 60) private List<@Valid PlayerItem> players;
    }
    @Data
    public static class PlayerItem {
        @NotBlank @Size(max = 100) private String fullName;
        @Min(0) @Max(999) private int jerseyNo;
        /** Optional for now ("30.123.456" or "30123456") */
        @Size(max = 20) private String dni;
    }
}
