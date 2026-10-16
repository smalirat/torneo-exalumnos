package com.mjt.Football_Tournament_Management_System.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

public class AuthDtos {
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
    }

    /** What the invitation page shows before claiming */
    public record InviteInfoRes(String teamName) {}

    public record TokenRes(String token, String tokenType, long expiresInMinutes) {}

    public record UserRes(Long id, String username, String role, List<Long> teamIds) {}
}
