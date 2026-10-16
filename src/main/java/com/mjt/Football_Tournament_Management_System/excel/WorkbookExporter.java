package com.mjt.Football_Tournament_Management_System.excel;

import com.mjt.Football_Tournament_Management_System.dto.ResponseDtos;
import com.mjt.Football_Tournament_Management_System.entire.*;
import com.mjt.Football_Tournament_Management_System.repo.*;
import com.mjt.Football_Tournament_Management_System.service.ChampionService;
import com.mjt.Football_Tournament_Management_System.service.SeasonDataService;
import com.mjt.Football_Tournament_Management_System.service.StandingsService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

/**
 * Writes a season to a workbook with the league's layout (design/EXCEL_NOTES.md), so the admin can keep using the
 * spreadsheet. Round trip guarantee: importing the exported file changes nothing (see ExcelImportApiTests).
 */
@Component
@RequiredArgsConstructor
public class WorkbookExporter {
    private static final String[] WEEKDAYS = {"Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"};
    private static final String[] MONTHS = {"Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto",
            "Septiembre", "Octubre", "Noviembre", "Diciembre"};
    private static final String[] TABLE_HEADER = {"POSICIÓN", "EQUIPO", "PTOS", "PJ", "PG", "PE", "PP", "GF", "GE", "DIF", "PR"};

    private final TournamentRepository tournamentRepo;
    private final CategoryRepository categoryRepo;
    private final ZoneRepository zoneRepo;
    private final MatchRepository matchRepo;
    private final YellowCardTallyRepository yellowRepo;
    private final StandingsService standingsService;
    private final SeasonDataService seasonData;
    private final ChampionService championService;

    @Value("${app.league.title:TORNEO DE EXALUMNOS - COLEGIO MARIANISTA}")
    private String leagueTitle;

    @Transactional(readOnly = true)
    public byte[] export(Season season) {
        try (var wb = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var w = new Writer(wb, season);
            var tournaments = tournamentRepo.findBySeasonOrderByStageAscIdAsc(season);
            // Clausura first, like the league's file
            tournaments = tournaments.stream().sorted(Comparator.comparing(
                    (Tournament t) -> t.getStage() == Tournament.Stage.CLAUSURA ? 0 : 1)).toList();
            for (var t : tournaments) if (t.getStage() != null) w.table(t);
            w.fixture("Part Jug", tournaments, true);
            w.fixture("Prox Partido", tournaments, false);
            w.ranking("Goleadores", "GOLEADORES", "Goles", seasonData.scorers(season.getId()));
            w.ranking("Figuras", "FIGURAS", "Cantidad de veces figura", seasonData.figures(season.getId()));
            w.ranking("Imbatibles", "IMBATIBLES", "Goles Recibidos", seasonData.goalkeepers(season.getId()));
            w.sanctions(tournaments);
            w.history();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar el Excel", e);
        }
    }

    private class Writer {
        final XSSFWorkbook wb;
        final Season season;
        final CellStyle bold;
        final CellStyle title;

        Writer(XSSFWorkbook wb, Season season) {
            this.wb = wb;
            this.season = season;
            Font f = wb.createFont();
            f.setBold(true);
            bold = wb.createCellStyle();
            bold.setFont(f);
            Font tf = wb.createFont();
            tf.setBold(true);
            tf.setFontHeightInPoints((short) 14);
            title = wb.createCellStyle();
            title.setFont(tf);
        }

        XSSFSheet sheet(String name, String subtitle) {
            var s = wb.createSheet(name);
            set(s, 1, 0, leagueTitle + " - AÑO " + season.getYear(), title);
            set(s, 3, 0, subtitle, bold);
            return s;
        }

        // ------------------------------------------------------------ standings

