package com.mjt.Football_Tournament_Management_System.excel;

import com.mjt.Football_Tournament_Management_System.entire.MatchGame;
import com.mjt.Football_Tournament_Management_System.entire.Sanction;
import com.mjt.Football_Tournament_Management_System.entire.Tournament;
import com.mjt.Football_Tournament_Management_System.excel.ParsedWorkbook.*;
import com.mjt.Football_Tournament_Management_System.exception.BadRequestException;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.IOException;
import java.io.InputStream;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the league's workbook (format described in design/EXCEL_NOTES.md). Tolerant on purpose: sheet names,
 * headers and team names are compared normalized, columns are found by header text, and anything it cannot
 * read is reported as a notice instead of guessed.
 */
public class WorkbookParser {
    private static final Pattern YEAR = Pattern.compile("ANO (\\d{4})");
    private static final Pattern GROUP = Pattern.compile("^(?:CATEGORIA ([A-Z])|ZONA (\\d+))$");
    private static final Pattern ROUND = Pattern.compile("^FECHA (?:N )?(\\d+)\\s*(.*)$");
    private static final Pattern DATE = Pattern.compile("(\\d{1,2}) DE ([A-Z]+)");
    private static final Pattern TIME = Pattern.compile("(\\d{1,2})[:.](\\d{2})");
    private static final Pattern GOALS = Pattern.compile("^(\\d+)\\s*(?:\\((\\d+)\\))?");
    private static final Pattern PLACEHOLDER = Pattern.compile("^(\\d+)\\s*(?:O\\s+)?(APERTURA|CLAUSURA)?\\s*CAT(?:EGORIA)?\\s*([A-Z])$");
    private static final Pattern STAGE_CODE = Pattern.compile("\\b(TA|TC)\\b");
    private static final Set<String> WEEKDAYS = Set.of("LUNES", "MARTES", "MIERCOLES", "JUEVES", "VIERNES", "SABADO", "DOMINGO");
    private static final Set<String> NO_CHAMPION = Set.of("PROXIMAMENTE!!", "PROXIMAMENTE", "S.N.", "S/N", "SN", "NN");
    private static final Map<String, Integer> MONTHS = Map.ofEntries(
            Map.entry("ENERO", 1), Map.entry("FEBRERO", 2), Map.entry("MARZO", 3), Map.entry("ABRIL", 4),
            Map.entry("MAYO", 5), Map.entry("JUNIO", 6), Map.entry("JULIO", 7), Map.entry("AGOSTO", 8),
            Map.entry("SEPTIEMBRE", 9), Map.entry("SETIEMBRE", 9), Map.entry("OCTUBRE", 10),
            Map.entry("NOVIEMBRE", 11), Map.entry("DICIEMBRE", 12));

    public ParsedWorkbook parse(InputStream in) {
        var sheets = new ArrayList<Sheet>();
        try (var wb = WorkbookFactory.create(in)) {
            wb.forEach(s -> sheets.add(new Sheet(s)));
        } catch (IOException | RuntimeException e) {
            throw new BadRequestException("No se pudo leer el archivo: tiene que ser un Excel .xlsx válido");
        }
        var out = new ParsedWorkbook();
        out.year = findYear(sheets);
        for (var sheet : sheets) {
            var name = Sheet.norm(sheet.name);
            if (name.startsWith("TABLA")) readTable(sheet, out);
            else if (name.startsWith("PART JUG") || name.startsWith("PROX")) readFixture(sheet, out);
            else if (name.startsWith("GOLEADOR")) readPlayerValues(sheet, "GOLES", out.goals, out);
            else if (name.startsWith("FIGURA")) readPlayerValues(sheet, "FIGURA", out.figures, out);
            else if (name.startsWith("IMBATIBLE")) readPlayerValues(sheet, "GOLES RECIBIDOS", out.goalsConceded, out);
            else if (name.startsWith("SANCIONADO")) readSanctions(sheet, out);
            else if (name.startsWith("HISTORIA")) readChampions(sheet, out);
            else out.notices.add("Hoja \"" + sheet.name + "\" ignorada (no se reconoce su contenido)");
        }
        return out;
    }

    private int findYear(List<Sheet> sheets) {
        for (var sheet : sheets) {
            for (int r = 0; r < Math.min(6, sheet.size()); r++) {
                var m = YEAR.matcher(sheet.rowText(r));
                if (m.find()) return Integer.parseInt(m.group(1));
            }
        }
        throw new BadRequestException("No se encontró el año de la planilla (un título con \"AÑO 2026\")");
    }

