package com.mjt.Football_Tournament_Management_System.controller;

import com.mjt.Football_Tournament_Management_System.dto.AuthDtos;
import com.mjt.Football_Tournament_Management_System.dto.ResponseDtos;
import com.mjt.Football_Tournament_Management_System.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Teams created without a captain (Excel import / admin) get a pending one; the real captain claims it by link. */
@RestController
@RequiredArgsConstructor
@Tag(name="Captain invitations")
public class InviteController {
    private final AuthService authService;

    @PostMapping("/api/admin/teams/{teamId}/invite")
    @Operation(summary = "Invitation link for the team's pending captain (same token until used); 409 if already claimed")
    public ResponseDtos.InviteRes invite(@PathVariable Long teamId) { return authService.invite(teamId); }

    @GetMapping("/api/auth/invites/{token}")
    @Operation(summary = "Team of an invitation, for the claim page; 404 if it does not exist or was used")
    public AuthDtos.InviteInfoRes info(@PathVariable String token) { return authService.inviteInfo(token); }

    @PostMapping("/api/me/invites/{token}/accept")
    @Operation(summary = "Logged-in captain without a team takes the invited team with their own account")
    public AuthDtos.UserRes accept(@PathVariable String token, Authentication auth) {
        return authService.acceptInvite(token, auth.getName());
    }

    @PostMapping("/api/auth/claim")
    @Operation(summary = "Claim the pending captain account: choose username and password; returns a JWT")
    public AuthDtos.TokenRes claim(@Valid @RequestBody AuthDtos.ClaimReq req) { return authService.claim(req); }
}