        void table(Tournament t) {
            var stageName = t.getStage() == Tournament.Stage.APERTURA ? "APERTURA" : "CLAUSURA";
            var s = sheet("Tabla " + (t.getStage() == Tournament.Stage.APERTURA ? "Apertura" : "Clausura"), "TORNEO " + stageName);
            record Block(String label, List<ResponseDtos.StandingRow> rows) {}
            var blocks = new ArrayList<Block>();
            for (var c : categoryRepo.findByTournamentOrderByDisplayOrderAscIdAsc(t)) {
                var zones = zoneRepo.findByCategoryOrderByDisplayOrderAscIdAsc(c);
                for (var z : zones) {
                    var label = zones.size() == 1 && !Sheet.norm(c.getName()).equals("UNICA")
                            ? "CATEGORIA " + c.getName() : z.getName().toUpperCase(Locale.ROOT);
                    blocks.add(new Block(label, standingsService.zoneStandings(z.getId())));
                }
            }
            int top = 6;
            for (int i = 0; i < blocks.size(); i += 2) {
                int height = 0;
                for (int j = i; j < Math.min(i + 2, blocks.size()); j++) {
                    int col = (j - i) * 15;
                    var b = blocks.get(j);
                    set(s, top, col, b.label(), bold);
                    for (int h = 0; h < TABLE_HEADER.length; h++) set(s, top + 2, col + h, TABLE_HEADER[h], bold);
                    int r = top + 3;
                    for (var row : b.rows()) {
                        int[] values = {row.position(), 0, row.points(), row.played(), row.won(), row.drawn(), row.lost(),
                                row.goalsFor(), row.goalsAgainst(), row.goalDiff(), row.presencePoints()};
                        for (int v = 0; v < values.length; v++) {
                            if (v == 1) set(s, r, col + 1, row.team().name(), null);
                            else set(s, r, col + v, values[v], null);
                        }
                        r++;
                    }
                    height = Math.max(height, b.rows().size());
                }
                top += height + 6;
            }
            s.setColumnWidth(1, 24 * 256);
            s.setColumnWidth(16, 24 * 256);
        }

        // ------------------------------------------------------------ matches

        void fixture(String name, List<Tournament> tournaments, boolean played) {
            var s = sheet(name, "");
            int r = 3;
            for (var t : tournaments) {
                if (t.getStage() == null) continue;
                set(s, r, 0, "TORNEO " + t.getStage().name(), bold);
                r += 2;
                var matches = matchRepo.findByTournament(t).stream().filter(m -> m.isFinished() == played)
                        .sorted(MatchGame.BY_DATE).toList();
                // One block per round: same round number / label / day
                var blocks = new LinkedHashMap<String, List<MatchGame>>();
                for (var m : matches) blocks.computeIfAbsent(blockKey(m), k -> new ArrayList<>()).add(m);
                var ordered = new ArrayList<>(blocks.values());
                if (played) Collections.reverse(ordered); // newest first, as in the league's file
                for (var block : ordered) r = block(s, r, block);
                r += 1;
            }
            s.setColumnWidth(3, 24 * 256);
            s.setColumnWidth(6, 24 * 256);
            s.setColumnWidth(8, 18 * 256);
            s.setColumnWidth(9, 50 * 256);
        }

        String blockKey(MatchGame m) {
            var day = m.getScheduledAt() == null ? "-" : m.getScheduledAt().toLocalDate().toString();
            return (m.isSpecial() ? "S" : "R") + "|" + m.getRound() + "|" + m.getRoundLabel() + "|" + day;
        }

        int block(XSSFSheet s, int r, List<MatchGame> matches) {
            var first = matches.get(0);
            String heading;
            if (first.getRound() != null) {
                heading = "FECHA Nº " + first.getRound() + (first.getRoundLabel() == null ? "" : " " + first.getRoundLabel().toUpperCase(Locale.ROOT));
            } else if (first.getRoundLabel() != null) {
                heading = first.getRoundLabel().toUpperCase(Locale.ROOT);
            } else {
                heading = first.isSpecial() ? "FINALES Y PROMOCIONES" : "FECHA";
            }
            set(s, r, 0, heading, bold);
            if (first.getScheduledAt() != null) set(s, r, 6, dayText(first.getScheduledAt().toLocalDate()), bold);
            r += 2;
            String[] header = {"Campo", "Cancha", "Horario", "Equipo", null, "vs", "Equipo", null, "Categoría"};
            for (int c = 0; c < header.length; c++) if (header[c] != null) set(s, r, c, header[c], bold);
            r += 2;
            for (var m : matches) {
                set(s, r, 0, m.getVenue(), null);
                set(s, r, 1, m.getPitch(), null);
                if (m.getScheduledAt() != null && (m.getScheduledAt().getHour() != 0 || m.getScheduledAt().getMinute() != 0)) {
                    set(s, r, 2, m.getScheduledAt().getHour() + ":" + String.format("%02d", m.getScheduledAt().getMinute()) + " hs", null);
                }
                set(s, r, 3, side(m, true), null);
                set(s, r, 5, "vs", null);
                set(s, r, 6, side(m, false), null);
                if (m.isFinished()) {
                    set(s, r, 4, score(m.getHomeGoals(), m.getHomePenalties()), null);
                    set(s, r, 7, score(m.getAwayGoals(), m.getAwayPenalties()), null);
                }
                set(s, r, 8, label(m), null);
                var note = note(m);
                if (note != null) set(s, r, 9, "Nota: " + note, null);
                r++;
            }
            return r + 2;
        }

