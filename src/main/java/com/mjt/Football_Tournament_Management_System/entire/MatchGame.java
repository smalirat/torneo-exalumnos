package com.mjt.Football_Tournament_Management_System.entire;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name="matches")
public class MatchGame {
    /** Goals awarded to the team that showed up when the other one did not. */
    public static final int WALKOVER_GOALS = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional=false)
    private Tournament tournament;

    // Zone of a regular match; null for special matches (promotion/final)
    @ManyToOne
    private Zone zone;

    @Enumerated(EnumType.STRING)
    private Type type = Type.REGULAR;

    // Rule that picks each side of a special match: position N of a zone (any tournament). Null = fixed team.
    @ManyToOne @JoinColumn(name="home_source_zone_id")
    private Zone homeSourceZone;
    private Integer homeSourcePosition;
    @ManyToOne @JoinColumn(name="away_source_zone_id")
    private Zone awaySourceZone;
    private Integer awaySourcePosition;

    @ManyToOne(optional=false) @JoinColumn(name="home_team_id")
    private Team homeTeam;

    @ManyToOne(optional=false) @JoinColumn(name="away_team_id")
    private Team awayTeam;

    private LocalDateTime scheduledAt;

    /** "Fecha N" of the tournament; null if not numbered. */
    private Integer round;
    /** Label of special rounds, e.g. "Interzonal", "Repetición en zonas". */
    private String roundLabel;
    /** Field ("Anexo", "Siberia") and pitch number. */
    private String venue;
    private String pitch;
    /** Free note shown with the match ("no se presentó, no avisó con anticipación"). */
    private String notes;

    /** Origin of the current data: imported from the Excel or edited on the web. */
    @Enumerated(EnumType.STRING)
    private DataSource source = DataSource.WEB;

    @Enumerated(EnumType.STRING)
    private Status status = Status.SCHEDULED;

    private Integer homeGoals = 0;
    private Integer awayGoals = 0;

    // Nullable wrappers so ddl-auto=update can add the columns to existing rows (null = present / 0)
    private Boolean homePresent = true;
    private Boolean awayPresent = true;
    private Integer homeYellowCards = 0;
    private Integer homeRedCards = 0;
    private Integer awayYellowCards = 0;
    private Integer awayRedCards = 0;

    /** Walkover because a team withdrew from the edition (not a one-off absence): hidden from last/next round */
    private boolean withdrawal;

    // Penalty shoot-out of a drawn promotion/final; null when there was none
    private Integer homePenalties;
    private Integer awayPenalties;

    /** Chronological order with undated matches last (databases put NULL first). */
    public static final Comparator<MatchGame> BY_DATE = Comparator
            .comparing(MatchGame::getScheduledAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(MatchGame::getId);

    public enum Status { SCHEDULED, FINISHED }

    /** REGULAR counts for its zone table; PROMOTION and FINAL are played apart. */
    public enum Type { REGULAR, PROMOTION, FINAL }

    public Type typeOrRegular() { return type == null ? Type.REGULAR : type; }

    public boolean isSpecial() { return typeOrRegular() != Type.REGULAR; }

    /** Winner of a finished match (goals, then penalties); null if not played, drawn or nobody showed up. */
    public Team winner() {
        if (!isFinished() || (!present(true) && !present(false))) return null;
        int h = goalsFor(true), a = goalsFor(false);
        if (h == a && homePenalties != null && awayPenalties != null) {
            h = homePenalties; a = awayPenalties;
        }
        return h > a ? homeTeam : a > h ? awayTeam : null;
    }

    // --- scoring rules (football), shared by standings and team dashboard ---

    public boolean isFinished() { return status == Status.FINISHED; }

    /** League time zone: "today" for walkovers loaded ahead of their date. */
    public static final ZoneId LEAGUE_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    public boolean isWalkover() { return isFinished() && (!present(true) || !present(false)); }

    /**
     * Ids of walkovers decided ahead (e.g. a team withdrew) that do not count yet. A walkover counts once some
     * other match of its round (same zone and round) has been played: rounds get postponed, so its date alone
     * says nothing. Only a round with no other matches falls back to the date.
     */
    public static Set<Long> pendingWalkovers(Collection<MatchGame> matches) {
        var rounds = matches.stream().collect(Collectors.groupingBy(MatchGame::roundKey));
        var out = new HashSet<Long>();
        var today = LocalDate.now(LEAGUE_ZONE);
        for (var round : rounds.values()) {
            var others = round.stream().filter(m -> !m.isWalkover()).toList();
            boolean played = others.stream().anyMatch(MatchGame::isFinished);
            for (var m : round) {
                if (!m.isWalkover()) continue;
                boolean pending = others.isEmpty()
                        ? m.scheduledAt != null && m.scheduledAt.toLocalDate().isAfter(today)
                        : !played;
                if (pending) out.add(m.id);
            }
        }
        return out;
    }

    /** Same zone and type, and same round number (else label, else day). */
    private List<Object> roundKey() {
        Object round = this.round != null ? this.round
                : roundLabel != null ? roundLabel
                : scheduledAt != null ? scheduledAt.toLocalDate() : "";
        return Arrays.asList(tournament.getId(), zone == null ? null : zone.getId(), typeOrRegular(), round);
    }

    /** Finished and already in effect: what standings and dashboards count. */
    public boolean counts(Set<Long> pendingWalkovers) { return isFinished() && !pendingWalkovers.contains(id); }

    public boolean isHome(Team team) { return homeTeam.getId().equals(team.getId()); }

    public boolean present(boolean home) {
        Boolean p = home ? homePresent : awayPresent;
        return p == null || p;
    }

    public int goalsFor(boolean home) { return nz(home ? homeGoals : awayGoals); }

    public int goalsAgainst(boolean home) { return goalsFor(!home); }

    /** 3 win / 1 draw / 0 loss. If both teams were absent nobody gets result points. */
    public int resultPoints(boolean home) {
        if (!present(true) && !present(false)) return 0;
        int f = goalsFor(home), a = goalsAgainst(home);
        return f > a ? 3 : f == a ? 1 : 0;
    }

    /** +1 for showing up. */
    public int presencePoints(boolean home) { return present(home) ? 1 : 0; }

    /** Fair play penalty: 1 per yellow, 3 per red. Lower is better. */
    public int fairPlayPenalty(boolean home) {
        return nz(home ? homeYellowCards : awayYellowCards) + 3 * nz(home ? homeRedCards : awayRedCards);
    }

    private static int nz(Integer v) { return v == null ? 0 : v; }
}
