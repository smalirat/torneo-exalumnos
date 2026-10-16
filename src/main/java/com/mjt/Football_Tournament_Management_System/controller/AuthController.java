package com.mjt.Football_Tournament_Management_System.controller;


import com.mjt.Football_Tournament_Management_System.dto.AuthDtos;
import com.mjt.Football_Tournament_Management_System.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name="Auth API")
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Login; returns a JWT to send as 'Authorization: Bearer <token>'")
    public AuthDtos.TokenRes login(@Valid @RequestBody AuthDtos.LoginReq req) {
        return authService.login(req);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Sign up as team captain; returns a JWT")
    public AuthDtos.TokenRes register(@Valid @RequestBody AuthDtos.RegisterReq req) {
        return authService.register(req);
    }

    @GetMapping("/me")
    @Operation(summary = "Current user, role and owned team ids")
    public AuthDtos.UserRes me(Authentication auth) {
        return authService.me(auth.getName());
    }
}