        String side(MatchGame m, boolean home) {
            var zone = home ? m.getHomeSourceZone() : m.getAwaySourceZone();
            if (m.isSpecial() && !m.isFinished() && zone != null) {
                var t = zone.getCategory().getTournament();
                var pos = home ? m.getHomeSourcePosition() : m.getAwaySourcePosition();
                return pos + "° " + (t.getStage() == null ? "" : t.getStage().name() + " ") + "CAT " + zone.getCategory().getName();
            }
            return (home ? m.getHomeTeam() : m.getAwayTeam()).getName();
        }

        Object score(Integer goals, Integer penalties) {
            if (penalties == null) return goals;
            return goals + " (" + penalties + ")";
        }

        String label(MatchGame m) {
            if (m.getType() == MatchGame.Type.PROMOTION) return "Promocion";
            if (m.getType() == MatchGame.Type.FINAL) return "Final";
            if (m.getZone() == null) return "Interzonal";
            var c = m.getZone().getCategory();
            if (Sheet.norm(c.getName()).equals("UNICA") || zoneRepo.countByCategory(c) > 1) return m.getZone().getName();
            return "CATEGORIA " + c.getName();
        }

        /** Keeps the note; adds the walkover sentence the importer needs when the web recorded an absence. */
        String note(MatchGame m) {
            var note = m.getNotes();
            if (!m.isFinished() || (m.present(true) && m.present(false))) return note;
            var absent = (m.present(true) ? m.getAwayTeam() : m.getHomeTeam()).getName();
            var n = Sheet.norm(note);
            if (n.contains(Sheet.norm(absent)) && (n.contains("NO SE PRESENTO") || n.contains("SE DIO DE BAJA"))) return note;
            var wo = "el equipo " + absent + " no se presento";
            return note == null ? wo : note + " - " + wo;
        }

        String dayText(LocalDate d) {
            return WEEKDAYS[d.getDayOfWeek().getValue() - 1] + " " + d.getDayOfMonth() + " de " + MONTHS[d.getMonthValue() - 1];
        }

        // ------------------------------------------------------------ rankings, sanctions, history

        void ranking(String name, String heading, String valueHeader, List<ResponseDtos.SeasonStatRow> rows) {
            var s = sheet(name, "TORNEO TABLA ANUAL");
            set(s, 5, 0, heading, bold);
            set(s, 7, 1, "Jugador", bold);
            set(s, 7, 2, "Equipo", bold);
            set(s, 7, 3, valueHeader, bold);
            int r = 8;
            for (var row : rows) {
                set(s, r, 1, row.player().fullName(), null);
                set(s, r, 2, row.team().name(), null);
                set(s, r, 3, row.value(), null);
                r++;
            }
            s.setColumnWidth(1, 30 * 256);
            s.setColumnWidth(2, 24 * 256);
        }

