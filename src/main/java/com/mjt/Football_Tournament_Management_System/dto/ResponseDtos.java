package com.mjt.Football_Tournament_Management_System.dto;

import com.mjt.Football_Tournament_Management_System.entire.*;
import com.mjt.Football_Tournament_Management_System.excel.ImportReport;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** API responses. Entities are never serialized directly. */
public class ResponseDtos {

    /** Minimal team reference, safe for public endpoints (no contact data). */
    /** crest: URL of the team's crest image, null when it has none (the UI then shows no badge). */
    public record TeamRef(Long id, String name, String crest) {
        public static TeamRef from(Team t) {
            return new TeamRef(t.getId(), t.getName(), crestUrl(t));
        }
    }

    static String crestUrl(Team t) {
        return t.getCrestVersion() == null ? null : "/api/public/teams/" + t.getId() + "/crest?v=" + t.getCrestVersion();
    }

    public record TeamRes(Long id, String name, String managerName, String phone, String email, Long captainId, String crest) {
        public static TeamRes from(Team t) {
            return new TeamRes(t.getId(), t.getName(), t.getManagerName(), t.getPhone(), t.getEmail(), t.getCaptain().getId(),
                    crestUrl(t));
        }
    }

    /**
     * Team list item for the team registry screen. captainUsername is null while the captain is pending
     * (nobody claimed the team yet: the admin can send an invitation link).
     */
    public record TeamSummaryRes(Long id, String name, String crest, Long captainId, String captainUsername,
                                 boolean captainPending, int playerCount) {
        public static TeamSummaryRes of(Team t, int playerCount) {
            var c = t.getCaptain();
            return new TeamSummaryRes(t.getId(), t.getName(), crestUrl(t), c.getId(), c.isPending() ? null : c.getUsername(),
                    c.isPending(), playerCount);
        }
    }

    /** Invitation to claim a team's pending captain account: the link is /invitacion/{token}. */
    public record InviteRes(String token, String teamName) {}

    public record PlayerRef(Long id, String fullName, int jerseyNo) {
        public static PlayerRef from(Player p) {
            return new PlayerRef(p.getId(), p.getFullName(), p.getJerseyNo());
        }
    }

    public record MatchEventRes(Long id, Long matchId, PlayerRef player, TeamRef team, String type) {
        public static MatchEventRes from(MatchEvent e) {
            return new MatchEventRes(e.getId(), e.getMatch().getId(), PlayerRef.from(e.getPlayer()),
                    TeamRef.from(e.getTeam()), e.getType().name());
        }
    }

    public record ScorerRow(PlayerRef player, TeamRef team, int goals) {}

    /** Annual ranking line: `value` is goals, times chosen figure or goals conceded depending on the ranking. */
    public record SeasonStatRow(int position, PlayerRef player, TeamRef team, int value) {}

    public record SanctionRes(Long id, Long seasonId, Long tournamentId, PlayerRef player, TeamRef team, String type,
                              int matchesSuspended, int matchesServed, int pending, String status, String notes,
                              String source) {
        public static SanctionRes from(Sanction s) {
            return new SanctionRes(s.getId(), s.getSeason().getId(),
                    s.getTournament() == null ? null : s.getTournament().getId(),
                    PlayerRef.from(s.getPlayer()), TeamRef.from(s.getTeam()), s.getType().name(),
                    s.getMatchesSuspended(), s.getMatchesServed(), s.pending(), s.getStatus().name(), s.getNotes(),
                    s.getSource() == null ? null : s.getSource().name());
        }
    }

    /** Accumulated yellows in a tournament; suspensions = yellows / 4. */
    public record YellowTallyRes(PlayerRef player, TeamRef team, int yellowCards, int suspensions) {
        public static YellowTallyRes from(YellowCardTally y) {
            return new YellowTallyRes(PlayerRef.from(y.getPlayer()), TeamRef.from(y.getTeam()),
                    y.getYellowCards(), y.suspensions());
        }
    }

