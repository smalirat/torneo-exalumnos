package com.mjt.Football_Tournament_Management_System.excel;

import com.mjt.Football_Tournament_Management_System.entire.MatchGame;
import com.mjt.Football_Tournament_Management_System.entire.Sanction;
import com.mjt.Football_Tournament_Management_System.entire.Tournament;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** Everything read from the league's workbook, before touching the database. Names are kept as written. */
public class ParsedWorkbook {
    public int year;
    public final List<TableTeam> tableTeams = new ArrayList<>();
    public final List<ParsedMatch> matches = new ArrayList<>();
    public final List<PlayerValue> goals = new ArrayList<>();
    public final List<PlayerValue> figures = new ArrayList<>();
    public final List<PlayerValue> goalsConceded = new ArrayList<>();
    public final List<ParsedSanction> sanctions = new ArrayList<>();
    public final List<ParsedYellows> yellows = new ArrayList<>();
    public final List<ParsedChampion> champions = new ArrayList<>();
    /** Things skipped while reading (LIBRE rows, placeholders, control rows, unknown labels). */
    public final List<String> notices = new ArrayList<>();

    /** A row of a standings table: says in which group (category or zone) the team plays. */
    public record TableTeam(Tournament.Stage stage, Group group, String team, Integer points, Integer pr,
                            Integer played, String where) {}

    /** "CATEGORIA A" -> category A; "ZONA 2" -> zone named "Zona 2". */
    public record Group(Kind kind, String name) {
        public enum Kind { CATEGORY, ZONE }

        public String label() { return kind == Kind.CATEGORY ? "Categoría " + name : "Zona " + name; }
    }

    /** One side of a match: a team, or a position of a group ("7° CLAUSURA CAT B"). */
    public record Side(String team, Integer position, Tournament.Stage stage, String category) {
        public boolean isPlaceholder() { return team == null; }

        public String key() { return team != null ? Sheet.norm(team) : position + " " + stage + " CAT " + category; }

        public String label() { return team != null ? team : position + "° Cat. " + category; }
    }

    public record ParsedMatch(Tournament.Stage stage, MatchGame.Type type, Group group, boolean interzonal,
                              String labelText, Integer round, String roundLabel, LocalDate date, LocalTime time,
                              String venue, String pitch, Side home, Side away,
                              Integer homeGoals, Integer awayGoals, Integer homePenalties, Integer awayPenalties,
                              boolean homePresent, boolean awayPresent, String notes, String where) {
        public boolean played() { return homeGoals != null && awayGoals != null; }
    }

    public record PlayerValue(String player, String team, int value, String where) {}

    public record ParsedSanction(String player, String team, Sanction.Type type, Tournament.Stage stage,
                                 int matchesSuspended, int matchesServed, Sanction.Status status, String notes,
                                 String where) {}

    public record ParsedYellows(Tournament.Stage stage, String player, String team, int yellowCards, String where) {}

    public record ParsedChampion(int year, String category, String team, String where) {}
}
