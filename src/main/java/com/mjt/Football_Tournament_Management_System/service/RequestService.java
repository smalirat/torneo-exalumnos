package com.mjt.Football_Tournament_Management_System.service;

import com.mjt.Football_Tournament_Management_System.dto.ResponseDtos;
import com.mjt.Football_Tournament_Management_System.entire.*;
import com.mjt.Football_Tournament_Management_System.entire.RequestDecision.Type;
import com.mjt.Football_Tournament_Management_System.exception.ConflictException;
import com.mjt.Football_Tournament_Management_System.exception.NotFoundException;
import com.mjt.Football_Tournament_Management_System.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Captains' changes waiting for the admin: a new team, a rename, deleting the team, adding / removing a player
 * and registering in an edition. They live as marks on the rows (see V12); approving applies the change,
 * rejecting drops it. Every answer is logged so the captain can see it.
 */
@Service
@RequiredArgsConstructor
public class RequestService {
    private final TeamRepository teamRepo;
    private final PlayerRepository playerRepo;
    private final RegistrationRepository regRepo;
    private final RequestDecisionRepository decisionRepo;
    private final AppUserRepository userRepo;
    private final TeamService teamService;

    /** Every pending request, oldest first */
    @Transactional(readOnly = true)
    public List<ResponseDtos.RequestRes> pending() {
        var out = new ArrayList<ResponseDtos.RequestRes>();
        for (var t : teamRepo.findByApprovedFalse()) {
            out.add(req(Type.TEAM, t.getId(), t, t.getName(), t.getCreatedAt()));
            // Players of a team in review go with the team: approved together
        }
        for (var t : teamRepo.findByPendingNameNotNull()) out.add(req(Type.RENAME, t.getId(), t, t.getPendingName(), t.getPendingNameAt()));
        for (var t : teamRepo.findByDeletionRequestedAtNotNull()) out.add(req(Type.DELETE_TEAM, t.getId(), t, t.getName(), t.getDeletionRequestedAt()));
        for (var p : playerRepo.findByApprovedFalseAndTeam_ApprovedTrue()) out.add(req(Type.PLAYER_ADD, p.getId(), p.getTeam(), playerName(p), p.getRequestedAt()));
        for (var p : playerRepo.findByRemovalRequestedAtNotNull()) out.add(req(Type.PLAYER_REMOVE, p.getId(), p.getTeam(), playerName(p), p.getRemovalRequestedAt()));
        for (var r : regRepo.findByApprovedFalse()) out.add(req(Type.REGISTRATION, r.getId(), r.getTeam(), r.getTournament().getName(), r.getRequestedAt()));
        out.sort(Comparator.comparing(ResponseDtos.RequestRes::requestedAt, Comparator.nullsFirst(Comparator.naturalOrder())));
        return out;
    }

    @Transactional(readOnly = true)
    public int count() { return pending().size(); }

    /** The captain's own pending requests and the latest answers */
    @Transactional(readOnly = true)
    public ResponseDtos.MyRequestsRes mine(String username) {
        var me = userRepo.findByUsername(username).orElseThrow(() -> NotFoundException.of("Usuario", username));
        var mine = pending().stream().filter(r -> r.captain() != null && r.captain().equals(username)).toList();
        var decisions = decisionRepo.findTop20ByCaptainOrderByDecidedAtDesc(me).stream().map(ResponseDtos.DecisionRes::from).toList();
        return new ResponseDtos.MyRequestsRes(mine, decisions);
    }

    @Transactional
    public void approve(Type type, Long id) {
        switch (type) {
            case TEAM -> {
                var t = team(id);
                if (t.isApproved()) throw done();
                t.setApproved(true);
                // Its roster is approved with it
                playerRepo.findByTeamOrderByJerseyNoAscFullNameAsc(t).forEach(p -> { p.setApproved(true); p.setRequestedAt(null); });
                log(t, type, t.getName(), true, null);
            }
            case RENAME -> {
                var t = team(id);
                if (t.getPendingName() == null) throw done();
                var name = t.getPendingName();
                teamRepo.findByName(name).filter(o -> !o.getId().equals(t.getId())).ifPresent(o -> {
                    throw new ConflictException("Ya hay un equipo llamado " + name);
                });
                log(t, type, name, true, null);
                t.setName(name); t.setPendingName(null); t.setPendingNameAt(null);
            }
            case DELETE_TEAM -> {
                var t = team(id);
                if (t.getDeletionRequestedAt() == null) throw done();
                logGone(t, type, true, null);
                teamService.removeTeam(t);
            }
            case PLAYER_ADD -> {
                var p = player(id);
                if (p.isApproved()) throw done();
                p.setApproved(true); p.setRequestedAt(null);
                log(p.getTeam(), type, playerName(p), true, null);
            }
            case PLAYER_REMOVE -> {
                var p = player(id);
                if (p.getRemovalRequestedAt() == null) throw done();
                log(p.getTeam(), type, playerName(p), true, null);
                teamService.removePlayer(p);
            }
            case REGISTRATION -> {
                var r = registration(id);
                if (r.isApproved()) throw done();
                r.setApproved(true); r.setRequestedAt(null);
                log(r.getTeam(), type, r.getTournament().getName(), true, null);
            }
        }
    }