    // ------------------------------------------------------------------ standings tables

    private void readTable(Sheet sheet, ParsedWorkbook out) {
        var stage = stageOf(Sheet.norm(sheet.name));
        if (stage == null) stage = stageFromRows(sheet);
        if (stage == null) {
            out.notices.add("Hoja \"" + sheet.name + "\": no se sabe si es Apertura o Clausura, se ignora");
            return;
        }
        record Label(int row, int col, Group group) {}
        var labels = new ArrayList<Label>();
        for (int r = 0; r < sheet.size(); r++) {
            for (int c = 0; c < sheet.width(r); c++) {
                var g = group(sheet.norm(r, c));
                if (g != null) labels.add(new Label(r, c, g));
            }
        }
        for (var label : labels) {
            int colEnd = labels.stream().filter(l -> l.row() == label.row() && l.col() > label.col())
                    .mapToInt(Label::col).min().orElse(Integer.MAX_VALUE);
            int header = -1;
            for (int h = label.row() + 1; h <= label.row() + 4 && header < 0; h++) {
                if (find(sheet, h, label.col(), colEnd, "EQUIPO") >= 0) header = h;
            }
            if (header < 0) continue;
            int teamCol = find(sheet, header, label.col(), colEnd, "EQUIPO");
            int posCol = find(sheet, header, label.col(), colEnd, "POSICION");
            int prCol = find(sheet, header, label.col(), colEnd, "PR");
            int pjCol = find(sheet, header, label.col(), colEnd, "PJ");
            int ptsCol = find(sheet, header, label.col(), colEnd, "PTOS");
            for (int r = header + 1; r < sheet.size(); r++) {
                var team = sheet.text(r, teamCol);
                if (team == null || (posCol >= 0 && sheet.integer(r, posCol) == null)) break;
                out.tableTeams.add(new TableTeam(stage, label.group(), team.trim(),
                        ptsCol >= 0 ? sheet.integer(r, ptsCol) : null, prCol >= 0 ? sheet.integer(r, prCol) : null, pjCol >= 0 ? sheet.integer(r, pjCol) : null,
                        where(sheet, r)));
            }
        }
    }

    // ------------------------------------------------------------------ played / upcoming matches

    private void readFixture(Sheet sheet, ParsedWorkbook out) {
        Tournament.Stage stage = null;
        Integer round = null;
        String roundLabel = null;
        LocalDate date = null;
        int vs = -1, cat = -1, pitch = -1, time = -1, venue = -1;
        for (int r = 0; r < sheet.size(); r++) {
            var text = sheet.rowText(r);
            if (text.equals("TORNEO APERTURA") || text.equals("TORNEO CLAUSURA")) {
                stage = stageOf(text);
                continue;
            }
            int vsCol = indexOf(sheet, r, "VS");
            if (vsCol < 0) {
                if (text.matches(".*\\b(FECHA|PROMOCION|PROMOCIONES|FINAL|FINALES|INTERZONAL)\\b.*")) {
                    var header = roundHeader(sheet, r, out.year);
                    round = header.round;
                    roundLabel = header.label;
                    date = header.date;
                }
                continue;
            }
            if (indexOf(sheet, r, "EQUIPO") >= 0) { // column header of a round block
                vs = vsCol;
                cat = indexOf(sheet, r, "CATEGORIA");
                pitch = indexOf(sheet, r, "CANCHA");
                time = indexOf(sheet, r, "HORARIO");
                int campo = indexOf(sheet, r, "CAMPO");
                venue = campo >= 0 ? campo : pitch - 1;
                continue;
            }
            if (vs < 0) continue;
            if (stage == null) {
                out.notices.add(where(sheet, r) + ": partido antes del título del torneo (Apertura/Clausura), se ignora");
                continue;
            }
            var homeText = sheet.text(r, vs - 2);
            var awayText = sheet.text(r, vs + 1);
            if (homeText == null || awayText == null) continue;
            readMatch(sheet, r, stage, round, roundLabel, date, vs, cat, pitch, time, venue, homeText, awayText, out);
        }
    }

    private record RoundHeader(Integer round, String label, LocalDate date) {}