    public record ChampionRes(Long id, int year, String categoryName, TeamRef team) {
        public static ChampionRes from(Champion c) {
            return new ChampionRes(c.getId(), c.getYear(), c.getCategoryName(), TeamRef.from(c.getTeam()));
        }
    }

    public record TitleCount(TeamRef team, int titles) {}

    /** x, y, w: fractions of the board width; z: layer (higher on top). The image is at `url`. */
    public record CollagePhotoRes(Long id, String url, int width, int height, double x, double y, double w, int z,
                                  boolean featured) {
        public CollagePhotoRes(Long id, int width, int height, double x, double y, double w, int z, boolean featured) {
            this(id, "/api/public/collage/" + id + "/image", width, height, x, y, w, z, featured);
        }
        public static CollagePhotoRes from(CollagePhoto p) {
            return new CollagePhotoRes(p.getId(), p.getWidth(), p.getHeight(), p.getPosX(), p.getPosY(), p.getSize(),
                    p.getLayer(), p.isFeatured());
        }
    }

    /** Result of an Excel upload (preview) or of applying it. */
    public record ImportPreviewRes(Long importId, String fileName, String status, Integer seasonYear,
                                   List<ImportReport.Counter> counters, List<ImportReport.Change> changes,
                                   List<ImportReport.Conflict> conflicts, List<String> warnings, List<String> notices) {
        public static ImportPreviewRes of(ExcelImport imp, ImportReport r) {
            return new ImportPreviewRes(imp.getId(), imp.getFileName(), imp.getStatus().name(), imp.getSeasonYear(),
                    r.counters(), r.changes, r.conflicts, r.warnings, r.notices);
        }
    }

    public record ImportHistoryRes(Long id, String fileName, String uploadedBy, LocalDateTime createdAt,
                                   LocalDateTime appliedAt, String status, Integer seasonYear, String summary) {
        public static ImportHistoryRes from(ExcelImport i) {
            return new ImportHistoryRes(i.getId(), i.getFileName(), i.getUploadedBy(), i.getCreatedAt(), i.getAppliedAt(),
                    i.getStatus().name(), i.getSeasonYear(), i.getSummary());
        }
    }

    /** History of one category: champions by year (newest first) and teams ranked by titles. */
    public record CategoryHistory(String categoryName, List<ChampionRes> champions, List<TitleCount> topChampions) {}

    public record CardRow(PlayerRef player, TeamRef team, int yellowCards, int redCards) {}

    public record PlayerRes(Long id, String fullName, int jerseyNo, Long teamId) {
        public static PlayerRes from(Player p) {
            return new PlayerRes(p.getId(), p.getFullName(), p.getJerseyNo(), p.getTeam().getId());
        }
    }

    public record LeagueRes(String title) {}

    public record TournamentRes(Long id, String name, String location,
                                LocalDate registrationStart, LocalDate registrationEnd,
                                LocalDate matchStart, LocalDate matchEnd, String status,
                                Long seasonId, Integer seasonYear, String stage) {
        public static TournamentRes from(Tournament t) {
            var s = t.getSeason();
            return new TournamentRes(t.getId(), t.getName(), t.getLocation(),
                    t.getRegistrationStart(), t.getRegistrationEnd(),
                    t.getMatchStart(), t.getMatchEnd(), t.getStatus().name(),
                    s == null ? null : s.getId(), s == null ? null : s.getYear(),
                    t.getStage() == null ? null : t.getStage().name());
        }
    }

    public record SeasonRes(Long id, int year, List<TournamentRes> tournaments) {}

