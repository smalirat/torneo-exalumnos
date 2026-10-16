package com.mjt.Football_Tournament_Management_System.controller;

import com.mjt.Football_Tournament_Management_System.dto.ResponseDtos;
import com.mjt.Football_Tournament_Management_System.service.FollowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Things of the logged-in user (any role): followed tournaments and notifications. */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
@Tag(name = "Me API")
public class MeController {
    private final FollowService followService;

    @GetMapping("/follows")
    @Operation(summary = "Tournaments the user follows, most recent first")
    public List<ResponseDtos.TournamentRes> follows(Authentication auth) {
        return followService.followed(auth.getName());
    }

    @PutMapping("/follows/{tournamentId}")
    @Operation(summary = "Follow a tournament (idempotent)")
    public ResponseDtos.FollowStatusRes follow(@PathVariable Long tournamentId, Authentication auth) {
        return followService.follow(auth.getName(), tournamentId);
    }

    @DeleteMapping("/follows/{tournamentId}")
    @Operation(summary = "Stop following a tournament (idempotent)")
    public ResponseDtos.FollowStatusRes unfollow(@PathVariable Long tournamentId, Authentication auth) {
        return followService.unfollow(auth.getName(), tournamentId);
    }

    @GetMapping("/notifications")
    @Operation(summary = "Latest news of the followed tournaments and how many are unread")
    public ResponseDtos.NotificationsRes notifications(Authentication auth) {
        return followService.notifications(auth.getName());
    }

    @PostMapping("/notifications/seen")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Mark every notification as read")
    public void seen(Authentication auth) {
        followService.markSeen(auth.getName());
    }
}