    private RoundHeader roundHeader(Sheet sheet, int r, int year) {
        Integer round = null;
        String label = null;
        LocalDate date = null;
        for (int c = 0; c < sheet.width(r); c++) {
            var n = sheet.norm(r, c);
            if (n.isEmpty()) continue;
            var dm = DATE.matcher(n);
            if (dm.find() && date == null) {
                date = toDate(year, dm, sheet, r);
                var first = n.split(" ")[0];
                if (WEEKDAYS.contains(first) || n.startsWith(dm.group())) continue;
            }
            if (label == null && round == null) {
                if (n.equals("FECHA")) continue; // block of matches without a round number
                var m = ROUND.matcher(n);
                if (m.matches()) {
                    round = Integer.parseInt(m.group(1));
                    label = m.group(2).isBlank() ? null : pretty(m.group(2));
                } else {
                    label = pretty(n);
                }
            }
        }
        return new RoundHeader(round, label, date);
    }

    private void readMatch(Sheet sheet, int r, Tournament.Stage stage, Integer round, String roundLabel,
                           LocalDate date, int vs, int cat, int pitchCol, int timeCol, int venueCol,
                           String homeText, String awayText, ParsedWorkbook out) {
        var labelText = cat >= 0 ? sheet.text(r, cat) : null;
        var label = Sheet.norm(labelText);
        MatchGame.Type type = label.contains("PROMOCION") ? MatchGame.Type.PROMOTION
                : label.contains("FINAL") ? MatchGame.Type.FINAL : MatchGame.Type.REGULAR;
        Group group = null;
        var gm = Pattern.compile("(?:CATEGORIA ([A-Z])\\b|ZONA (\\d+))").matcher(label);
        if (gm.find()) group = gm.group(1) != null ? new Group(Group.Kind.CATEGORY, gm.group(1)) : new Group(Group.Kind.ZONE, gm.group(2));
        boolean interzonal = label.contains("INTERZONAL") && type == MatchGame.Type.REGULAR;

        var home = side(homeText, stage);
        var away = side(awayText, stage);
        if (type == MatchGame.Type.REGULAR && (home.isPlaceholder() || away.isPlaceholder())) {
            out.notices.add(where(sheet, r) + ": " + home.label() + " vs " + away.label()
                    + " — rival por posición todavía sin definir, se omite");
            return;
        }
        var hg = goals(sheet.get(r, vs - 1));
        var ag = goals(sheet.get(r, vs + 2));
        var notes = cat >= 0 ? sheet.text(r, cat + 1) : null;
        if (notes != null) notes = notes.replaceFirst("(?i)^nota:\\s*", "").trim();

        // Walkovers: the note names the team that did not show up or left the tournament
        boolean homePresent = true, awayPresent = true;
        var noteNorm = Sheet.norm(notes);
        if (hg != null && (noteNorm.contains("NO SE PRESENTO") || noteNorm.contains("SE DIO DE BAJA"))) {
            if (!home.isPlaceholder() && noteNorm.contains(home.key())) homePresent = false;
            else if (!away.isPlaceholder() && noteNorm.contains(away.key())) awayPresent = false;
            else out.notices.add(where(sheet, r) + ": la nota habla de un equipo ausente pero no se reconoce cuál");
        }

        LocalTime time = null;
        var t = timeCol >= 0 ? sheet.text(r, timeCol) : null;
        if (t != null) {
            var tm = TIME.matcher(t);
            if (tm.find()) time = LocalTime.of(Integer.parseInt(tm.group(1)), Integer.parseInt(tm.group(2)));
        }
        out.matches.add(new ParsedMatch(stage, type, group, interzonal, labelText, round, roundLabel, date, time,
                venueCol >= 0 ? sheet.text(r, venueCol) : null, pitchCol >= 0 ? sheet.text(r, pitchCol) : null,
                home, away, hg == null ? null : hg[0], ag == null ? null : ag[0],
                hg == null ? null : hg[1], ag == null ? null : ag[1], homePresent, awayPresent, notes,
                where(sheet, r)));
    }

    private Side side(String text, Tournament.Stage current) {
        var m = PLACEHOLDER.matcher(Sheet.norm(text));
        if (m.matches()) {
            var stage = m.group(2) != null ? Tournament.Stage.valueOf(m.group(2)) : current;
            return new Side(null, Integer.parseInt(m.group(1)), stage, m.group(3));
        }
        return new Side(text.trim(), null, null, null);
    }