    @Transactional
    public void reject(Type type, Long id, String reason) {
        var why = reason == null || reason.isBlank() ? null : reason.trim();
        switch (type) {
            case TEAM -> {
                var t = team(id);
                if (t.isApproved()) throw done();
                logGone(t, type, false, why);
                teamService.removeTeam(t); // the captain's account stays: they can create another one
            }
            case RENAME -> {
                var t = team(id);
                if (t.getPendingName() == null) throw done();
                log(t, type, t.getPendingName(), false, why);
                t.setPendingName(null); t.setPendingNameAt(null);
            }
            case DELETE_TEAM -> {
                var t = team(id);
                if (t.getDeletionRequestedAt() == null) throw done();
                log(t, type, t.getName(), false, why);
                t.setDeletionRequestedAt(null);
            }
            case PLAYER_ADD -> {
                var p = player(id);
                if (p.isApproved()) throw done();
                log(p.getTeam(), type, playerName(p), false, why);
                playerRepo.delete(p);
            }
            case PLAYER_REMOVE -> {
                var p = player(id);
                if (p.getRemovalRequestedAt() == null) throw done();
                log(p.getTeam(), type, playerName(p), false, why);
                p.setRemovalRequestedAt(null);
            }
            case REGISTRATION -> {
                var r = registration(id);
                if (r.isApproved()) throw done();
                log(r.getTeam(), type, r.getTournament().getName(), false, why);
                regRepo.delete(r);
            }
        }
    }

    /** "Aprobar todo" of a team: everything it has pending except deleting it, which is always decided apart */
    @Transactional
    public int approveAll(Long teamId) {
        var mine = pending().stream()
                .filter(r -> teamId.equals(r.teamId()) && !Type.DELETE_TEAM.name().equals(r.type()))
                .toList();
        // The team first, so its roster is approved with it
        mine.stream().filter(r -> Type.TEAM.name().equals(r.type())).forEach(r -> approve(Type.TEAM, r.id()));
        mine.stream().filter(r -> !Type.TEAM.name().equals(r.type()))
                .forEach(r -> approve(Type.valueOf(r.type()), r.id()));
        return mine.size();
    }

    // ---------------------------------------------------------------- helpers

    private ResponseDtos.RequestRes req(Type type, Long id, Team t, String subject, LocalDateTime at) {
        var c = t.getCaptain();
        return new ResponseDtos.RequestRes(type.name(), id, t.getId(), t.getName(), c == null || c.isPending() ? null : c.getUsername(),
                subject, at);
    }

    private void log(Team t, Type type, String subject, boolean approved, String reason) {
        decisionRepo.save(new RequestDecision(null, t, t.getName(), t.getCaptain(), type, subject, approved, reason, LocalDateTime.now()));
    }

    /** For a decision that deletes the team: the log keeps its name but no link to it */
    private void logGone(Team t, Type type, boolean approved, String reason) {
        var captain = t.getCaptain().isPending() ? null : t.getCaptain();
        decisionRepo.save(new RequestDecision(null, null, t.getName(), captain, type, t.getName(), approved, reason, LocalDateTime.now()));
    }

    private static String playerName(Player p) {
        return p.getFullName() + (p.getJerseyNo() > 0 ? " (" + p.getJerseyNo() + ")" : "");
    }

    private static ConflictException done() {
        return new ConflictException("La solicitud ya fue resuelta");
    }

    private Team team(Long id) { return teamRepo.findById(id).orElseThrow(() -> NotFoundException.of("Equipo", id)); }
    private Player player(Long id) { return playerRepo.findById(id).orElseThrow(() -> NotFoundException.of("Jugador", id)); }
    private Registration registration(Long id) { return regRepo.findById(id).orElseThrow(() -> NotFoundException.of("Inscripción", id)); }
}
