package com.mjt.Football_Tournament_Management_System;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FollowApiTests extends ApiTestSupport {

    @Test
    void followIsIdempotentAndCountedPublicly() throws Exception {
        String admin = admin();
        long tournament = newTournament(admin);
        String fan = newUser();

        get("/api/public/tournaments/" + tournament + "/followers", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followers").value(0))
                .andExpect(jsonPath("$.following").value(false));

        put("/api/me/follows/" + tournament, null, fan).andExpect(jsonPath("$.followers").value(1));
        put("/api/me/follows/" + tournament, null, fan).andExpect(jsonPath("$.followers").value(1));
        get("/api/public/tournaments/" + tournament + "/followers", fan)
                .andExpect(jsonPath("$.following").value(true));
        get("/api/me/follows", fan)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(tournament));

        delete("/api/me/follows/" + tournament, fan)
                .andExpect(jsonPath("$.followers").value(0))
                .andExpect(jsonPath("$.following").value(false));
        get("/api/me/follows", fan).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void followersGetFixtureAndResultNewsUntilTheyMarkThemSeen() throws Exception {
        String admin = admin();
        long tournament = newTournament(admin);
        var category = newCategory(admin, tournament, "A");
        long zone = category.get("zones").get(0).get("id").asLong();
        long home = newTeam(admin), away = newTeam(admin);
        for (long team : new long[]{home, away}) {
            patch("/api/admin/registrations/" + enroll(admin, team, tournament) + "/zone", "{\"zoneId\":" + zone + "}", admin)
                    .andExpect(status().isOk());
        }
        String fan = newUser(), stranger = newUser();
        put("/api/me/follows/" + tournament, null, fan).andExpect(status().isOk());

        long match = read(post("/api/admin/fixtures/manual", "{\"zoneId\":" + zone + ",\"fixtures\":[{\"homeTeamId\":"
                + home + ",\"awayTeamId\":" + away + ",\"round\":3}]}", admin).andExpect(status().isOk())).get(0).get("id").asLong();
        patch("/api/admin/matches/" + match + "/score", "{\"homeGoals\":2,\"awayGoals\":1}", admin).andExpect(status().isOk());

        get("/api/me/notifications", fan)
                .andExpect(jsonPath("$.unread").value(2))
                .andExpect(jsonPath("$.items[0].type").value("RESULT"))
                .andExpect(jsonPath("$.items[0].message", startsWith("Fecha 3: Team ")))
                .andExpect(jsonPath("$.items[0].tournamentId").value(tournament))
                .andExpect(jsonPath("$.items[0].unread").value(true))
                .andExpect(jsonPath("$.items[1].type").value("FIXTURE"));
        get("/api/me/notifications", stranger).andExpect(jsonPath("$.unread").value(0))
                .andExpect(jsonPath("$.items.length()").value(0));

        post("/api/me/notifications/seen", null, fan).andExpect(status().isNoContent());
        get("/api/me/notifications", fan)
                .andExpect(jsonPath("$.unread").value(0))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].unread").value(false));
    }

    @Test
    void meEndpointsNeedASession() throws Exception {
        get("/api/me/follows", null).andExpect(status().isUnauthorized());
        get("/api/me/notifications", null).andExpect(status().isUnauthorized());
        put("/api/me/follows/999999", null, newUser()).andExpect(status().isNotFound());
    }
}
