package com.mjt.Football_Tournament_Management_System.excel;

import com.mjt.Football_Tournament_Management_System.entire.*;
import com.mjt.Football_Tournament_Management_System.excel.ParsedWorkbook.*;
import com.mjt.Football_Tournament_Management_System.repo.*;
import com.mjt.Football_Tournament_Management_System.service.StandingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Writes a parsed workbook into the database. Runs inside a transaction opened by the caller: for a preview the
 * caller rolls it back, so preview and apply share exactly this code.
 *
 * Rules (docs/excel-y-temporadas.md): the standings tables decide where each team plays; records edited on the web
 * (source WEB) that differ from the Excel are conflicts and are left alone unless their key is in `overwrite`;
 * nothing is deleted.
 */
@Component
@RequiredArgsConstructor
public class ExcelImporter {
    private static final String TEAMS = "Equipos", PLAYERS = "Jugadores", TOURNAMENTS = "Campeonatos",
            GROUPS = "Categorías y zonas", REGISTRATIONS = "Inscripciones", MATCHES = "Partidos",
            STATS = "Estadísticas anuales", SANCTIONS = "Sanciones", YELLOWS = "Amarillas acumuladas",
            CHAMPIONS = "Campeones";

    private final SeasonRepository seasonRepo;
    private final TournamentRepository tournamentRepo;
    private final CategoryRepository categoryRepo;
    private final ZoneRepository zoneRepo;
    private final TeamRepository teamRepo;
    private final PlayerRepository playerRepo;
    private final RegistrationRepository regRepo;
    private final MatchRepository matchRepo;
    private final MatchEventRepository eventRepo;
    private final PlayerSeasonStatRepository statRepo;
    private final SanctionRepository sanctionRepo;
    private final YellowCardTallyRepository yellowRepo;
    private final ChampionRepository championRepo;
    private final StandingsService standingsService;

    private record TablePoints(int points, int presence) {}

    public void run(ParsedWorkbook wb, Set<String> overwrite, ImportReport report) {
        new Run(wb, overwrite, report).go();
    }

    /** State of one import run (caches of teams, players, zones). */
    private class Run {
        final ParsedWorkbook wb;
        final Set<String> overwrite;
        final ImportReport report;
        final Map<String, Team> teams = new HashMap<>();
        final Map<Long, Map<String, Player>> playersByTeam = new HashMap<>();
        final Map<Tournament.Stage, Tournament> tournaments = new EnumMap<>(Tournament.Stage.class);
        /** stage -> normalized team name -> zone (from the standings tables). */
        final Map<Tournament.Stage, Map<String, Zone>> teamZone = new EnumMap<>(Tournament.Stage.class);
        Season season;

        Run(ParsedWorkbook wb, Set<String> overwrite, ImportReport report) {
            this.wb = wb;
            this.overwrite = overwrite;
            this.report = report;
        }

        void go() {
            for (var t : teamRepo.findAll()) teams.put(Sheet.norm(t.getName()), t);
            season = seasonRepo.findByYear(wb.year).orElseGet(() -> {
                report.created(TOURNAMENTS, "Temporada " + wb.year);
                return seasonRepo.save(new Season(null, wb.year));
            });
            importTables();
            importMatches();
            checkPresencePoints();
            checkTablePoints();
            importPlayerStats();
            importSanctions();
            importYellows();
            importChampions();
            report.notices.addAll(wb.notices);
        }

        // ---------------------------------------------------------------- tournaments, groups, registrations

        Tournament tournament(Tournament.Stage stage) {
            return tournaments.computeIfAbsent(stage, s -> tournamentRepo.findBySeasonAndStage(season, s).orElseGet(() -> {
                var t = new Tournament();
                t.setName((s == Tournament.Stage.APERTURA ? "Apertura " : "Clausura ") + wb.year);
                t.setSeason(season);
                t.setStage(s);
                t.setStatus(Tournament.Status.LIVE);
                report.created(TOURNAMENTS, t.getName());
                return tournamentRepo.save(t);
            }));
        }

