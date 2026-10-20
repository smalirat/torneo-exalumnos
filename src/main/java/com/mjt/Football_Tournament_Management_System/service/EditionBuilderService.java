package com.mjt.Football_Tournament_Management_System.service;

import com.mjt.Football_Tournament_Management_System.dto.AdminDtos;
import com.mjt.Football_Tournament_Management_System.dto.ResponseDtos;
import com.mjt.Football_Tournament_Management_System.entire.*;
import com.mjt.Football_Tournament_Management_System.exception.BadRequestException;
import com.mjt.Football_Tournament_Management_System.exception.ConflictException;
import com.mjt.Football_Tournament_Management_System.exception.NotFoundException;
import com.mjt.Football_Tournament_Management_System.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Automatic build of an edition from the results of another one (usually the previous one).
 * <p>
 * Each target category is a level (its order: A = 0, B = 1…). A source category keeps its level when the target
 * has a category with the same name, otherwise its position in the list. Then:
 * <ul>
 *   <li>Promotions decide: the winner plays in the upper category, the loser in the lower one. Between two
 *       groups of the same category (e.g. 5º Zona 1 vs 6º Zona 2) the upper one is that category and the lower one
 *       the next.</li>
 *   <li>The promotion places split each table: in the group that defends the upper category, the teams below
 *       them go down; in the group that challenges it, the teams above them go up. Everyone else stays.</li>
 *   <li>Optionally, interzonal / intercategory match days crossed by seeding (the same crossing as the
 *       Interzonales button of the fixture, see {@link InterzonalService}).</li>
 *   <li>Unplayed promotions, teams new to the edition, teams that withdrew and groups without any rule when
 *       the structure changed are questions for the admin.</li>
 * </ul>
 * Nothing is saved until the admin applies the (reviewed) proposal.
 */
@Service
@RequiredArgsConstructor
public class EditionBuilderService {
    private final TournamentRepository tournamentRepo;
    private final CategoryRepository categoryRepo;
    private final ZoneRepository zoneRepo;
    private final RegistrationRepository regRepo;
    private final MatchRepository matchRepo;
    private final TeamRepository teamRepo;
    private final StandingsService standings;
    private final TournamentService tournaments;
    private final PlayoffService playoffs;
    private final InterzonalService interzonals;
    private final Random random = new Random();

    /** A team in the final table of the source edition */
    private record Entry(Team team, int level, String group, int position, int groupKey, boolean withdrawn, Long zoneId) {}

    /** A promotion of the source edition, seen from one of its sides */
    private record Promo(int upper, int lower, Team winner, String opponent) {}

