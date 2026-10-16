package com.mjt.Football_Tournament_Management_System.service;

import com.mjt.Football_Tournament_Management_System.dto.AdminDtos;
import com.mjt.Football_Tournament_Management_System.dto.ResponseDtos;
import com.mjt.Football_Tournament_Management_System.entire.*;
import com.mjt.Football_Tournament_Management_System.exception.ConflictException;
import com.mjt.Football_Tournament_Management_System.exception.NotFoundException;
import com.mjt.Football_Tournament_Management_System.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Collator;
import java.util.*;
import java.util.function.Function;

/**
 * Annual data of a season that the league keeps in its Excel: player totals (goals, figure, goals conceded),
 * sanctions and accumulated yellow cards per tournament. Web edits mark the record as source WEB.
 */
@Service
@RequiredArgsConstructor
public class SeasonDataService {
    private static final Collator NAME_ORDER = Collator.getInstance(new Locale("es", "AR"));

    private final SeasonRepository seasonRepo;
    private final TournamentRepository tournamentRepo;
    private final PlayerRepository playerRepo;
    private final PlayerSeasonStatRepository statRepo;
    private final SanctionRepository sanctionRepo;
    private final YellowCardTallyRepository yellowRepo;

    // ---------- rankings ----------

    public List<ResponseDtos.SeasonStatRow> scorers(Long seasonId) {
        return ranking(seasonId, PlayerSeasonStat::getGoals, true);
    }

    public List<ResponseDtos.SeasonStatRow> figures(Long seasonId) {
        return ranking(seasonId, PlayerSeasonStat::getFigureCount, true);
    }

    /** Goalkeepers ("imbatibles"): fewest goals conceded first. */
    public List<ResponseDtos.SeasonStatRow> goalkeepers(Long seasonId) {
        return ranking(seasonId, PlayerSeasonStat::getGoalsConceded, false);
    }

    private List<ResponseDtos.SeasonStatRow> ranking(Long seasonId, Function<PlayerSeasonStat, Integer> value, boolean desc) {
        Comparator<PlayerSeasonStat> byValue = Comparator.comparingInt(s -> value.apply(s));
        var sorted = statRepo.findBySeason(findSeason(seasonId)).stream()
                .filter(s -> value.apply(s) != null && (!desc || value.apply(s) > 0))
                .sorted((desc ? byValue.reversed() : byValue)
                        .thenComparing((a, b) -> NAME_ORDER.compare(a.getPlayer().getFullName(), b.getPlayer().getFullName())))
                .toList();
        var out = new ArrayList<ResponseDtos.SeasonStatRow>();
        for (int i = 0; i < sorted.size(); i++) {
            var s = sorted.get(i);
            out.add(new ResponseDtos.SeasonStatRow(i + 1, ResponseDtos.PlayerRef.from(s.getPlayer()),
                    ResponseDtos.TeamRef.from(s.getTeam()), value.apply(s)));
        }
        return out;
    }

    @Transactional
    public void upsertPlayerStat(Long seasonId, AdminDtos.PlayerStatReq req) {
        var season = findSeason(seasonId);
        var player = findPlayer(req.getPlayerId());
        var stat = statRepo.findBySeasonAndPlayer(season, player).orElseGet(() -> {
            var s = new PlayerSeasonStat();
            s.setSeason(season);
            s.setPlayer(player);
            return s;
        });
        stat.setTeam(teamOf(player));
        stat.setGoals(req.getGoals());
        stat.setFigureCount(req.getFigureCount());
        stat.setGoalsConceded(req.getGoalsConceded());
        stat.setSource(DataSource.WEB);
        statRepo.save(stat);
    }

    @Transactional
    public void deletePlayerStat(Long seasonId, Long playerId) {
        statRepo.findBySeasonAndPlayer(findSeason(seasonId), findPlayer(playerId)).ifPresent(statRepo::delete);
    }

    // ---------- sanctions ----------

    public List<ResponseDtos.SanctionRes> sanctions(Long seasonId) {
        return sanctionRepo.findBySeasonOrderByIdAsc(findSeason(seasonId)).stream()
                .sorted(Comparator.comparing((Sanction s) -> s.getStatus() == Sanction.Status.SERVED)
                        .thenComparing((a, b) -> NAME_ORDER.compare(a.getPlayer().getFullName(), b.getPlayer().getFullName())))
                .map(ResponseDtos.SanctionRes::from).toList();
    }

