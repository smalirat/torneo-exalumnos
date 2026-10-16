package com.mjt.Football_Tournament_Management_System.service;

import com.mjt.Football_Tournament_Management_System.dto.AuthDtos;
import com.mjt.Football_Tournament_Management_System.entire.AppUser;
import com.mjt.Football_Tournament_Management_System.entire.Team;
import com.mjt.Football_Tournament_Management_System.exception.ConflictException;
import com.mjt.Football_Tournament_Management_System.exception.NotFoundException;
import com.mjt.Football_Tournament_Management_System.repo.AppUserRepository;
import com.mjt.Football_Tournament_Management_System.repo.TeamRepository;
import com.mjt.Football_Tournament_Management_System.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final AuthenticationManager am;
    private final UserDetailsService uds;
    private final JwtService jwt;
    private final AppUserRepository userRepo;
    private final TeamRepository teamRepo;
    private final PasswordEncoder encoder;

    public AuthDtos.TokenRes login(AuthDtos.LoginReq req) {
        am.authenticate(new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword()));
        return token(req.getUsername());
    }

    /** Public sign-up: always creates a CAPTAIN. Admins are only created via bootstrap. */
    @Transactional
    public AuthDtos.TokenRes register(AuthDtos.RegisterReq req) {
        if (userRepo.existsByUsername(req.getUsername())) {
            throw new ConflictException("El nombre de usuario ya está en uso");
        }
        userRepo.save(new AppUser(null, req.getUsername(), encoder.encode(req.getPassword()), AppUser.Role.CAPTAIN, null));
        return token(req.getUsername());
    }

    public AuthDtos.UserRes me(String username) {
        var u = userRepo.findByUsername(username).orElseThrow(() -> NotFoundException.of("Usuario", username));
        var teamIds = teamRepo.findByCaptain(u).stream().map(Team::getId).toList();
        return new AuthDtos.UserRes(u.getId(), u.getUsername(), u.getRole().name(), teamIds);
    }

    private AuthDtos.TokenRes token(String username) {
        return new AuthDtos.TokenRes(jwt.generate(uds.loadUserByUsername(username)), "Bearer", jwt.getExpirationMinutes());
    }
}