    public record RegistrationRes(Long id, Long tournamentId, Long categoryId, Long zoneId, TeamRef team,
                                  boolean approved, boolean withdrawn, LocalDate withdrawnAt) {
        public static RegistrationRes from(Registration r) {
            var z = r.getZone();
            return new RegistrationRes(r.getId(), r.getTournament().getId(),
                    z == null ? null : z.getCategory().getId(), z == null ? null : z.getId(),
                    TeamRef.from(r.getTeam()), r.isApproved(), r.isWithdrawn(), r.getWithdrawnAt());
        }
    }

    /** A team's place in a tournament: category/zone are null until the admin assigns them. */
    public record TeamRegistrationRes(Long registrationId, TournamentRes tournament, Long categoryId, String categoryName,
                                      Long zoneId, String zoneName, int zoneCount, int categoryCount) {
        public static TeamRegistrationRes from(Registration r) {
            var z = r.getZone();
            var c = z == null ? null : z.getCategory();
            return new TeamRegistrationRes(r.getId(), TournamentRes.from(r.getTournament()),
                    c == null ? null : c.getId(), c == null ? null : c.getName(),
                    z == null ? null : z.getId(), z == null ? null : z.getName(), c == null ? 0 : c.getZoneCount(),
                    r.getTournament().getCategoryCount());
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

    /** homeSource/awaySource: rule that picked the side of a promotion/final (null = fixed team). */
    public record MatchRes(Long id, Long tournamentId, Long zoneId, String type, TeamRef homeTeam, TeamRef awayTeam,
                           SlotRes homeSource, SlotRes awaySource,
                           LocalDateTime scheduledAt, Integer round, String roundLabel, String venue, String pitch,
                           String notes, String status, Integer homeGoals, Integer awayGoals,
                           Integer homePenalties, Integer awayPenalties, TeamRef winner,
                           boolean homePresent, boolean awayPresent, boolean advanceWalkover, boolean withdrawal) {
        /** For a match that is not a walkover waiting for its round (new, unplayed or special). */
        public static MatchRes from(MatchGame m) { return from(m, Set.of()); }

        /** pendingWalkovers: from {@link MatchGame#pendingWalkovers}, over the tournament's matches. */
        public static MatchRes from(MatchGame m, Set<Long> pendingWalkovers) {
            return new MatchRes(m.getId(), m.getTournament().getId(), m.getZone() == null ? null : m.getZone().getId(),
                    m.typeOrRegular().name(), TeamRef.from(m.getHomeTeam()), TeamRef.from(m.getAwayTeam()),
                    SlotRes.of(m.getHomeSourceZone(), m.getHomeSourcePosition()),
                    SlotRes.of(m.getAwaySourceZone(), m.getAwaySourcePosition()),
                    m.getScheduledAt(), m.getRound(), m.getRoundLabel(), m.getVenue(), m.getPitch(), m.getNotes(),
                    m.getStatus().name(), m.getHomeGoals(), m.getAwayGoals(),
                    m.getHomePenalties(), m.getAwayPenalties(),
                    m.winner() == null ? null : TeamRef.from(m.winner()),
                    m.present(true), m.present(false), pendingWalkovers.contains(m.getId()), m.isWithdrawal());
        }
    }

    /** Rule "position N of zone X"; names included so the UI can label it ("3º · A (Apertura)"). */
    public record SlotRes(Long zoneId, String zoneName, int zoneCount, String categoryName, int categoryCount,
                          Long tournamentId, String tournamentName, int position) {
        static SlotRes of(Zone zone, Integer position) {
            if (zone == null || position == null) return null;
            var c = zone.getCategory();
            return new SlotRes(zone.getId(), zone.getName(), c.getZoneCount(), c.getName(), c.getTournament().getCategoryCount(),
                    c.getTournament().getId(), c.getTournament().getName(), position);
        }
    }

    /** One line of a zone table. fairPlay is a penalty (1 per yellow, 3 per red): lower is better. */
    public record StandingRow(int position, TeamRef team, int points, int played, int won, int drawn, int lost,
                              int goalsFor, int goalsAgainst, int goalDiff, int presencePoints, int fairPlay) {}
}
