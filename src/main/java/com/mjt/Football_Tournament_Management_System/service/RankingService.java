package com.mjt.Football_Tournament_Management_System.service;

import com.mjt.Football_Tournament_Management_System.dto.ResponseDtos;
import com.mjt.Football_Tournament_Management_System.entire.MatchEvent;
import com.mjt.Football_Tournament_Management_System.exception.NotFoundException;
import com.mjt.Football_Tournament_Management_System.repo.MatchEventRepository;
import com.mjt.Football_Tournament_Management_System.repo.TournamentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.Collator;
import java.util.*;

/** Per-tournament player rankings built from match events. */
@Service
@RequiredArgsConstructor
public class RankingService {
    private static final Collator NAME_ORDER = Collator.getInstance(new Locale("es", "AR"));

    private final TournamentRepository tournamentRepo;
    private final MatchEventRepository eventRepo;

    /** Top scorers; own goals do not count. Ties sorted by name. */
    public List<ResponseDtos.ScorerRow> scorers(Long tournamentId) {
        var rows = new LinkedHashMap<Long, int[]>();
        var refs = new HashMap<Long, MatchEvent>();
        for (var e : events(tournamentId, List.of(MatchEvent.Type.GOAL))) {
            rows.computeIfAbsent(e.getPlayer().getId(), k -> new int[1])[0]++;
            refs.putIfAbsent(e.getPlayer().getId(), e);
        }
        return rows.entrySet().stream()
                .map(en -> {
                    var e = refs.get(en.getKey());
                    return new ResponseDtos.ScorerRow(ResponseDtos.PlayerRef.from(e.getPlayer()),
                            ResponseDtos.TeamRef.from(e.getTeam()), en.getValue()[0]);
                })
                .sorted(Comparator.comparingInt(ResponseDtos.ScorerRow::goals).reversed()
                        .thenComparing((a, b) -> NAME_ORDER.compare(a.player().fullName(), b.player().fullName())))
                .toList();
    }

    /** Booked players: most red cards first, then most yellow cards, then name. */
    public List<ResponseDtos.CardRow> cards(Long tournamentId) {
        var rows = new LinkedHashMap<Long, int[]>(); // [yellow, red]
        var refs = new HashMap<Long, MatchEvent>();
        for (var e : events(tournamentId, List.of(MatchEvent.Type.YELLOW_CARD, MatchEvent.Type.RED_CARD))) {
            var counts = rows.computeIfAbsent(e.getPlayer().getId(), k -> new int[2]);
            counts[e.getType() == MatchEvent.Type.YELLOW_CARD ? 0 : 1]++;
            refs.putIfAbsent(e.getPlayer().getId(), e);
        }
        return rows.entrySet().stream()
                .map(en -> {
                    var e = refs.get(en.getKey());
                    return new ResponseDtos.CardRow(ResponseDtos.PlayerRef.from(e.getPlayer()),
                            ResponseDtos.TeamRef.from(e.getTeam()), en.getValue()[0], en.getValue()[1]);
                })
                .sorted(Comparator.comparingInt(ResponseDtos.CardRow::redCards).reversed()
                        .thenComparing(Comparator.comparingInt(ResponseDtos.CardRow::yellowCards).reversed())
                        .thenComparing((a, b) -> NAME_ORDER.compare(a.player().fullName(), b.player().fullName())))
                .toList();
    }

    private List<MatchEvent> events(Long tournamentId, List<MatchEvent.Type> types) {
        var t = tournamentRepo.findById(tournamentId).orElseThrow(() -> NotFoundException.of("Campeonato", tournamentId));
        return eventRepo.findByMatch_TournamentAndTypeIn(t, types);
    }
}
