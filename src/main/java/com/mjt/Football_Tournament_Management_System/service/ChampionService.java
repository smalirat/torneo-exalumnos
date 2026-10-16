package com.mjt.Football_Tournament_Management_System.service;

import com.mjt.Football_Tournament_Management_System.dto.AdminDtos;
import com.mjt.Football_Tournament_Management_System.dto.ResponseDtos;
import com.mjt.Football_Tournament_Management_System.entire.Champion;
import com.mjt.Football_Tournament_Management_System.entire.DataSource;
import com.mjt.Football_Tournament_Management_System.exception.NotFoundException;
import com.mjt.Football_Tournament_Management_System.repo.ChampionRepository;
import com.mjt.Football_Tournament_Management_System.repo.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Collator;
import java.util.*;

/** Historic champions by year and category, and the "most titles" ranking per category. */
@Service
@RequiredArgsConstructor
public class ChampionService {
    private static final Collator NAME_ORDER = Collator.getInstance(new Locale("es", "AR"));

    private final ChampionRepository championRepo;
    private final TeamRepository teamRepo;

    public List<ResponseDtos.CategoryHistory> history() {
        var byCategory = new TreeMap<String, List<Champion>>();
        for (var c : championRepo.findAllByOrderByCategoryNameAscYearDesc()) {
            byCategory.computeIfAbsent(c.getCategoryName(), k -> new ArrayList<>()).add(c);
        }
        var out = new ArrayList<ResponseDtos.CategoryHistory>();
        byCategory.forEach((category, champions) -> {
            var titles = new LinkedHashMap<Long, int[]>();
            var teams = new HashMap<Long, ResponseDtos.TeamRef>();
            for (var c : champions) {
                titles.computeIfAbsent(c.getTeam().getId(), k -> new int[1])[0]++;
                teams.putIfAbsent(c.getTeam().getId(), ResponseDtos.TeamRef.from(c.getTeam()));
            }
            var top = titles.entrySet().stream()
                    .map(e -> new ResponseDtos.TitleCount(teams.get(e.getKey()), e.getValue()[0]))
                    .sorted(Comparator.comparingInt(ResponseDtos.TitleCount::titles).reversed()
                            .thenComparing((a, b) -> NAME_ORDER.compare(a.team().name(), b.team().name())))
                    .toList();
            out.add(new ResponseDtos.CategoryHistory(category,
                    champions.stream().map(ResponseDtos.ChampionRes::from).toList(), top));
        });
        return out;
    }

    /** Sets the champion of a year and category (replaces an existing one). */
    @Transactional
    public ResponseDtos.ChampionRes upsert(AdminDtos.ChampionReq req) {
        var category = req.getCategoryName().trim().toUpperCase(Locale.ROOT);
        var team = teamRepo.findById(req.getTeamId()).orElseThrow(() -> NotFoundException.of("Equipo", req.getTeamId()));
        var champion = championRepo.findByYearAndCategoryName(req.getYear(), category).orElseGet(() -> {
            var c = new Champion();
            c.setYear(req.getYear());
            c.setCategoryName(category);
            return c;
        });
        champion.setTeam(team);
        champion.setSource(DataSource.WEB);
        return ResponseDtos.ChampionRes.from(championRepo.save(champion));
    }

    @Transactional
    public void delete(Long id) {
        championRepo.delete(championRepo.findById(id).orElseThrow(() -> NotFoundException.of("Campeón", id)));
    }
}