        Zone zone(Tournament t, Group group) {
            var categories = categoryRepo.findByTournamentOrderByDisplayOrderAscIdAsc(t);
            if (group.kind() == Group.Kind.CATEGORY) {
                var category = categories.stream().filter(c -> Sheet.norm(c.getName()).equals(group.name())).findFirst()
                        .orElseGet(() -> newCategory(t, group.name(), categories.size()));
                var zones = zoneRepo.findByCategoryOrderByDisplayOrderAscIdAsc(category);
                if (zones.size() > 1) report.warnings.add(t.getName() + ": la categoría " + category.getName()
                        + " tiene varias zonas en la web; los equipos del Excel van a la primera");
                return zones.get(0);
            }
            var wanted = "ZONA " + group.name();
            for (var c : categories) {
                for (var z : zoneRepo.findByCategoryOrderByDisplayOrderAscIdAsc(c)) {
                    if (Sheet.norm(z.getName()).equals(wanted)) return z;
                }
            }
            var category = categories.stream().filter(c -> Sheet.norm(c.getName()).equals("UNICA")).findFirst()
                    .orElseGet(() -> categories.isEmpty() ? newCategory(t, "Única", 0) : categories.get(0));
            var zone = zoneRepo.save(new Zone(null, category, "Zona " + group.name(), zoneRepo.countByCategory(category)));
            report.created(GROUPS, t.getName() + " · " + category.getName() + " · " + zone.getName());
            return zone;
        }

        Category newCategory(Tournament t, String name, int order) {
            var c = categoryRepo.save(new Category(null, t, name, order));
            if (!name.equals("Única")) zoneRepo.save(new Zone(null, c, "Zona 1", 0));
            report.created(GROUPS, t.getName() + " · Categoría " + name);
            return c;
        }

        void importTables() {
            var zonesByGroup = new HashMap<String, Zone>();
            for (var row : wb.tableTeams) {
                var t = tournament(row.stage());
                var zone = zonesByGroup.computeIfAbsent(row.stage() + "|" + row.group(), k -> zone(t, row.group()));
                var team = team(row.team());
                teamZone.computeIfAbsent(row.stage(), s -> new HashMap<>()).put(Sheet.norm(team.getName()), zone);

                var reg = regRepo.findByTournament(t).stream()
                        .filter(r -> r.getTeam().getId().equals(team.getId())).findFirst().orElse(null);
                var label = t.getName() + " · " + team.getName() + " → " + row.group().label();
                if (reg == null) {
                    var r = new Registration();
                    r.setTournament(t); r.setTeam(team); r.setZone(zone); r.setApproved(true);
                    regRepo.save(r);
                    report.created(REGISTRATIONS, label);
                } else if (reg.getZone() == null || !reg.getZone().getId().equals(zone.getId())) {
                    if (reg.getZone() != null && matchRepo.existsByZoneAndTeam(reg.getZone(), team)) {
                        report.warnings.add(label + ": en la web juega en otra zona y ya tiene partidos ahí; no se mueve");
                        teamZone.get(row.stage()).put(Sheet.norm(team.getName()), reg.getZone());
                    } else {
                        reg.setZone(zone);
                        report.updated(REGISTRATIONS, label);
                    }
                } else {
                    report.unchanged(REGISTRATIONS);
                }
            }
        }

        // ---------------------------------------------------------------- matches

        void importMatches() {
            var existingByTournament = new HashMap<Long, List<MatchGame>>();
            for (var pm : wb.matches) {
                var t = tournament(pm.stage());
                var existing = existingByTournament.computeIfAbsent(t.getId(), id -> new ArrayList<>(matchRepo.findByTournament(t)));
                var draft = pm.type() == MatchGame.Type.REGULAR ? regular(pm, t) : special(pm, t);
                if (draft == null) continue;
                var match = existing.stream().filter(m -> sameMatch(m, draft)).findFirst().orElse(null);
                if (match == null) {
                    draft.setSource(DataSource.EXCEL);
                    existing.add(matchRepo.save(draft));
                    report.created(MATCHES, describe(t, draft));
                    continue;
                }
                var diff = matchDiff(match, draft);
                if (diff.isEmpty()) {
                    report.unchanged(MATCHES);
                    continue;
                }
                var key = "match:" + match.getId();
                boolean webEdited = match.getSource() == DataSource.WEB || !eventRepo.findByMatchOrderByIdAsc(match).isEmpty();
                if (webEdited) {
                    boolean force = overwrite.contains(key);
                    report.conflict(key, MATCHES, describe(t, match) + " (" + pm.where() + ")", resultText(draft),
                            resultText(match), force);
                    if (!force) continue;
                    eventRepo.deleteByMatch(match); // the Excel result replaces the per-player detail
                }
                copyMatch(draft, match);
                match.setSource(DataSource.EXCEL);
                report.updated(MATCHES, describe(t, match) + ": " + String.join(", ", diff));
            }
        }