    /** [goals, penalties] from 3, "3", "0 (4)"; null when empty. */
    private static Integer[] goals(Object cell) {
        if (cell instanceof Double d) return new Integer[]{(int) Math.round(d), null};
        if (cell instanceof String s) {
            var m = GOALS.matcher(s.trim());
            if (m.find()) return new Integer[]{Integer.parseInt(m.group(1)), m.group(2) == null ? null : Integer.parseInt(m.group(2))};
        }
        return null;
    }

    // ------------------------------------------------------------------ player totals

    private void readPlayerValues(Sheet sheet, String valueHeader, List<PlayerValue> target, ParsedWorkbook out) {
        for (int h = 0; h < sheet.size(); h++) {
            int playerCol = indexOf(sheet, h, "JUGADOR");
            int teamCol = indexOf(sheet, h, "EQUIPO");
            if (playerCol < 0 || teamCol < 0) continue;
            int valueCol = -1;
            for (int c = 0; c < sheet.width(h); c++) {
                if (sheet.norm(h, c).contains(valueHeader)) { valueCol = c; break; }
            }
            if (valueCol < 0) return;
            for (int r = h + 1; r < sheet.size(); r++) {
                var player = sheet.text(r, playerCol);
                var team = sheet.text(r, teamCol);
                var value = sheet.integer(r, valueCol);
                if (player == null || team == null || value == null) continue; // blank, "control" rows, #REF!
                target.add(new PlayerValue(player.trim(), team.trim(), value, where(sheet, r)));
            }
            return;
        }
        out.notices.add("Hoja \"" + sheet.name + "\": no se encontró el encabezado Jugador / Equipo");
    }

    // ------------------------------------------------------------------ sanctions and yellow cards

    private void readSanctions(Sheet sheet, ParsedWorkbook out) {
        for (int r = 0; r < sheet.size(); r++) {
            var text = sheet.rowText(r);
            if (text.startsWith("EXPULSADOS")) r = readExpelled(sheet, r + 1, out) - 1;
            else if (text.startsWith("AMONESTADOS")) r = readYellows(sheet, r + 1, stageOf(text), out) - 1;
        }
    }

    /** Reads the "Expulsados" block from `start`; returns the row where it ended. */
    private int readExpelled(Sheet sheet, int start, ParsedWorkbook out) {
        int h = start;
        while (h < sheet.size() && indexOf(sheet, h, "JUGADOR") < 0) h++;
        int player = indexOf(sheet, h, "JUGADOR"), team = indexOf(sheet, h, "EQUIPO");
        int red = indexOfContaining(sheet, h, "ROJA"), suspended = indexOfContaining(sheet, h, "FECHAS");
        int served = indexOfContaining(sheet, h, "CUMPLIDA"), pending = indexOfContaining(sheet, h, "PENDIENTE");
        int notesCol = indexOfContaining(sheet, h, "OBSERVACION");
        int r = h + 1;
        for (; r < sheet.size(); r++) {
            if (sheet.rowText(r).startsWith("AMONESTADOS")) break;
            var p = sheet.text(r, player);
            var t = sheet.text(r, team);
            if (p == null || t == null || Sheet.norm(t).equals("CONTROL")) continue;
            var notes = notesCol >= 0 ? sheet.text(r, notesCol) : null;
            var n = Sheet.norm(notes);
            var type = n.contains("ROJA DIRECTA") ? Sanction.Type.DIRECT_RED
                    : n.contains("DOBLE AMARILLA") ? Sanction.Type.DOUBLE_YELLOW
                    : n.contains("ACUMULACION") || "-".equals(sheet.text(r, red)) ? Sanction.Type.YELLOW_ACCUMULATION
                    : Sanction.Type.OTHER;
            int susp = Optional.ofNullable(sheet.integer(r, suspended)).orElse(0);
            int done = Optional.ofNullable(sheet.integer(r, served)).orElse(0);
            var pend = sheet.norm(r, pending);
            var status = pend.contains("TRIBUNAL") ? Sanction.Status.TRIBUNAL
                    : pend.contains("CUMPLI") || done >= susp ? Sanction.Status.SERVED : Sanction.Status.PENDING;
            var sm = STAGE_CODE.matcher(n);
            var stage = sm.find() ? (sm.group(1).equals("TA") ? Tournament.Stage.APERTURA : Tournament.Stage.CLAUSURA) : null;
            out.sanctions.add(new ParsedSanction(p.trim(), t.trim(), type, stage, susp, done, status, notes, where(sheet, r)));
        }
        return r;
    }