    @Transactional
    public ResponseDtos.SanctionRes createSanction(Long seasonId, AdminDtos.SanctionReq req) {
        var s = new Sanction();
        s.setSeason(findSeason(seasonId));
        apply(s, req);
        return ResponseDtos.SanctionRes.from(sanctionRepo.save(s));
    }

    @Transactional
    public ResponseDtos.SanctionRes updateSanction(Long id, AdminDtos.SanctionReq req) {
        var s = sanctionRepo.findById(id).orElseThrow(() -> NotFoundException.of("Sanción", id));
        apply(s, req);
        return ResponseDtos.SanctionRes.from(s);
    }

    @Transactional
    public void deleteSanction(Long id) {
        sanctionRepo.delete(sanctionRepo.findById(id).orElseThrow(() -> NotFoundException.of("Sanción", id)));
    }

    private void apply(Sanction s, AdminDtos.SanctionReq req) {
        var player = findPlayer(req.getPlayerId());
        s.setPlayer(player);
        s.setTeam(teamOf(player));
        if (req.getTournamentId() != null) {
            var t = tournamentRepo.findById(req.getTournamentId())
                    .orElseThrow(() -> NotFoundException.of("Campeonato", req.getTournamentId()));
            if (t.getSeason() == null || !t.getSeason().getId().equals(s.getSeason().getId())) {
                throw new ConflictException("El campeonato no pertenece a esa temporada");
            }
            s.setTournament(t);
        } else {
            s.setTournament(null);
        }
        s.setType(req.getType());
        s.setMatchesSuspended(req.getMatchesSuspended());
        s.setMatchesServed(req.getMatchesServed());
        s.setStatus(req.getStatus() != null ? req.getStatus()
                : req.getMatchesServed() >= req.getMatchesSuspended() ? Sanction.Status.SERVED : Sanction.Status.PENDING);
        s.setNotes(TournamentService.blankToNull(req.getNotes()));
        s.setSource(DataSource.WEB);
    }

    // ---------- accumulated yellow cards ----------

    public List<ResponseDtos.YellowTallyRes> yellowCards(Long tournamentId) {
        return yellowRepo.findByTournament(findTournament(tournamentId)).stream()
                .sorted(Comparator.comparingInt(YellowCardTally::getYellowCards).reversed()
                        .thenComparing((a, b) -> NAME_ORDER.compare(a.getPlayer().getFullName(), b.getPlayer().getFullName())))
                .map(ResponseDtos.YellowTallyRes::from).toList();
    }

    @Transactional
    public void upsertYellowCards(Long tournamentId, AdminDtos.YellowTallyReq req) {
        var t = findTournament(tournamentId);
        var player = findPlayer(req.getPlayerId());
        var tally = yellowRepo.findByTournamentAndPlayer(t, player).orElseGet(() -> {
            var y = new YellowCardTally();
            y.setTournament(t);
            y.setPlayer(player);
            return y;
        });
        tally.setTeam(teamOf(player));
        tally.setYellowCards(req.getYellowCards());
        tally.setSource(DataSource.WEB);
        yellowRepo.save(tally);
    }

    @Transactional
    public void deleteYellowCards(Long tournamentId, Long playerId) {
        yellowRepo.findByTournamentAndPlayer(findTournament(tournamentId), findPlayer(playerId)).ifPresent(yellowRepo::delete);
    }

    // ---------- helpers ----------

    private Season findSeason(Long id) {
        return seasonRepo.findById(id).orElseThrow(() -> NotFoundException.of("Temporada", id));
    }

    private Tournament findTournament(Long id) {
        return tournamentRepo.findById(id).orElseThrow(() -> NotFoundException.of("Campeonato", id));
    }

    private Player findPlayer(Long id) {
        return playerRepo.findById(id).orElseThrow(() -> NotFoundException.of("Jugador", id));
    }

    private static Team teamOf(Player p) {
        if (p.getTeam() == null) throw new ConflictException("El jugador " + p.getFullName() + " no tiene equipo");
        return p.getTeam();
    }
}