        MatchGame regular(ParsedMatch pm, Tournament t) {
            var zones = teamZone.getOrDefault(pm.stage(), Map.of());
            var home = team(pm.home().team());
            var away = team(pm.away().team());
            var hz = zones.get(Sheet.norm(home.getName()));
            var az = zones.get(Sheet.norm(away.getName()));
            if (hz == null || az == null) {
                report.warnings.add(pm.where() + ": " + home.getName() + " vs " + away.getName() + " — "
                        + (hz == null ? home.getName() : away.getName()) + " no figura en la tabla del "
                        + t.getName() + "; se omite el partido");
                return null;
            }
            Zone zone = null;
            if (hz.getId().equals(az.getId())) {
                zone = hz;
                if (pm.interzonal()) {
                    report.warnings.add(pm.where() + ": dice Interzonal pero los dos juegan en " + groupName(hz));
                } else if (pm.group() != null && !matchesGroup(hz, pm.group())) {
                    report.warnings.add(pm.where() + ": " + home.getName() + " vs " + away.getName() + " dice "
                            + pm.labelText() + " pero los dos juegan en " + groupName(hz) + "; se carga en " + groupName(hz));
                }
            } else if (!pm.interzonal()) {
                report.warnings.add(pm.where() + ": " + home.getName() + " (" + groupName(hz) + ") vs " + away.getName()
                        + " (" + groupName(az) + ") dice " + pm.labelText() + "; se carga como interzonal");
            }
            var m = baseMatch(pm, t);
            m.setType(MatchGame.Type.REGULAR);
            m.setZone(zone);
            m.setHomeTeam(home);
            m.setAwayTeam(away);
            return m;
        }

        MatchGame special(ParsedMatch pm, Tournament t) {
            var m = baseMatch(pm, t);
            m.setType(pm.type());
            if (!side(m, true, pm.home(), pm) || !side(m, false, pm.away(), pm)) return null;
            if (m.getHomeTeam().getId().equals(m.getAwayTeam().getId())) {
                report.warnings.add(pm.where() + ": los dos lados de la definición son el mismo equipo; se omite");
                return null;
            }
            return m;
        }

        /** Fixed team, or "position N of category X" resolved against the current table of that zone. */
        boolean side(MatchGame m, boolean home, Side side, ParsedMatch pm) {
            if (!side.isPlaceholder()) {
                var team = team(side.team());
                if (home) m.setHomeTeam(team); else m.setAwayTeam(team);
                return true;
            }
            var zone = zone(tournament(side.stage()), new Group(Group.Kind.CATEGORY, side.category()));
            var table = standingsService.zoneStandings(zone.getId());
            if (side.position() > table.size()) {
                report.warnings.add(pm.where() + ": " + side.label() + " — la categoría tiene " + table.size()
                        + " equipos; se omite la definición");
                return false;
            }
            var team = teamRepo.findById(table.get(side.position() - 1).team().id()).orElseThrow();
            if (home) {
                m.setHomeTeam(team); m.setHomeSourceZone(zone); m.setHomeSourcePosition(side.position());
            } else {
                m.setAwayTeam(team); m.setAwaySourceZone(zone); m.setAwaySourcePosition(side.position());
            }
            return true;
        }

