package com.mjt.Football_Tournament_Management_System.service;

import com.mjt.Football_Tournament_Management_System.dto.ResponseDtos;
import com.mjt.Football_Tournament_Management_System.entire.MatchGame;
import com.mjt.Football_Tournament_Management_System.entire.Registration;
import com.mjt.Football_Tournament_Management_System.entire.Team;
import com.mjt.Football_Tournament_Management_System.exception.NotFoundException;
import com.mjt.Football_Tournament_Management_System.repo.MatchRepository;
import com.mjt.Football_Tournament_Management_System.repo.RegistrationRepository;
import com.mjt.Football_Tournament_Management_System.repo.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.Collator;
import java.util.*;

/**
 * Zone standings. Points = 3/1/0 + 1 presence point per match attended.
 * Tie-break: points, goal difference, goals for, head-to-head, fair play, presence points, name.
 */
@Service
@RequiredArgsConstructor
public class StandingsService {
    private static final Collator NAME_ORDER = Collator.getInstance(new Locale("es", "AR"));

    private final ZoneRepository zoneRepo;
    private final RegistrationRepository regRepo;
    private final MatchRepository matchRepo;

    public List<ResponseDtos.StandingRow> zoneStandings(Long zoneId) {
        var zone = zoneRepo.findById(zoneId).orElseThrow(() -> NotFoundException.of("Zona", zoneId));
        var rows = new LinkedHashMap<Long, Row>();
        for (Registration r : regRepo.findByZone(zone)) rows.put(r.getTeam().getId(), new Row(r.getTeam()));

        // All regular matches of the tournament between teams of this zone and anyone: zone matches plus
        // interzonal ones (no zone), which count in the table of each team's zone
        var finished = matchRepo.findByTournament(zone.getCategory().getTournament()).stream()
                .filter(m -> !m.isSpecial() && m.counts())
                .filter(m -> m.getZone() == null || m.getZone().getId().equals(zoneId))
                .filter(m -> rows.containsKey(m.getHomeTeam().getId()) || rows.containsKey(m.getAwayTeam().getId()))
                .toList();
        for (var m : finished) {
            var home = rows.get(m.getHomeTeam().getId());
            var away = rows.get(m.getAwayTeam().getId());
            if (home != null) home.add(m, true);
            if (away != null) away.add(m, false);
        }

        var sorted = new ArrayList<>(rows.values());
        sorted.sort(Comparator.comparingInt(Row::points).reversed()
                .thenComparing(Comparator.comparingInt(Row::goalDiff).reversed())
                .thenComparing(Comparator.comparingInt((Row r) -> r.goalsFor).reversed()));
        breakTies(sorted, finished);

        var out = new ArrayList<ResponseDtos.StandingRow>();
        for (int i = 0; i < sorted.size(); i++) out.add(sorted.get(i).toRes(i + 1));
        return out;
    }

    /** Re-sorts each block tied on points/difference/goals with the remaining criteria. */
    private void breakTies(List<Row> rows, List<MatchGame> matches) {
        int i = 0;
        while (i < rows.size()) {
            int j = i + 1;
            while (j < rows.size() && rows.get(j).tiedWith(rows.get(i))) j++;
            if (j - i > 1) {
                var block = rows.subList(i, j);
                var ids = new HashSet<Long>();
                block.forEach(r -> ids.add(r.team.getId()));
                block.forEach(r -> r.headToHead = headToHeadPoints(r.team, ids, matches));
                block.sort(Comparator.comparingInt((Row r) -> r.headToHead).reversed()
                        .thenComparingInt(r -> r.fairPlay)
                        .thenComparing(Comparator.comparingInt((Row r) -> r.presence).reversed())
                        .thenComparing((a, b) -> NAME_ORDER.compare(a.team.getName(), b.team.getName())));
            }
            i = j;
        }
    }

    private int headToHeadPoints(Team team, Set<Long> tied, List<MatchGame> matches) {
        int pts = 0;
        for (var m : matches) {
            if (!tied.contains(m.getHomeTeam().getId()) || !tied.contains(m.getAwayTeam().getId())) continue;
            if (m.getHomeTeam().getId().equals(team.getId())) pts += m.resultPoints(true);
            else if (m.getAwayTeam().getId().equals(team.getId())) pts += m.resultPoints(false);
        }
        return pts;
    }

    private static final class Row {
        final Team team;
        int played, won, drawn, lost, goalsFor, goalsAgainst, presence, fairPlay, headToHead;

        Row(Team team) { this.team = team; }

        void add(MatchGame m, boolean home) {
            played++;
            goalsFor += m.goalsFor(home);
            goalsAgainst += m.goalsAgainst(home);
            presence += m.presencePoints(home);
            fairPlay += m.fairPlayPenalty(home);
            switch (m.resultPoints(home)) {
                case 3 -> won++;
                case 1 -> drawn++;
                default -> lost++;
            }
        }

        int points() { return 3 * won + drawn + presence; }

        int goalDiff() { return goalsFor - goalsAgainst; }

        boolean tiedWith(Row o) {
            return points() == o.points() && goalDiff() == o.goalDiff() && goalsFor == o.goalsFor;
        }

        ResponseDtos.StandingRow toRes(int position) {
            return new ResponseDtos.StandingRow(position, ResponseDtos.TeamRef.from(team), points(), played,
                    won, drawn, lost, goalsFor, goalsAgainst, goalDiff(), presence, fairPlay);
        }
    }
}
