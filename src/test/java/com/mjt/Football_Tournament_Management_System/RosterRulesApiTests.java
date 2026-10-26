package com.mjt.Football_Tournament_Management_System;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Roster rules of the league: banned players, 11 to 24 players, additions deadlines */
class RosterRulesApiTests extends ApiTestSupport {

    private static String freshDni() { return String.valueOf(20_000_000 + new Random().nextInt(70_000_000)); }

    private String players(int n) {
        var list = new ArrayList<String>();
        for (int i = 0; i < n; i++) list.add("{\"fullName\":\"Jugador " + i + "\",\"jerseyNo\":" + (i + 1) + "}");
        return "{\"players\":[" + String.join(",", list) + "]}";
    }

    @Test
    void bannedPlayersCannotBeAdded() throws Exception {
        String admin = admin();
        long team = newTeam(admin);
        String already = freshDni(), banned = freshDni();
        long inRoster = read(post("/api/teams/" + team + "/players", "{\"players\":[{\"fullName\":\"Ya Estaba\",\"jerseyNo\":1,\"dni\":\""
                + already + "\"}]}", admin)).get(0).get("id").asLong();

        get("/api/admin/banned", newUser()).andExpect(status().isForbidden());
        post("/api/admin/banned", "{\"dni\":\"" + banned + "\",\"fullName\":\"Malo, Juan\",\"reason\":\"Agresión al árbitro\"}", admin)
                .andExpect(status().isOk());
        post("/api/admin/banned", "{\"dni\":\"" + banned + "\"}", admin).andExpect(status().isConflict());
        // Someone already in a roster can be banned too: the list says where he is, the roster flags him
        long alreadyBan = read(post("/api/admin/banned", "{\"dni\":\"" + already + "\"}", admin).andExpect(status().isOk())).get("id").asLong();
        get("/api/admin/banned", admin)
                .andExpect(jsonPath("$[?(@.dni == '" + already + "')].fullName").value("Ya Estaba"))
                .andExpect(jsonPath("$[?(@.dni == '" + already + "')].currentTeam").isNotEmpty());
        get("/api/teams/" + team + "/roster", admin).andExpect(jsonPath("$[?(@.id == " + inRoster + ")].banned").value(true));

        post("/api/teams/" + team + "/players", "{\"players\":[{\"fullName\":\"Otro\",\"jerseyNo\":2,\"dni\":\"" + banned + "\"}]}", admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("inhabilitados")));
        long noDni = read(post("/api/teams/" + team + "/players", "{\"players\":[{\"fullName\":\"Sin DNI\",\"jerseyNo\":3}]}", admin)).get(0).get("id").asLong();
        patch("/api/teams/" + team + "/players/" + noDni, "{\"dni\":\"" + banned + "\"}", admin).andExpect(status().isConflict());

        delete("/api/admin/banned/" + alreadyBan, admin).andExpect(status().isNoContent());
        get("/api/teams/" + team + "/roster", admin).andExpect(jsonPath("$[?(@.id == " + inRoster + ")].banned").value(false));
    }

    @Test
    void rostersGoFromElevenToTwentyFour() throws Exception {
        String admin = admin();
        long full = newTeam(admin);
        post("/api/teams/" + full + "/players", players(24), admin).andExpect(status().isOk());
        post("/api/teams/" + full + "/players", players(1), admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("24")));

        // A captain cannot leave the team under 11: first the replacement
        String captain = newUser();
        long team = newTeam(captain);
        post("/api/teams/" + team + "/players", players(11), admin).andExpect(status().isOk());
        long first = read(get("/api/teams/" + team + "/roster", captain)).get(0).get("id").asLong();
        delete("/api/teams/" + team + "/players/" + first, captain)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("reemplazo")));
        post("/api/teams/" + team + "/players", players(1), admin).andExpect(status().isOk());
        delete("/api/teams/" + team + "/players/" + first, captain).andExpect(status().isNoContent());
    }

    @Test
    void additionsHaveDeadlines() throws Exception {
        String admin = admin();
        String captain = newUser();
        long team = newTeam(captain), rival = newTeam(admin), third = newTeam(admin);
        long t = newTournament(admin);
        long zone = newCategory(admin, t, "A").get("zones").get(0).get("id").asLong();
        for (long x : new long[]{team, rival, third}) post("/api/admin/zones/" + zone + "/teams", "{\"teamId\":" + x + "}", admin);
        patch("/api/admin/tournaments/" + t + "/status", "{\"status\":\"LIVE\",\"finishActive\":true}", admin).andExpect(status().isOk());
        var now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        // Round 1 already played (20 days ago), round 2 in a month, round 3 and 4 later
        var fixtures = read(post("/api/admin/fixtures/manual", "{\"zoneId\":" + zone + ",\"fixtures\":["
                + fixture(team, rival, 1, now.minusDays(20)) + "," + fixture(rival, third, 2, now.plusDays(30)) + ","
                + fixture(team, third, 3, now.plusDays(37)) + "," + fixture(rival, team, 4, now.plusDays(44)) + "]}", admin));
        patch("/api/admin/matches/" + fixtures.get(0).get("id").asLong() + "/score", "{\"events\":[]}", admin).andExpect(status().isOk());

        // Asked for now (mid-edition): plays from match day 2
        var asked = read(post("/api/teams/" + team + "/players", players(1), captain).andExpect(status().isOk())).get(0);
        assertThat(asked.get("eligibleFromRound").asInt()).isEqualTo(2);
        get("/api/admin/requests", admin)
                .andExpect(jsonPath("$[?(@.id == " + asked.get("id").asLong() + ")].subject")
                        .value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("juega desde la Fecha 2"))));

        // Rounds 2 played too: the next one (3) is one of the last two, no more additions (the admin can)
        patch("/api/admin/matches/" + fixtures.get(1).get("id").asLong() + "/score", "{\"events\":[]}", admin).andExpect(status().isOk());
        post("/api/teams/" + team + "/players", players(1), captain)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("últimas 2 fechas")));
        post("/api/teams/" + team + "/players", players(1), admin).andExpect(status().isOk());
    }

    private static String fixture(long home, long away, int round, LocalDateTime when) {
        return "{\"homeTeamId\":" + home + ",\"awayTeamId\":" + away + ",\"round\":" + round + ",\"scheduledAt\":\"" + when + ":00\"}";
    }
}