        MatchGame baseMatch(ParsedMatch pm, Tournament t) {
            var m = new MatchGame();
            m.setTournament(t);
            m.setRound(pm.round());
            m.setRoundLabel(pm.roundLabel());
            m.setScheduledAt(pm.date() == null ? null : pm.time() == null ? pm.date().atStartOfDay() : pm.date().atTime(pm.time()));
            m.setVenue(pm.venue());
            m.setPitch(pm.pitch());
            m.setNotes(pm.notes());
            m.setHomePresent(pm.homePresent());
            m.setAwayPresent(pm.awayPresent());
            if (pm.played()) {
                m.setStatus(MatchGame.Status.FINISHED);
                m.setHomeGoals(pm.homeGoals());
                m.setAwayGoals(pm.awayGoals());
                if (pm.homePenalties() != null && pm.awayPenalties() != null) {
                    m.setHomePenalties(pm.homePenalties());
                    m.setAwayPenalties(pm.awayPenalties());
                }
            } else {
                m.setStatus(MatchGame.Status.SCHEDULED);
                m.setHomeGoals(0);
                m.setAwayGoals(0);
            }
            return m;
        }

        /** Same tournament, type, teams and round (or same position rules for definitions). */
        boolean sameMatch(MatchGame a, MatchGame b) {
            if (a.typeOrRegular() != b.typeOrRegular()) return false;
            if (a.isSpecial()) return slotKey(a, true).equals(slotKey(b, true)) && slotKey(a, false).equals(slotKey(b, false));
            return a.getHomeTeam().getId().equals(b.getHomeTeam().getId())
                    && a.getAwayTeam().getId().equals(b.getAwayTeam().getId())
                    && Objects.equals(a.getRound(), b.getRound());
        }

        String slotKey(MatchGame m, boolean home) {
            var zone = home ? m.getHomeSourceZone() : m.getAwaySourceZone();
            if (zone != null) return zone.getId() + "#" + (home ? m.getHomeSourcePosition() : m.getAwaySourcePosition());
            return "team" + (home ? m.getHomeTeam() : m.getAwayTeam()).getId();
        }

        List<String> matchDiff(MatchGame cur, MatchGame xl) {
            var diff = new ArrayList<String>();
            if (!Objects.equals(id(cur.getZone()), id(xl.getZone()))) diff.add("zona");
            if (!Objects.equals(cur.getScheduledAt(), xl.getScheduledAt())) diff.add("fecha/horario");
            if (!Objects.equals(cur.getRoundLabel(), xl.getRoundLabel())) diff.add("etiqueta de fecha");
            if (!Objects.equals(cur.getVenue(), xl.getVenue()) || !Objects.equals(cur.getPitch(), xl.getPitch())) diff.add("campo/cancha");
            if (!comparableNote(cur.getNotes()).equals(comparableNote(xl.getNotes()))) diff.add("nota");
            if (cur.getStatus() != xl.getStatus() || (xl.isFinished() && !resultText(cur).equals(resultText(xl)))) diff.add("resultado");
            return diff;
        }

        /** Ignores the walkover sentence the exporter adds for absences recorded on the web (WorkbookExporter#note). */
        String comparableNote(String note) {
            if (note == null) return "";
            return note.replaceAll("(?i)\\s*(-\\s*)?el equipo .+ no se presento\\s*$", "").trim();
        }

        void copyMatch(MatchGame from, MatchGame to) {
            to.setZone(from.getZone());
            to.setScheduledAt(from.getScheduledAt());
            to.setRound(from.getRound());
            to.setRoundLabel(from.getRoundLabel());
            to.setVenue(from.getVenue());
            to.setPitch(from.getPitch());
            to.setNotes(from.getNotes());
            to.setStatus(from.getStatus());
            to.setHomeGoals(from.getHomeGoals());
            to.setAwayGoals(from.getAwayGoals());
            to.setHomePenalties(from.getHomePenalties());
            to.setAwayPenalties(from.getAwayPenalties());
            to.setHomePresent(from.getHomePresent());
            to.setAwayPresent(from.getAwayPresent());
        }

        String resultText(MatchGame m) {
            if (!m.isFinished()) return "sin jugar";
            var s = m.getHomeGoals() + "-" + m.getAwayGoals();
            if (m.getHomePenalties() != null) s += " (" + m.getHomePenalties() + "-" + m.getAwayPenalties() + " pen.)";
            if (!m.present(true) || !m.present(false)) s += " W.O.";
            return s;
        }

