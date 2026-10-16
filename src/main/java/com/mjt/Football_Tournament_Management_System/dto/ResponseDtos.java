package com.mjt.Football_Tournament_Management_System.dto;

import com.mjt.Football_Tournament_Management_System.entire.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** API responses. Entities are never serialized directly. */
public class ResponseDtos {

    /** Minimal team reference, safe for public endpoints (no contact data). */
    public record TeamRef(Long id, String name) {
        public static TeamRef from(Team t) {
            return new TeamRef(t.getId(), t.getName());
        }
    }

    public record TeamRes(Long id, String name, String managerName, String phone, String email, Long captainId) {
        public static TeamRes from(Team t) {
            var captainId = t.getCaptain() == null ? null : t.getCaptain().getId();
            return new TeamRes(t.getId(), t.getName(), t.getManagerName(), t.getPhone(), t.getEmail(), captainId);
        }
    }

    public record PlayerRes(Long id, String fullName, int jerseyNo, Long teamId) {
        public static PlayerRes from(Player p) {
            return new PlayerRes(p.getId(), p.getFullName(), p.getJerseyNo(), p.getTeam().getId());
        }
    }

    public record TournamentRes(Long id, String name, String location,
                                LocalDate registrationStart, LocalDate registrationEnd,
                                LocalDate matchStart, LocalDate matchEnd, String status) {
        public static TournamentRes from(Tournament t) {
            return new TournamentRes(t.getId(), t.getName(), t.getLocation(),
                    t.getRegistrationStart(), t.getRegistrationEnd(),
                    t.getMatchStart(), t.getMatchEnd(), t.getStatus().name());
        }
    }

    public record RegistrationRes(Long id, Long tournamentId, TeamRef team, boolean approved) {
        public static RegistrationRes from(Registration r) {
            return new RegistrationRes(r.getId(), r.getTournament().getId(), TeamRef.from(r.getTeam()), r.isApproved());
        }
    }

    public record MatchRes(Long id, Long tournamentId, TeamRef homeTeam, TeamRef awayTeam,
                           LocalDateTime scheduledAt, String status, Integer homeGoals, Integer awayGoals) {
        public static MatchRes from(MatchGame m) {
            return new MatchRes(m.getId(), m.getTournament().getId(),
                    TeamRef.from(m.getHomeTeam()), TeamRef.from(m.getAwayTeam()),
                    m.getScheduledAt(), m.getStatus().name(), m.getHomeGoals(), m.getAwayGoals());
        }
    }
}
