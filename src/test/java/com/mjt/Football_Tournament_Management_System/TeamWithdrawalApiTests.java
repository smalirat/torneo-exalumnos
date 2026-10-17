package com.mjt.Football_Tournament_Management_System;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TeamWithdrawalApiTests extends ApiTestSupport {

    @Test
    void withdrawnTeamLosesItsRemainingMatchesThreeNilAndCanComeBack() throws Exception {
        String admin = admin();
        long tournament = newTournament(admin);
        JsonNode category = newCategory(admin, tournament, "A");
        long zone = category.get("zones").get(0).get("id").asLong();
        long gone = newTeam(admin), b = newTeam(admin), c = newTeam(admin), d = newTeam(admin);
        long goneReg = 0;
        for (long team : new long[]{gone, b, c, d}) {
            long reg = enroll(admin, team, tournament);
            if (team == gone) goneReg = reg;
            patch("/api/admin/registrations/" + reg + "/zone", "{\"zoneId\":" + zone + "}", admin).andExpect(status().isOk());
        }
        var fixtures = read(post("/api/admin/fixtures/manual", "{\"zoneId\":" + zone + ",\"fixtures\":["
                + "{\"homeTeamId\":" + gone + ",\"awayTeamId\":" + b + ",\"round\":1},"
                + "{\"homeTeamId\":" + c + ",\"awayTeamId\":" + d + ",\"round\":1},"
                + "{\"homeTeamId\":" + c + ",\"awayTeamId\":" + gone + ",\"round\":2},"
                + "{\"homeTeamId\":" + b + ",\"awayTeamId\":" + d + ",\"round\":2}]}", admin));
        long played = fixtures.get(0).get("id").asLong(), later = fixtures.get(2).get("id").asLong();
        patch("/api/admin/matches/" + played + "/score", "{\"homeGoals\":1,\"awayGoals\":1,\"events\":[]}", admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.withdrawal").value(false));

        post("/api/admin/registrations/" + goneReg + "/withdraw", "{}", newUser()).andExpect(status().isForbidden());
        post("/api/admin/registrations/" + goneReg + "/withdraw", "{\"date\":\"2026-09-01\"}", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.withdrawn").value(true))
                .andExpect(jsonPath("$.withdrawnAt").value("2026-09-01"));
        post("/api/admin/registrations/" + goneReg + "/withdraw", "{}", admin).andExpect(status().isConflict());

        // The match it already played stays; the pending one is a 3-0 withdrawal walkover for the rival
        get("/api/public/zones/" + zone + "/fixtures", null)
                .andExpect(jsonPath("$[?(@.id == " + played + ")].withdrawal").value(false))
                .andExpect(jsonPath("$[?(@.id == " + played + ")].homeGoals").value(1))
                .andExpect(jsonPath("$[?(@.id == " + later + ")].withdrawal").value(true))
                .andExpect(jsonPath("$[?(@.id == " + later + ")].status").value("FINISHED"))
                .andExpect(jsonPath("$[?(@.id == " + later + ")].homeGoals").value(3))
                .andExpect(jsonPath("$[?(@.id == " + later + ")].awayPresent").value(false));
        get("/api/public/tournaments/" + tournament + "/registrations", null)
                .andExpect(jsonPath("$[?(@.team.id == " + gone + ")].withdrawn").value(true));

        // It comes back: round 2 was not played yet, so its match is pending again
        delete("/api/admin/registrations/" + goneReg + "/withdraw", admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.withdrawn").value(false));
        get("/api/public/zones/" + zone + "/fixtures", null)
                .andExpect(jsonPath("$[?(@.id == " + later + ")].status").value("SCHEDULED"))
                .andExpect(jsonPath("$[?(@.id == " + later + ")].withdrawal").value(false));
    }
}