        String describe(Tournament t, MatchGame m) {
            var round = m.getRound() != null ? "Fecha " + m.getRound() : m.getRoundLabel() != null ? m.getRoundLabel() : "";
            return t.getName() + " · " + round + " · " + m.getHomeTeam().getName() + " vs " + m.getAwayTeam().getName();
        }

        /** The Excel's PR should be one point per match the team showed up to (our rule). */
        void checkPresencePoints() {
            for (var row : wb.tableTeams) {
                if (row.pr() == null) continue;
                var key = Sheet.norm(row.team());
                int present = 0, absent = 0;
                for (var m : wb.matches) {
                    if (m.stage() != row.stage() || m.type() != MatchGame.Type.REGULAR || !m.played()) continue;
                    if (!m.home().isPlaceholder() && Sheet.norm(m.home().team()).equals(key)) {
                        if (m.homePresent()) present++; else absent++;
                    } else if (!m.away().isPlaceholder() && Sheet.norm(m.away().team()).equals(key)) {
                        if (m.awayPresent()) present++; else absent++;
                    }
                }
                if (row.pr() != present) {
                    report.warnings.add(row.where() + ": " + row.team() + " tiene PR " + row.pr() + " en el Excel; según los "
                            + "partidos corresponde " + present + (absent > 0 ? " (no se presentó en " + absent + ")" : "")
                            + ". La web usa " + present + ".");
                }
            }
        }

        /**
         * The Excel's PTOS minus its PR must equal the points the matches give (3 per win, 1 per draw). Otherwise a
         * result in the fixture sheet does not match the table (or a match is missing).
         */
        void checkTablePoints() {
            var byZone = new HashMap<Long, Map<Long, TablePoints>>();
            for (var row : wb.tableTeams) {
                if (row.points() == null || row.pr() == null) continue;
                var zone = teamZone.getOrDefault(row.stage(), Map.of()).get(Sheet.norm(row.team()));
                if (zone == null) continue;
                var table = byZone.computeIfAbsent(zone.getId(), id -> {
                    var map = new HashMap<Long, TablePoints>();
                    for (var r : standingsService.zoneStandings(id)) map.put(r.team().id(), new TablePoints(r.points(), r.presencePoints()));
                    return map;
                });
                var ours = table.get(team(row.team()).getId());
                if (ours == null) continue;
                int excelResultPoints = row.points() - row.pr();
                int ourResultPoints = ours.points() - ours.presence();
                if (excelResultPoints != ourResultPoints) {
                    report.warnings.add(row.where() + ": " + row.team() + " suma " + excelResultPoints
                            + " puntos por resultados en la tabla del Excel, pero sus partidos dan " + ourResultPoints
                            + ". Revisar resultados o partidos faltantes.");
                }
            }
        }

        // ---------------------------------------------------------------- players and annual data

        void importPlayerStats() {
            record Totals(String player, String team, Integer goals, Integer figures, Integer conceded) {}
            var totals = new LinkedHashMap<String, Totals>();
            for (var list : List.of(wb.goals, wb.figures, wb.goalsConceded)) {
                var seen = new HashMap<String, PlayerValue>();
                for (var v : list) {
                    var prev = seen.put(key(v), v);
                    if (prev != null) report.warnings.add(v.where() + ": " + v.player() + " (" + v.team()
                            + ") aparece repetido (también en " + prev.where() + "); se toma el último valor");
                }
            }
            for (var v : wb.goals) totals.merge(key(v), new Totals(v.player(), v.team(), v.value(), null, null),
                    (a, b) -> new Totals(a.player(), a.team(), b.goals(), a.figures(), a.conceded()));
            for (var v : wb.figures) totals.merge(key(v), new Totals(v.player(), v.team(), null, v.value(), null),
                    (a, b) -> new Totals(a.player(), a.team(), a.goals(), b.figures(), a.conceded()));
            for (var v : wb.goalsConceded) totals.merge(key(v), new Totals(v.player(), v.team(), null, null, v.value()),
                    (a, b) -> new Totals(a.player(), a.team(), a.goals(), a.figures(), b.conceded()));

            var existing = new HashMap<Long, PlayerSeasonStat>();
            for (var s : statRepo.findBySeason(season)) existing.put(s.getPlayer().getId(), s);
            for (var tot : totals.values()) {
                var team = team(tot.team());
                var player = player(team, tot.player());
                var label = player.getFullName() + " (" + team.getName() + ")";
                var stat = existing.get(player.getId());
                if (stat == null) {
                    stat = new PlayerSeasonStat(null, season, player, team, tot.goals(), tot.figures(), tot.conceded(), DataSource.EXCEL);
                    statRepo.save(stat);
                    report.created(STATS, label);
                    continue;
                }
                if (Objects.equals(stat.getGoals(), tot.goals()) && Objects.equals(stat.getFigureCount(), tot.figures())
                        && Objects.equals(stat.getGoalsConceded(), tot.conceded())) {
                    report.unchanged(STATS);
                    continue;
                }
                if (!allowed("stat:" + stat.getId(), stat.getSource(), STATS, label,
                        statsText(tot.goals(), tot.figures(), tot.conceded()),
                        statsText(stat.getGoals(), stat.getFigureCount(), stat.getGoalsConceded()))) continue;
                stat.setTeam(team);
                stat.setGoals(tot.goals());
                stat.setFigureCount(tot.figures());
                stat.setGoalsConceded(tot.conceded());
                stat.setSource(DataSource.EXCEL);
                report.updated(STATS, label);
            }
        }

