package com.mjt.Football_Tournament_Management_System.dto;

import com.mjt.Football_Tournament_Management_System.entire.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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

    public record RegistrationRes(Long id, Long tournamentId, Long categoryId, Long zoneId, TeamRef team,
                                  boolean approved) {
        public static RegistrationRes from(Registration r) {
            var z = r.getZone();
            return new RegistrationRes(r.getId(), r.getTournament().getId(),
                    z == null ? null : z.getCategory().getId(), z == null ? null : z.getId(),
                    TeamRef.from(r.getTeam()), r.isApproved());
        }
    }

    public record ZoneRes(Long id, Long categoryId, String name, int displayOrder) {
        public static ZoneRes from(Zone z) {
            return new ZoneRes(z.getId(), z.getCategory().getId(), z.getName(), z.getDisplayOrder());
        }
    }

    /** zoneCount lets the UI hide the zone level when a category has a single zone. */
    public record CategoryRes(Long id, Long tournamentId, String name, int displayOrder,
                              int zoneCount, List<ZoneRes> zones) {}

    public record MatchRes(Long id, Long tournamentId, Long zoneId, TeamRef homeTeam, TeamRef awayTeam,
                           LocalDateTime scheduledAt, String status, Integer homeGoals, Integer awayGoals) {
        public static MatchRes from(MatchGame m) {
            return new MatchRes(m.getId(), m.getTournament().getId(), m.getZone() == null ? null : m.getZone().getId(),
                    TeamRef.from(m.getHomeTeam()), TeamRef.from(m.getAwayTeam()),
                    m.getScheduledAt(), m.getStatus().name(), m.getHomeGoals(), m.getAwayGoals());
        }
    }
}