    private int readYellows(Sheet sheet, int start, Tournament.Stage stage, ParsedWorkbook out) {
        int h = start;
        while (h < sheet.size() && indexOf(sheet, h, "JUGADOR") < 0) h++;
        int player = indexOf(sheet, h, "JUGADOR"), team = indexOf(sheet, h, "EQUIPO");
        int count = indexOfContaining(sheet, h, "AMARILLA");
        int r = h + 1;
        for (; r < sheet.size(); r++) {
            var text = sheet.rowText(r);
            if (text.startsWith("AMONESTADOS") || text.startsWith("EXPULSADOS")) break;
            var p = sheet.text(r, player);
            var t = sheet.text(r, team);
            var n = sheet.integer(r, count);
            if (p == null || t == null || n == null || n <= 0) continue;
            if (stage == null) {
                out.notices.add(where(sheet, r) + ": amarillas sin torneo (falta \"AMONESTADOS Torneo ...\"), se ignoran");
                continue;
            }
            out.yellows.add(new ParsedYellows(stage, p.trim(), t.trim(), n, where(sheet, r)));
        }
        return r;
    }

    // ------------------------------------------------------------------ champions history

    private void readChampions(Sheet sheet, ParsedWorkbook out) {
        for (int h = 0; h < sheet.size(); h++) {
            for (int c = 0; c < sheet.width(h); c++) {
                if (!sheet.norm(h, c).equals("ANO")) continue;
                String category = null;
                for (int up = h - 1; up >= 0 && category == null; up--) {
                    var g = group(sheet.norm(up, c));
                    if (g != null && g.kind() == Group.Kind.CATEGORY) category = g.name();
                }
                if (category == null) continue;
                for (int r = h + 1; r < sheet.size(); r++) {
                    var year = sheet.integer(r, c);
                    if (year == null || year < 1900 || year > 2100) continue;
                    var team = sheet.text(r, c + 1);
                    if (team == null || NO_CHAMPION.contains(Sheet.norm(team))) {
                        out.notices.add(where(sheet, r) + ": " + year + " categoría " + category + " sin campeón ("
                                + (team == null ? "vacío" : team) + "), se omite");
                        continue;
                    }
                    out.champions.add(new ParsedChampion(year, category, team.trim(), where(sheet, r)));
                }
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private static Group group(String norm) {
        var m = GROUP.matcher(norm.replace("\"", "").replace("'", "").replaceAll("\\s+", " ").trim());
        if (!m.matches()) return null;
        return m.group(1) != null ? new Group(Group.Kind.CATEGORY, m.group(1)) : new Group(Group.Kind.ZONE, m.group(2));
    }

    private static Tournament.Stage stageOf(String norm) {
        if (norm.contains("APERTURA")) return Tournament.Stage.APERTURA;
        if (norm.contains("CLAUSURA")) return Tournament.Stage.CLAUSURA;
        return null;
    }

    private static Tournament.Stage stageFromRows(Sheet sheet) {
        for (int r = 0; r < Math.min(8, sheet.size()); r++) {
            var t = sheet.rowText(r);
            if (t.startsWith("TORNEO APERTURA") || t.startsWith("TORNEO CLAUSURA")) return stageOf(t);
        }
        return null;
    }

    private static int indexOf(Sheet sheet, int row, String norm) {
        for (int c = 0; c < sheet.width(row); c++) if (sheet.norm(row, c).equals(norm)) return c;
        return -1;
    }

    private static int indexOfContaining(Sheet sheet, int row, String part) {
        for (int c = 0; c < sheet.width(row); c++) if (sheet.norm(row, c).contains(part)) return c;
        return -1;
    }

    private static int find(Sheet sheet, int row, int from, int to, String norm) {
        for (int c = from; c < Math.min(to, sheet.width(row)); c++) if (sheet.norm(row, c).equals(norm)) return c;
        return -1;
    }

    private static LocalDate toDate(int year, Matcher m, Sheet sheet, int r) {
        var month = MONTHS.get(m.group(2));
        if (month == null) return null;
        try {
            return LocalDate.of(year, month, Integer.parseInt(m.group(1)));
        } catch (DateTimeException e) {
            return null;
        }
    }

    /** "REPETICION EN ZONAS" -> "Repeticion en zonas". */
    private static String pretty(String norm) {
        var s = norm.toLowerCase(Locale.ROOT).trim();
        return s.isEmpty() ? null : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String where(Sheet sheet, int r) {
        return sheet.name.trim() + " fila " + Sheet.excelRow(r);
    }
}