        String statsText(Integer goals, Integer figures, Integer conceded) {
            return "goles " + (goals == null ? "-" : goals) + ", figura " + (figures == null ? "-" : figures)
                    + ", goles recibidos " + (conceded == null ? "-" : conceded);
        }

        void importSanctions() {
            var existing = sanctionRepo.findBySeasonOrderByIdAsc(season);
            for (var s : wb.sanctions) {
                var team = team(s.team());
                var player = player(team, s.player());
                var tournament = s.stage() == null ? null : tournament(s.stage());
                var label = player.getFullName() + " (" + team.getName() + ") — " + (s.notes() == null ? s.type() : s.notes());
                var match = existing.stream().filter(e -> e.getPlayer().getId().equals(player.getId()) && e.getType() == s.type()
                        && (Sheet.norm(e.getNotes()).equals(Sheet.norm(s.notes()))
                            // created on the web without notes; the export wrote generated ones
                            || (e.getNotes() == null && e.getMatchesSuspended() == s.matchesSuspended())))
                        .findFirst().orElse(null);
                if (match == null) {
                    var n = new Sanction(null, season, tournament, player, team, s.type(), s.matchesSuspended(),
                            s.matchesServed(), s.status(), s.notes(), DataSource.EXCEL);
                    existing.add(sanctionRepo.save(n));
                    report.created(SANCTIONS, label);
                    continue;
                }
                if (match.getMatchesSuspended() == s.matchesSuspended() && match.getMatchesServed() == s.matchesServed()
                        && match.getStatus() == s.status()) {
                    report.unchanged(SANCTIONS);
                    continue;
                }
                if (!allowed("sanction:" + match.getId(), match.getSource(), SANCTIONS, label,
                        s.matchesServed() + "/" + s.matchesSuspended() + " " + s.status(),
                        match.getMatchesServed() + "/" + match.getMatchesSuspended() + " " + match.getStatus())) continue;
                match.setMatchesSuspended(s.matchesSuspended());
                match.setMatchesServed(s.matchesServed());
                match.setStatus(s.status());
                match.setTournament(tournament);
                match.setSource(DataSource.EXCEL);
                report.updated(SANCTIONS, label + ": " + s.matchesServed() + "/" + s.matchesSuspended() + " fechas");
            }
        }

