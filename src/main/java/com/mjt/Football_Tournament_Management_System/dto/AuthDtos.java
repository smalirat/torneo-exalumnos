package com.mjt.Football_Tournament_Management_System.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

public class AuthDtos {
    /** A phone as people type it: "11 5555-1234", "+54 9 11 5555 1234", "(011) 4555-1234" */
    static final String PHONE = "^\\+?[0-9 ()\\-]{6,30}$";

    @Data
    public static class LoginReq {
        @NotBlank private String username;
        @NotBlank private String password;
    }

    @Data
    public static class RegisterReq {
        @NotBlank @Size(min = 3, max = 50)
        @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "solo letras, números, punto, guion y guion bajo")
        private String username;
        @NotBlank @Size(min = 8, max = 100)
        private String password;
        @NotBlank @Email @Size(max = 120) private String email;
        @NotBlank @Pattern(regexp = PHONE, message = "solo números, espacios, guiones, paréntesis y +") @Size(max = 30)
        private String phone;
        /** Signing up goes with a team: either a new one (teamName) or one without captain (claimTeamId). */
        @Size(max = 100) private String teamName;
        private Long claimTeamId;
    }

    /** A user without team asks to be the captain of a team that has none */
    @Data
    public static class ClaimTeamReq {
        @NotNull private Long teamId;
    }

    /** Claim a pending captain account from an invitation link: the real captain picks username and password. */
    @Data
    public static class ClaimReq {
        @NotBlank private String token;
        @NotBlank @Size(min = 3, max = 50)
        @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "solo letras, números, punto, guion y guion bajo")
        private String username;
        @NotBlank @Size(min = 8, max = 100)
        private String password;
        @NotBlank @Email @Size(max = 120) private String email;
        @NotBlank @Pattern(regexp = PHONE, message = "solo números, espacios, guiones, paréntesis y +") @Size(max = 30)
        private String phone;
    }

    /** The user's own contact ("Mis datos") */
    @Data
    public static class ContactReq {
        @NotBlank @Email @Size(max = 120) private String email;
        @NotBlank @Pattern(regexp = PHONE, message = "solo números, espacios, guiones, paréntesis y +") @Size(max = 30)
        private String phone;
    }

    /** What the invitation page shows before claiming */
    public record InviteInfoRes(String teamName) {}

    public record TokenRes(String token, String tokenType, long expiresInMinutes) {}

    public record UserRes(Long id, String username, String role, List<Long> teamIds, String email, String phone) {}
}
