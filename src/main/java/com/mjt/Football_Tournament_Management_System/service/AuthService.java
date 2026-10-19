package com.mjt.Football_Tournament_Management_System.service;

import com.mjt.Football_Tournament_Management_System.dto.AuthDtos;
import com.mjt.Football_Tournament_Management_System.dto.ResponseDtos;
import com.mjt.Football_Tournament_Management_System.entire.AppUser;
import com.mjt.Football_Tournament_Management_System.entire.Team;
import com.mjt.Football_Tournament_Management_System.exception.BadRequestException;
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

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final AuthenticationManager am;
    private final UserDetailsService uds;
    private final JwtService jwt;
    private final AppUserRepository userRepo;
    private final TeamRepository teamRepo;
    private final PasswordEncoder encoder;
    private final TeamService teamService;

    public AuthDtos.TokenRes login(AuthDtos.LoginReq req) {
        am.authenticate(new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword()));
        return token(req.getUsername());
    }

    /** Public sign-up: always creates a CAPTAIN. Admins are only created via bootstrap. */
    @Transactional
    public AuthDtos.TokenRes register(AuthDtos.RegisterReq req) {
        boolean newTeam = req.getTeamName() != null && !req.getTeamName().isBlank();
        if (newTeam == (req.getClaimTeamId() != null)) {
            throw new BadRequestException("Elegí crear un equipo nuevo o pedir ser capitán de uno que ya está en el torneo");
        }
        checkUsernameFree(req.getUsername());
        var user = userRepo.save(new AppUser(null, req.getUsername(), encoder.encode(req.getPassword()), AppUser.Role.CAPTAIN));
        // Both wait for the admin; if they fail (e.g. the team name exists) the user is not created either
        if (newTeam) teamService.createTeamFor(user, req.getTeamName().trim());
        else teamService.requestClaim(user, req.getClaimTeamId());
        return token(req.getUsername());
    }

    /** Invitation link for the pending captain of a team (the same link until it is used). */
    @Transactional
    public ResponseDtos.InviteRes invite(Long teamId) {
        var team = teamRepo.findById(teamId).orElseThrow(() -> NotFoundException.of("Equipo", teamId));
        var captain = team.getCaptain();
        if (!captain.isPending()) throw new ConflictException("El equipo ya tiene capitán (" + captain.getUsername() + ")");
        if (captain.getInviteToken() == null) captain.setInviteToken(UUID.randomUUID().toString().replace("-", ""));
        return new ResponseDtos.InviteRes(captain.getInviteToken(), team.getName());
    }

    public AuthDtos.InviteInfoRes inviteInfo(String token) {
        return new AuthDtos.InviteInfoRes(invitedTeam(pendingByToken(token)).getName());
    }

    /** The real captain takes over the pending account: picks username and password and is logged in. */
    @Transactional
    public AuthDtos.TokenRes claim(AuthDtos.ClaimReq req) {
        var captain = pendingByToken(req.getToken());
        checkUsernameFree(req.getUsername());
        captain.setUsername(req.getUsername());
        captain.setPasswordHash(encoder.encode(req.getPassword()));
        captain.setPending(false);
        captain.setInviteToken(null);
        userRepo.flush();
        return token(req.getUsername());
    }

    /** A captain who already has an account (and no team) takes the invited team; the pending account goes away. */
    @Transactional
    public AuthDtos.UserRes acceptInvite(String token, String username) {
        var pending = pendingByToken(token);
        var user = userRepo.findByUsername(username).orElseThrow(() -> NotFoundException.of("Usuario", username));
        if (user.getRole() != AppUser.Role.CAPTAIN) throw new ConflictException("Solo un capitán puede aceptar la invitación");
        if (!teamRepo.findByCaptain(user).isEmpty()) throw new ConflictException("Ya sos capitán de un equipo");
        var team = invitedTeam(pending);
        team.setCaptain(user);
        teamRepo.flush();
        userRepo.delete(pending);
        return me(username);
    }

    private AppUser pendingByToken(String token) {
        return userRepo.findByInviteToken(token).filter(AppUser::isPending)
                .orElseThrow(() -> new NotFoundException("La invitación no existe o ya se usó"));
    }

    private Team invitedTeam(AppUser captain) {
        return teamRepo.findByCaptain(captain).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("La invitación no existe o ya se usó"));
    }

    private void checkUsernameFree(String username) {
        if (username.toLowerCase().startsWith(AppUser.PENDING_PREFIX)) {
            throw new BadRequestException("El nombre de usuario no puede empezar con \"" + AppUser.PENDING_PREFIX + "\"");
        }
        if (userRepo.existsByUsername(username)) throw new ConflictException("El nombre de usuario ya está en uso");
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