        void importYellows() {
            // The same player can be listed twice in a block: keep the highest count and say so
            var unique = new LinkedHashMap<String, ParsedYellows>();
            for (var y : wb.yellows) {
                var k = y.stage() + "|" + Sheet.norm(y.team()) + "|" + Sheet.norm(y.player());
                var prev = unique.get(k);
                if (prev != null) {
                    report.warnings.add(y.where() + ": " + y.player() + " (" + y.team() + ") aparece repetido en las amarillas"
                            + " (también en " + prev.where() + "); se toma " + Math.max(prev.yellowCards(), y.yellowCards()));
                    if (prev.yellowCards() >= y.yellowCards()) continue;
                }
                unique.put(k, y);
            }
            for (var y : unique.values()) {
                var t = tournament(y.stage());
                var team = team(y.team());
                var player = player(team, y.player());
                var label = t.getName() + " · " + player.getFullName() + " (" + team.getName() + ")";
                var tally = yellowRepo.findByTournamentAndPlayer(t, player).orElse(null);
                if (tally == null) {
                    yellowRepo.save(new YellowCardTally(null, t, player, team, y.yellowCards(), DataSource.EXCEL));
                    report.created(YELLOWS, label + ": " + y.yellowCards());
                    continue;
                }
                if (tally.getYellowCards() == y.yellowCards()) {
                    report.unchanged(YELLOWS);
                    continue;
                }
                if (!allowed("yellow:" + tally.getId(), tally.getSource(), YELLOWS, label,
                        String.valueOf(y.yellowCards()), String.valueOf(tally.getYellowCards()))) continue;
                tally.setYellowCards(y.yellowCards());
                tally.setSource(DataSource.EXCEL);
                report.updated(YELLOWS, label + ": " + y.yellowCards());
            }
        }

        void importChampions() {
            for (var c : wb.champions) {
                var team = team(c.team());
                var label = c.year() + " Categoría " + c.category();
                var current = championRepo.findByYearAndCategoryName(c.year(), c.category()).orElse(null);
                if (current == null) {
                    championRepo.save(new Champion(null, c.year(), c.category(), team, DataSource.EXCEL));
                    report.created(CHAMPIONS, label + ": " + team.getName());
                    continue;
                }
                if (current.getTeam().getId().equals(team.getId())) {
                    report.unchanged(CHAMPIONS);
                    continue;
                }
                if (!allowed("champion:" + current.getId(), current.getSource(), CHAMPIONS, label,
                        team.getName(), current.getTeam().getName())) continue;
                current.setTeam(team);
                current.setSource(DataSource.EXCEL);
                report.updated(CHAMPIONS, label + ": " + team.getName());
            }
        }

        // ---------------------------------------------------------------- helpers

        /** Records edited on the web are only overwritten when the admin chose to (key in `overwrite`). */
        boolean allowed(String key, DataSource source, String entity, String label, String excel, String web) {
            if (source != DataSource.WEB) return true;
            boolean force = overwrite.contains(key);
            report.conflict(key, entity, label, excel, web, force);
            return force;
        }

        Team team(String name) {
            return teams.computeIfAbsent(Sheet.norm(name), k -> {
                var t = new Team();
                t.setName(name.trim());
                report.created(TEAMS, t.getName());
                return teamRepo.save(t);
            });
        }

        Player player(Team team, String name) {
            var roster = playersByTeam.computeIfAbsent(team.getId(), id -> {
                var map = new HashMap<String, Player>();
                for (var p : playerRepo.findByTeamOrderByJerseyNoAscFullNameAsc(team)) map.put(Sheet.norm(p.getFullName()), p);
                return map;
            });
            return roster.computeIfAbsent(Sheet.norm(name), k -> {
                var p = new Player();
                p.setFullName(name.trim());
                p.setTeam(team);
                report.created(PLAYERS, p.getFullName() + " (" + team.getName() + ")");
                return playerRepo.save(p);
            });
        }

        String key(PlayerValue v) {
            return Sheet.norm(v.team()) + "|" + Sheet.norm(v.player());
        }

        boolean matchesGroup(Zone zone, Group group) {
            return group.kind() == Group.Kind.CATEGORY
                    ? Sheet.norm(zone.getCategory().getName()).equals(group.name())
                    : Sheet.norm(zone.getName()).equals("ZONA " + group.name());
        }

        String groupName(Zone zone) {
            return Sheet.norm(zone.getCategory().getName()).equals("UNICA") ? zone.getName()
                    : "Categoría " + zone.getCategory().getName()
                    + (zoneRepo.countByCategory(zone.getCategory()) > 1 ? " · " + zone.getName() : "");
        }

        Long id(Zone z) {
            return z == null ? null : z.getId();
        }
    }
}
