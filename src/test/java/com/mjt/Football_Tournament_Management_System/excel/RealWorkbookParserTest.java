package com.mjt.Football_Tournament_Management_System.excel;

import com.mjt.Football_Tournament_Management_System.entire.MatchGame;
import com.mjt.Football_Tournament_Management_System.entire.Tournament;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Checks the parser against the league's real workbook. The file is local only (design/ is not versioned,
 * it has real names), so the test is skipped when it is not there.
 */
class RealWorkbookParserTest {
    private static final Path FILE = Path.of("design", "TORNEO CLAUSURA 2026-7.xlsx");

    @Test
    void readsTheRealWorkbook() throws Exception {
        assumeTrue(Files.exists(FILE), "real workbook not present");
        ParsedWorkbook wb;
        try (InputStream in = Files.newInputStream(FILE)) {
            wb = new WorkbookParser().parse(in);
        }

        System.out.println("year=" + wb.year + " tableTeams=" + wb.tableTeams.size() + " matches=" + wb.matches.size()
                + " goals=" + wb.goals.size() + " figures=" + wb.figures.size() + " gk=" + wb.goalsConceded.size()
                + " sanctions=" + wb.sanctions.size() + " yellows=" + wb.yellows.size() + " champions=" + wb.champions.size()
                + " notices=" + wb.notices.size());
        System.out.println("groups: " + wb.tableTeams.stream()
                .collect(Collectors.groupingBy(t -> t.stage() + " " + t.group().label(), Collectors.counting())));
        System.out.println("matches by stage/type: " + wb.matches.stream()
                .collect(Collectors.groupingBy(m -> m.stage() + " " + m.type() + (m.interzonal() ? " IZ" : "") + (m.played() ? " played" : ""), Collectors.counting())));
        wb.matches.stream().filter(m -> m.type() != MatchGame.Type.REGULAR || !m.homePresent() || !m.awayPresent())
                .limit(12).forEach(m -> System.out.println("  " + m));
        wb.notices.stream().limit(15).forEach(n -> System.out.println("  notice: " + n));
        wb.sanctions.stream().limit(3).forEach(s -> System.out.println("  " + s));

        assertEquals(2026, wb.year);
        assertEquals(42, wb.tableTeams.size()); // Apertura 10+10, Clausura 10+12
        assertTrue(wb.matches.stream().anyMatch(m -> m.interzonal() && m.stage() == Tournament.Stage.APERTURA));
        assertTrue(wb.matches.stream().anyMatch(m -> m.type() == MatchGame.Type.FINAL && m.homePenalties() != null));
        assertTrue(wb.matches.stream().anyMatch(m -> !m.awayPresent() || !m.homePresent()));
        assertTrue(wb.goals.size() > 100);
        assertTrue(wb.champions.stream().anyMatch(c -> c.year() == 1993 && c.category().equals("A")));
    }
}
