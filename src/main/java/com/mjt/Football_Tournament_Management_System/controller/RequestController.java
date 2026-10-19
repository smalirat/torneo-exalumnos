package com.mjt.Football_Tournament_Management_System.controller;

import com.mjt.Football_Tournament_Management_System.dto.AdminDtos;
import com.mjt.Football_Tournament_Management_System.dto.ResponseDtos;
import com.mjt.Football_Tournament_Management_System.entire.RequestDecision;
import com.mjt.Football_Tournament_Management_System.service.RequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Captains' changes waiting for the admin (types: TEAM, RENAME, DELETE_TEAM, PLAYER_ADD, PLAYER_REMOVE, REGISTRATION). */
@RestController
@RequiredArgsConstructor
@Tag(name="Captain requests")
public class RequestController {
    private final RequestService service;

    @GetMapping("/api/admin/requests")
    @Operation(summary = "Pending requests, oldest first")
    public List<ResponseDtos.RequestRes> pending() { return service.pending(); }

    @GetMapping("/api/admin/requests/count")
    @Operation(summary = "How many requests are pending (for the sidebar counter)")
    public Map<String, Integer> count() { return Map.of("count", service.count()); }

    @PostMapping("/api/admin/requests/{type}/{id}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Approve a request: the change is applied now")
    public void approve(@PathVariable RequestDecision.Type type, @PathVariable Long id) { service.approve(type, id); }

    @PostMapping("/api/admin/requests/{type}/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Reject a request (optional reason, shown to the captain)")
    public void reject(@PathVariable RequestDecision.Type type, @PathVariable Long id,
                       @Valid @RequestBody(required = false) AdminDtos.RejectReq req) {
        service.reject(type, id, req == null ? null : req.getReason());
    }

    @PostMapping("/api/admin/requests/teams/{teamId}/approve-all")
    @Operation(summary = "Approve everything a team has pending, except deleting it")
    public Map<String, Integer> approveAll(@PathVariable Long teamId) { return Map.of("approved", service.approveAll(teamId)); }

    @GetMapping("/api/me/requests")
    @Operation(summary = "The captain's pending requests and the latest answers")
    public ResponseDtos.MyRequestsRes mine(Authentication auth) { return service.mine(auth.getName()); }
}
