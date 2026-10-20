package com.mjt.Football_Tournament_Management_System.service;

import com.mjt.Football_Tournament_Management_System.dto.AdminDtos;
import com.mjt.Football_Tournament_Management_System.dto.ResponseDtos;
import com.mjt.Football_Tournament_Management_System.entire.MatchGame;
import com.mjt.Football_Tournament_Management_System.entire.Team;
import com.mjt.Football_Tournament_Management_System.entire.Tournament;
import com.mjt.Football_Tournament_Management_System.entire.Zone;
import com.mjt.Football_Tournament_Management_System.exception.BadRequestException;
import com.mjt.Football_Tournament_Management_System.exception.NotFoundException;
import com.mjt.Football_Tournament_Management_System.repo.MatchRepository;
import com.mjt.Football_Tournament_Management_System.repo.RegistrationRepository;
import com.mjt.Football_Tournament_Management_System.repo.TeamRepository;
import com.mjt.Football_Tournament_Management_System.repo.TournamentRepository;
import com.mjt.Football_Tournament_Management_System.repo.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;

/**
 * Interzonal and intercategory match days, crossed by positions: the tables at the moment of generating, or
 * the seeding when an edition is built automatically.
 * <p>
 * For each pair of zones, match day k crosses position i of the smaller zone with position (i + k) of the other
 * one (SAME: 1º vs 1º, then 1º vs 2º…) or (último - i + k) (INVERSE: 1º vs último…): nobody repeats a rival
 * while k is below the size of the bigger zone, and its extra teams rest. Home and away alternate every match day.
 * They are regular matches without zone, so they count in the table of each team's zone.
 */
@Service
@RequiredArgsConstructor
public class InterzonalService {
    private final TournamentRepository tournamentRepo;
    private final ZoneRepository zoneRepo;
    private final RegistrationRepository regRepo;
    private final MatchRepository matchRepo;
    private final TeamRepository teamRepo;
    private final StandingsService standings;

    /** The matches it would create (preview: nothing saved) or creates them, from the tables now */
    @Transactional
    public List<ResponseDtos.MatchRes> generate(Long tournamentId, AdminDtos.InterzonalReq req, boolean save) {
        var t = tournamentRepo.findById(tournamentId).orElseThrow(() -> NotFoundException.of("Campeonato", tournamentId));
        var withdrawn = new HashSet<Long>();
        for (var r : regRepo.findByTournament(t)) if (r.isWithdrawn()) withdrawn.add(r.getTeam().getId());
        var out = cross(t, req.getPairs(), zone -> table(zone, withdrawn), req.getRounds(), req.getFirstRound(),
                req.getStart(), req.getMinutesBetween(), req.getCrossing(), req.getVenue());
        var result = save ? matchRepo.saveAll(out) : out;
        return result.stream().map(ResponseDtos.MatchRes::from).toList();
    }

    /**
     * The crossing itself, over the teams of each zone in order ("position"): the table now, or the seeding when
     * an edition is built (its tables are still empty). Returns the matches, not saved.
     */
    public List<MatchGame> cross(Tournament t, List<AdminDtos.ZonePair> zonePairs, Function<Zone, List<Team>> ordered,
                                 int rounds, Integer firstRound, LocalDateTime start, int minutesBetween,
                                 AdminDtos.InterzonalReq.Crossing crossing, String venueText) {
        record Pair(Zone a, Zone b, List<Team> teamsA, List<Team> teamsB) {}
        var pairs = new ArrayList<Pair>();
        var used = new HashSet<Long>();
        for (var p : zonePairs) {
            var a = zoneOf(t, p.getZoneA());
            var b = zoneOf(t, p.getZoneB());
            if (a.getId().equals(b.getId())) throw new BadRequestException("Una zona no puede cruzarse con sí misma");
            if (!used.add(a.getId()) || !used.add(b.getId())) {
                throw new BadRequestException("Cada zona va en un solo cruce (si no, un equipo jugaría dos veces en la misma fecha)");
            }
            var pair = new Pair(a, b, ordered.apply(a), ordered.apply(b));
            int bigger = Math.max(pair.teamsA().size(), pair.teamsB().size());
            if (Math.min(pair.teamsA().size(), pair.teamsB().size()) == 0) {
                throw new BadRequestException(label(a) + " o " + label(b) + " no tiene equipos");
            }
            if (rounds > bigger) {
                throw new BadRequestException("Entre " + label(a) + " y " + label(b) + " alcanzan " + bigger
                        + (bigger == 1 ? " fecha" : " fechas") + " de interzonales sin repetir rival");
            }
            pairs.add(pair);
        }

        int first = firstRound != null ? firstRound
                : matchRepo.findByTournament(t).stream().map(MatchGame::getRound).filter(Objects::nonNull)
                        .max(Integer::compare).orElse(0) + 1;
        var venue = venueText == null || venueText.isBlank() ? null : venueText.trim();
        var out = new ArrayList<MatchGame>();
        for (int k = 0; k < rounds; k++) {
            var cursor = start == null ? null : start.plusWeeks(k);
            for (var p : pairs) {
                boolean aSmall = p.teamsA().size() <= p.teamsB().size();
                var small = aSmall ? p.teamsA() : p.teamsB();
                var big = aSmall ? p.teamsB() : p.teamsA();
                int b = big.size();
                for (int i = 0; i < small.size(); i++) {
                    int j = crossing == AdminDtos.InterzonalReq.Crossing.SAME ? (i + k) % b : (b - 1 - i + k) % b;
                    var fromA = aSmall ? small.get(i) : big.get(j);
                    var fromB = aSmall ? big.get(j) : small.get(i);
                    var m = new MatchGame();
                    m.setTournament(t);
                    m.setType(MatchGame.Type.REGULAR);
                    m.setHomeTeam(k % 2 == 0 ? fromA : fromB);
                    m.setAwayTeam(k % 2 == 0 ? fromB : fromA);
                    m.setRound(first + k);
                    m.setRoundLabel(p.a().getCategory().getId().equals(p.b().getCategory().getId()) ? "Interzonal" : "Intercategoría");
                    m.setVenue(venue);
                    m.setScheduledAt(cursor);
                    if (cursor != null) cursor = cursor.plusMinutes(minutesBetween);
                    out.add(m);
                }
            }
        }
        return out;
    }

    /** Teams of the zone in the order of its table now (the ones that withdrew do not play) */
    private List<Team> table(Zone zone, Set<Long> withdrawn) {
        var out = new ArrayList<Team>();
        for (var row : standings.zoneStandings(zone.getId())) {
            if (withdrawn.contains(row.team().id())) continue;
            out.add(teamRepo.findById(row.team().id()).orElseThrow());
        }
        return out;
    }

    private Zone zoneOf(Tournament t, Long zoneId) {
        var z = zoneRepo.findById(zoneId).orElseThrow(() -> NotFoundException.of("Zona", zoneId));
        if (!z.getCategory().getTournament().getId().equals(t.getId())) throw new BadRequestException("La zona no es de esta edición");
        return z;
    }

    /** Same naming rule as the UI: only the zone with a single category; no "Zona 1" when it is the only one */
    private static String label(Zone z) {
        var c = z.getCategory();
        if (c.getTournament().getCategoryCount() <= 1) return z.getName();
        return c.getZoneCount() > 1 ? c.getName() + " · " + z.getName() : c.getName();
    }
}
