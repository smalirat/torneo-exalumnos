package com.mjt.Football_Tournament_Management_System.entire;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

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

    // Zone of a regular match; null for special matches (promotion/final, coming later)
    @ManyToOne
    private Zone zone;

    @ManyToOne(optional=false) @JoinColumn(name="home_team_id")
    private Team homeTeam;

    @ManyToOne(optional=false) @JoinColumn(name="away_team_id")
    private Team awayTeam;

    private LocalDateTime scheduledAt;

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

    public enum Status { SCHEDULED, FINISHED }

    // --- scoring rules (football), shared by standings and team dashboard ---

    public boolean isFinished() { return status == Status.FINISHED; }

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
