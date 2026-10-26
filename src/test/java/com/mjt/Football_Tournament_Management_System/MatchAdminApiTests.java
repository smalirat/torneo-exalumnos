package com.mjt.Football_Tournament_Management_System;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Suspended matches, swapping times, protests */
class MatchAdminApiTests extends ApiTestSupport {

    private long[] zoneWithTwo(String admin, long home, long away) throws Exception {
        long t = newTournament(admin);
        long zone = newCategory(admin, t, "A").get("zones").get(0).get("id").asLong();
        for (long x : new long[]{home, away}) post("/api/admin/zones/" + zone + "/teams", "{\"teamId\":" + x + "}", admin);
        return new long[]{t, zone};
    }

    @Test
    void aSuspendedMatchKeepsItsScoreAndDoesNotCountUntilFinished() throws Exception {
        String admin = admin();
        long a = newTeam(admin), b = newTeam(admin);
        long zone = zoneWithTwo(admin, a, b)[1];
        long match = read(post("/api/admin/fixtures/manual", "{\"zoneId\":" + zone + ",\"fixtures\":[{\"homeTeamId\":" + a
                + ",\"awayTeamId\":" + b + ",\"round\":1}]}", admin)).get(0).get("id").asLong();

        patch("/api/admin/matches/" + match + "/suspension", "{\"homeGoals\":1,\"awayGoals\":0,\"remainingMinutes\":25}", newUser())
                .andExpect(status().isForbidden());
        patch("/api/admin/matches/" + match + "/suspension", "{\"homeGoals\":1,\"awayGoals\":0,\"remainingMinutes\":25}", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suspended").value(true))
                .andExpect(jsonPath("$.remainingMinutes").value(25))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.homeGoals").value(1));
        get("/api/public/zones/" + zone + "/standings", null).andExpect(jsonPath("$[0].played").value(0));

        // The minutes left are played: final result
        patch("/api/admin/matches/" + match + "/score", "{\"homeGoals\":2,\"awayGoals\":1,\"events\":[]}", admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.suspended").value(false)).andExpect(jsonPath("$.remainingMinutes").isEmpty());
        get("/api/public/zones/" + zone + "/standings", null).andExpect(jsonPath("$[0].played").value(1));
        patch("/api/admin/matches/" + match + "/suspension", "{\"homeGoals\":1,\"awayGoals\":0,\"remainingMinutes\":25}", admin)
                .andExpect(status().isConflict());
    }

    @Test
    void twoMatchesSwapTheirTimes() throws Exception {
        String admin = admin();
        long a = newTeam(admin), b = newTeam(admin), c = newTeam(admin), d = newTeam(admin);
        long t = newTournament(admin);
        long zone = newCategory(admin, t, "A").get("zones").get(0).get("id").asLong();
        for (long x : new long[]{a, b, c, d}) post("/api/admin/zones/" + zone + "/teams", "{\"teamId\":" + x + "}", admin);
        var ms = read(post("/api/admin/fixtures/manual", "{\"zoneId\":" + zone + ",\"fixtures\":["
                + "{\"homeTeamId\":" + a + ",\"awayTeamId\":" + b + ",\"round\":1,\"scheduledAt\":\"2026-11-01T09:00:00\",\"venue\":\"Anexo\",\"pitch\":\"1\"},"
                + "{\"homeTeamId\":" + c + ",\"awayTeamId\":" + d + ",\"round\":1,\"scheduledAt\":\"2026-11-01T11:20:00\",\"venue\":\"Siberia\",\"pitch\":\"3\"}]}", admin));
        long first = ms.get(0).get("id").asLong(), second = ms.get(1).get("id").asLong();
        post("/api/admin/matches/" + first + "/swap-schedule", "{\"otherMatchId\":" + second + "}", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].scheduledAt").value("2026-11-01T11:20:00"))
                .andExpect(jsonPath("$[0].venue").value("Siberia"))
                .andExpect(jsonPath("$[1].scheduledAt").value("2026-11-01T09:00:00"))
                .andExpect(jsonPath("$[1].pitch").value("1"));
    }

    @Test
    void captainsProtestPlayedMatchesAndTheAdminResolves() throws Exception {
        String admin = admin();
        String captain = newUser(), outsider = newUser();
        long mine = newTeam(captain), rival = newTeam(admin);
        newTeam(outsider);
        long zone = zoneWithTwo(admin, mine, rival)[1];
        long match = read(post("/api/admin/fixtures/manual", "{\"zoneId\":" + zone + ",\"fixtures\":[{\"homeTeamId\":" + mine
                + ",\"awayTeamId\":" + rival + ",\"round\":1}]}", admin)).get(0).get("id").asLong();
        String body = "{\"matchId\":" + match + ",\"facts\":\"Jugó un inhabilitado\",\"request\":\"Que se nos dé el partido\"}";

        post("/api/me/protests", body, captain).andExpect(status().isConflict()); // not played yet
        patch("/api/admin/matches/" + match + "/score", "{\"homeGoals\":0,\"awayGoals\":1,\"events\":[]}", admin);
        post("/api/me/protests", body, outsider).andExpect(status().isForbidden());
        long id = read(post("/api/me/protests", body, captain).andExpect(status().isCreated())).get("id").asLong();
        post("/api/me/protests", body, captain).andExpect(status().isConflict()); // one open at a time
        get("/api/admin/requests/count", admin).andExpect(jsonPath("$.count").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        get("/api/admin/protests", admin).andExpect(jsonPath("$[?(@.id == " + id + ")].status").value("OPEN"));
        post("/api/admin/protests/" + id + "/resolve", "{\"resolution\":\"Rechazada: el jugador estaba habilitado\"}", captain)
                .andExpect(status().isForbidden());
        post("/api/admin/protests/" + id + "/resolve", "{\"resolution\":\"Rechazada: el jugador estaba habilitado\"}", admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESOLVED"));
        get("/api/me/protests", captain)
                .andExpect(jsonPath("$[0].resolution").value("Rechazada: el jugador estaba habilitado"))
                .andExpect(jsonPath("$[0].match").value(org.hamcrest.Matchers.containsString(" vs ")));
    }
}