        void sanctions(List<Tournament> tournaments) {
            var s = sheet("Sancionados", "TORNEO TABLA ANUAL");
            set(s, 5, 0, "EXPULSADOS", bold);
            String[] header = {"Jugador", "Equipo", "Tarjeta Roja", "Fechas sanción", "Cumplida", "Pendiente", "Observaciones"};
            for (int c = 0; c < header.length; c++) set(s, 7, c + 1, header[c], bold);
            int r = 8;
            for (var x : seasonData.sanctions(season.getId())) {
                set(s, r, 1, x.player().fullName(), null);
                set(s, r, 2, x.team().name(), null);
                set(s, r, 3, x.type().equals("YELLOW_ACCUMULATION") ? "-" : (Object) 1, null);
                set(s, r, 4, x.matchesSuspended(), null);
                set(s, r, 5, x.matchesServed(), null);
                set(s, r, 6, x.status().equals("SERVED") ? "CUMPLIO" : x.status().equals("TRIBUNAL") ? "TRIBUNAL" : (Object) x.pending(), null);
                set(s, r, 7, x.notes() != null ? x.notes() : sanctionNote(x, tournaments), null);
                r++;
            }
            r += 2;
            for (var t : tournaments) {
                if (t.getStage() == null) continue;
                set(s, r, 0, "AMONESTADOS Torneo " + t.getStage().name(), bold);
                set(s, r, 7, "Con " + YellowCardTally.YELLOWS_PER_SUSPENSION + " amarillas acumuladas - 1 fecha de suspensión", null);
                r += 2;
                set(s, r, 1, "Jugador", bold);
                set(s, r, 2, "Equipo", bold);
                set(s, r, 3, "Amarillas", bold);
                r++;
                for (var y : yellowRepo.findByTournament(t).stream()
                        .sorted(Comparator.comparing((YellowCardTally y) -> y.getTeam().getName()).thenComparing(y -> y.getPlayer().getFullName())).toList()) {
                    set(s, r, 1, y.getPlayer().getFullName(), null);
                    set(s, r, 2, y.getTeam().getName(), null);
                    set(s, r, 3, y.getYellowCards(), null);
                    r++;
                }
                r += 2;
            }
            s.setColumnWidth(1, 30 * 256);
            s.setColumnWidth(2, 24 * 256);
            s.setColumnWidth(7, 50 * 256);
        }

        /** Notes for sanctions created on the web: the type and tournament code the importer reads back. */
        String sanctionNote(ResponseDtos.SanctionRes x, List<Tournament> tournaments) {
            var what = switch (x.type()) {
                case "DIRECT_RED" -> "Expulsión por Roja Directa";
                case "DOUBLE_YELLOW" -> "Expulsión por Doble Amarilla";
                case "YELLOW_ACCUMULATION" -> "Acumulacion de amarillas";
                default -> "Sanción";
            };
            var stage = tournaments.stream().filter(t -> t.getId().equals(x.tournamentId())).findFirst()
                    .map(Tournament::getStage).orElse(null);
            return stage == null ? what : what + " " + (stage == Tournament.Stage.APERTURA ? "TA " : "TC ") + season.getYear();
        }

        void history() {
            var s = wb.createSheet("HISTORIA");
            int col = 1;
            for (var category : championService.history()) {
                set(s, 1, col, "LISTADO DE CAMPEONES", bold);
                set(s, 2, col, "Categoria \"" + category.categoryName() + "\"", bold);
                set(s, 6, col, "Año", bold);
                set(s, 6, col + 1, "Equipo Campeon", bold);
                int r = 7;
                for (var c : category.champions()) {
                    set(s, r, col, c.year(), null);
                    set(s, r, col + 1, c.team().name(), null);
                    r++;
                }
                set(s, 1, col + 4, "Maximos CAMPEONES", bold);
                set(s, 6, col + 4, "Torneos", bold);
                set(s, 6, col + 5, "Equipo", bold);
                r = 7;
                for (var t : category.topChampions()) {
                    set(s, r, col + 4, t.titles(), null);
                    set(s, r, col + 5, t.team().name(), null);
                    r++;
                }
                s.setColumnWidth(col + 1, 26 * 256);
                s.setColumnWidth(col + 5, 26 * 256);
                col += 8;
            }
        }

        void set(XSSFSheet s, int r, int c, Object value, CellStyle style) {
            if (value == null) return;
            var row = s.getRow(r) == null ? s.createRow(r) : s.getRow(r);
            var cell = row.createCell(c);
            if (value instanceof Number n) cell.setCellValue(n.doubleValue());
            else cell.setCellValue(value.toString());
            if (style != null) cell.setCellStyle(style);
        }
    }
}