    @Transactional(readOnly = true)
    public ResponseDtos.BuildProposal proposal(Long targetId, Long sourceId) {
        var target = findTournament(targetId);
        var source = findTournament(sourceId);
        if (target.getId().equals(source.getId())) throw new BadRequestException("Elegí otra edición como base");
        var targetCats = categoryRepo.findByTournamentOrderByDisplayOrderAscIdAsc(target);
        if (targetCats.isEmpty()) throw new ConflictException("Creá primero las categorías de la edición");
        int levels = targetCats.size();

        // ---- final tables of the source edition
        var sourceCats = categoryRepo.findByTournamentOrderByDisplayOrderAscIdAsc(source);
        var withdrawn = new HashSet<Long>();
        for (var r : regRepo.findByTournament(source)) if (r.isWithdrawn()) withdrawn.add(r.getTeam().getId());
        var entries = new LinkedHashMap<Long, Entry>();
        int groupKey = 0;
        for (int ci = 0; ci < sourceCats.size(); ci++) {
            var c = sourceCats.get(ci);
            int level = levelOf(c, ci, targetCats);
            var zones = zoneRepo.findByCategoryOrderByDisplayOrderAscIdAsc(c);
            for (var z : zones) {
                String group = groupLabel(sourceCats.size(), c, zones.size(), z);
                for (var row : standings.zoneStandings(z.getId())) {
                    var team = teamRepo.findById(row.team().id()).orElseThrow();
                    entries.put(team.getId(), new Entry(team, level, group, row.position(), groupKey, withdrawn.contains(team.getId()), z.getId()));
                }
                groupKey++;
            }
        }

        // ---- promotions: who plays where, and the places that split each table
        var promos = new HashMap<Long, Promo>();
        var downPlaces = new HashMap<Integer, List<Integer>>(); // group -> places defending the upper category
        var upPlaces = new HashMap<Integer, List<Integer>>();   // group -> places challenging it
        for (var m : matchRepo.findByTournamentAndTypeInOrderByScheduledAtAsc(source, List.of(MatchGame.Type.PROMOTION))) {
            if (m.getHomeTeam() == null || m.getAwayTeam() == null) continue;
            var a = entries.get(m.getHomeTeam().getId());
            var b = entries.get(m.getAwayTeam().getId());
            if (a == null || b == null) continue;
            int upper = Math.min(a.level(), b.level());
            int lower = a.level() == b.level() ? a.level() + 1 : Math.max(a.level(), b.level());
            if (lower >= levels) continue; // no category below in the new edition: nothing to decide
            var winner = m.winner();
            for (var side : List.of(a, b)) {
                var other = side == a ? b : a;
                promos.put(side.team().getId(), new Promo(upper, lower, winner, other.team().getName()));
                (side.level() == upper ? downPlaces : upPlaces)
                        .computeIfAbsent(side.groupKey(), k -> new ArrayList<>()).add(side.position());
            }
        }

        // ---- proposal for every team of the source edition
        boolean structureChanged = sourceCats.size() != levels;
        var targetRegs = new HashMap<Long, Registration>();
        for (var r : regRepo.findByTournament(target)) targetRegs.put(r.getTeam().getId(), r);
        record Draft(ResponseDtos.BuildTeam row, int level, int sourceLevel, int position, int groupKey) {}
        var drafts = new ArrayList<Draft>();
        for (var e : entries.values()) {
            int level = e.level();
            String reason = "STAYS", detail = null;
            boolean question = false, included = true;
            var promo = promos.get(e.team().getId());
            var down = downPlaces.get(e.groupKey());
            var up = upPlaces.get(e.groupKey());
            if (promo != null) {
                detail = promo.opponent();
                if (promo.winner() == null) {
                    reason = "PROMO_PENDING";
                    question = true;
                    level = Math.min(e.level(), levels - 1);
                } else if (promo.winner().getId().equals(e.team().getId())) {
                    reason = "PROMO_WON";
                    level = promo.upper();
                } else {
                    reason = "PROMO_LOST";
                    level = promo.lower();
                }
            } else if (down != null && e.position() > Collections.max(down) && e.level() + 1 < levels) {
                reason = "DESCENDS";
                level = e.level() + 1;
            } else if (up != null && e.position() < Collections.min(up) && e.level() > 0) {
                reason = "ASCENDS";
                level = e.level() - 1;
            } else if (structureChanged && down == null && up == null) {
                reason = "NO_RULE";
                question = true;
            }
            if (e.withdrawn()) {
                reason = "WITHDRAWN";
                included = false;
                question = true;
            }
            var placed = targetRegs.get(e.team().getId());
            if (placed != null && placed.getZone() != null) {
                reason = "PLACED";
                question = false;
                included = true;
                level = targetCats.indexOf(placed.getZone().getCategory());
            }
            var row = new ResponseDtos.BuildTeam(ResponseDtos.TeamRef.from(e.team()), e.group(), e.position(),
                    targetCats.get(level).getId(), reason, detail, included, question);
            drafts.add(new Draft(row, level, e.level(), e.position(), e.groupKey()));
        }

        // ---- teams registered in the new edition that did not play the source one
        for (var r : targetRegs.values()) {
            if (entries.containsKey(r.getTeam().getId())) continue;
            boolean placed = r.getZone() != null;
            int level = placed ? targetCats.indexOf(r.getZone().getCategory()) : levels - 1;
            var row = new ResponseDtos.BuildTeam(ResponseDtos.TeamRef.from(r.getTeam()), null, null,
                    targetCats.get(level).getId(), placed ? "PLACED" : "NEW", null, true, !placed);
            drafts.add(new Draft(row, level, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
        }

        // Seeding order inside each category: higher source category first, then final position, then group
        drafts.sort(Comparator.comparingInt(Draft::level).thenComparingInt(Draft::sourceLevel)
                .thenComparingInt(Draft::position).thenComparingInt(Draft::groupKey)
                .thenComparing(d -> d.row().team().name()));

        var cats = targetCats.stream()
                .map(c -> new ResponseDtos.BuildCategory(c.getId(), c.getName(), (int) zoneRepo.countByCategory(c)))
                .toList();

        // ---- zones of the new edition, and the definitions of the source copied onto them
        var zones = new ArrayList<ResponseDtos.BuildZone>();
        var targetZoneByKey = new HashMap<String, Zone>();
        for (var c : targetCats) {
            var list = zoneRepo.findByCategoryOrderByDisplayOrderAscIdAsc(c);
            for (int i = 0; i < list.size(); i++) {
                zones.add(new ResponseDtos.BuildZone(list.get(i).getId(), c.getId(), groupLabel(targetCats.size(), c, list.size(), list.get(i))));
                targetZoneByKey.put(zoneKey(c, i), list.get(i));
            }
        }
        var sourceZoneKey = new HashMap<Long, String>();
        for (var c : sourceCats) {
            var list = zoneRepo.findByCategoryOrderByDisplayOrderAscIdAsc(c);
            for (int i = 0; i < list.size(); i++) sourceZoneKey.put(list.get(i).getId(), zoneKey(c, i));
        }
        var definitions = new ArrayList<ResponseDtos.BuildDefinition>();
        var seenDefs = new HashSet<String>();
        for (var m : matchRepo.findByTournamentAndTypeInOrderByScheduledAtAsc(source, List.of(MatchGame.Type.PROMOTION, MatchGame.Type.FINAL))) {
            var home = slotOf(m.getHomeSourceZone(), m.getHomeSourcePosition(), m.getHomeTeam(), entries);
            var away = slotOf(m.getAwaySourceZone(), m.getAwaySourcePosition(), m.getAwayTeam(), entries);
            if (home == null || away == null) continue;
            var hz = targetZoneByKey.get(sourceZoneKey.get(home.zoneId()));
            var az = targetZoneByKey.get(sourceZoneKey.get(away.zoneId()));
            if (hz == null || az == null) continue; // the structure changed: nothing to copy onto
            var def = new ResponseDtos.BuildDefinition(m.typeOrRegular().name(), hz.getId(), home.position(), az.getId(), away.position());
            if (seenDefs.add(def.toString())) definitions.add(def);
        }
        return new ResponseDtos.BuildProposal(source.getId(), source.getName(), matchRepo.existsByTournament(target),
                cats, drafts.stream().map(Draft::row).toList(), zones, definitions);
    }

    /**
     * Applies the reviewed proposal: registers (approved) every team in its category, spreads each category into
     * its zones and optionally generates the round-robin fixture. Only for an edition without matches.
     */
    @Transactional
    public ResponseDtos.BuildResult apply(Long targetId, AdminDtos.BuildReq req) {
        var target = findTournament(targetId);
        if (matchRepo.existsByTournament(target)) {
            throw new ConflictException("La edición ya tiene partidos: el armado automático es para una edición sin partidos");
        }
        var cats = new LinkedHashMap<Long, Category>();
        for (var c : categoryRepo.findByTournamentOrderByDisplayOrderAscIdAsc(target)) cats.put(c.getId(), c);
        var regs = new HashMap<Long, Registration>();
        for (var r : regRepo.findByTournament(target)) regs.put(r.getTeam().getId(), r);

        var byCategory = new LinkedHashMap<Long, List<Registration>>();
        var seen = new HashSet<Long>();
        for (var a : req.getAssignments()) {
            if (!cats.containsKey(a.getCategoryId())) throw new BadRequestException("La categoría no es de esta edición");
            if (!seen.add(a.getTeamId())) throw new BadRequestException("Un equipo aparece dos veces");
            var team = teamRepo.findById(a.getTeamId()).orElseThrow(() -> NotFoundException.of("Equipo", a.getTeamId()));
            var r = regs.get(team.getId());
            if (r == null) {
                r = new Registration();
                r.setTournament(target);
                r.setTeam(team);
            }
            r.setApproved(true);
            r.setRequestedAt(null);
            byCategory.computeIfAbsent(a.getCategoryId(), k -> new ArrayList<>()).add(r);
        }

        // Zones: balanced = snake order of the seeding (1º→Z1, 2º→Z2, 3º→Z2, 4º→Z1…); random = shuffled
        var seeded = new LinkedHashMap<Long, List<Team>>(); // zone -> its teams in seeding order
        for (var e : byCategory.entrySet()) {
            var zones = zoneRepo.findByCategoryOrderByDisplayOrderAscIdAsc(cats.get(e.getKey()));
            var list = new ArrayList<>(e.getValue());
            if (req.getZoneMode() == AdminDtos.BuildReq.ZoneMode.RANDOM) Collections.shuffle(list, random);
            int n = zones.size();
            for (int i = 0; i < list.size(); i++) {
                int lap = i / n, k = i % n;
                var zone = zones.get(req.getZoneMode() == AdminDtos.BuildReq.ZoneMode.RANDOM || lap % 2 == 0 ? k : n - 1 - k);
                list.get(i).setZone(zone);
                seeded.computeIfAbsent(zone.getId(), z -> new ArrayList<>()).add(list.get(i).getTeam());
            }
            regRepo.saveAll(list);
        }
        regRepo.flush();

        int matches = 0;
        if (req.getFixtureStart() != null) {
            for (var c : cats.values()) {
                for (var z : zoneRepo.findByCategoryOrderByDisplayOrderAscIdAsc(c)) {
                    matches += tournaments.createRoundRobinFixtures(z.getId(), req.getFixtureStart(), req.getMinutesBetween()).size();
                }
            }
        }
        var plan = req.getInterzonal();
        if (plan != null) {
            // After the last match day of the edition; with a fixture, a week after its last day, from the same time
            var start = req.getFixtureStart() == null ? null : matchRepo.findByTournament(target).stream()
                    .map(MatchGame::getScheduledAt).filter(Objects::nonNull).max(Comparator.naturalOrder())
                    .map(last -> last.toLocalDate().plusWeeks(1).atTime(req.getFixtureStart().toLocalTime()))
                    .orElse(req.getFixtureStart());
            var crossed = interzonals.cross(target, plan.getPairs(), zone -> seeded.getOrDefault(zone.getId(), List.of()),
                    plan.getRounds(), null, start, req.getMinutesBetween(), plan.getCrossing(), null);
            matches += matchRepo.saveAll(crossed).size();
        }
        int definitions = 0;
        for (var d : req.getDefinitions()) {
            for (var zoneId : List.of(d.getHomeZoneId(), d.getAwayZoneId())) {
                var z = zoneRepo.findById(zoneId).orElseThrow(() -> NotFoundException.of("Zona", zoneId));
                if (!cats.containsKey(z.getCategory().getId())) throw new BadRequestException("La zona no es de esta edición");
            }
            var s = new AdminDtos.SpecialMatchReq();
            s.setType(d.getType());
            s.setHome(slot(d.getHomeZoneId(), d.getHomePosition()));
            s.setAway(slot(d.getAwayZoneId(), d.getAwayPosition()));
            playoffs.create(target.getId(), s);
            definitions++;
        }
        return new ResponseDtos.BuildResult(seen.size(), matches, definitions);
    }

    private static AdminDtos.SlotReq slot(Long zoneId, int position) {
        var s = new AdminDtos.SlotReq();
        s.setZoneId(zoneId);
        s.setPosition(position);
        return s;
    }

    private record Slot(Long zoneId, int position) {}

    /** A side of a source definition as "position of a zone": its rule, or where its team finished */
    private static Slot slotOf(Zone ruleZone, Integer rulePosition, Team team, Map<Long, Entry> entries) {
        if (ruleZone != null && rulePosition != null) return new Slot(ruleZone.getId(), rulePosition);
        var e = team == null ? null : entries.get(team.getId());
        return e == null ? null : new Slot(e.zoneId(), e.position());
    }

    /** A zone by its category name and its place in the category ("a#0"): how source zones map onto new ones */
    private static String zoneKey(Category c, int index) {
        return c.getName().trim().toLowerCase(Locale.ROOT) + "#" + index;
    }

    /** Level of a source category: the target category with the same name, else the same place in the list */
    private static int levelOf(Category c, int index, List<Category> targetCats) {
        for (int i = 0; i < targetCats.size(); i++) {
            if (targetCats.get(i).getName().trim().equalsIgnoreCase(c.getName().trim())) return i;
        }
        return Math.min(index, targetCats.size() - 1);
    }

    /** Same naming rule as the UI: only the zone with a single category; no "Zona 1" when it is the only one */
    private static String groupLabel(int categoryCount, Category c, int zoneCount, Zone z) {
        if (categoryCount == 1) return zoneCount > 1 ? z.getName() : c.getName();
        return zoneCount > 1 ? c.getName() + " · " + z.getName() : c.getName();
    }

    private Tournament findTournament(Long id) {
        return tournamentRepo.findById(id).orElseThrow(() -> NotFoundException.of("Campeonato", id));
    }
}
