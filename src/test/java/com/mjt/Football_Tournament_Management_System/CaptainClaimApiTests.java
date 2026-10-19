package com.mjt.Football_Tournament_Management_System;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CaptainClaimApiTests extends ApiTestSupport {

    @Test
    void signingUpForATeamWithoutCaptainWaitsForTheAdmin() throws Exception {
        String admin = admin();
        long team = read(post("/api/teams/register", "{\"name\":\"Sin capitán " + UUID.randomUUID() + "\"}", admin)).get("id").asLong();
        get("/api/public/teams/unclaimed", null).andExpect(jsonPath("$[*].id").value(hasItem((int) team)));

        String user = "capi" + UUID.randomUUID().toString().substring(0, 6);
        String token = read(post("/api/auth/register", signUp(user, team), null).andExpect(status().isCreated())).get("token").asText();
        get("/api/public/teams/unclaimed", null).andExpect(jsonPath("$[*].id").value(not(hasItem((int) team))));
        get("/api/me/requests", token).andExpect(jsonPath("$.pending[0].type").value("CLAIM"));
        get("/api/teams", token).andExpect(jsonPath("$.length()").value(0)); // not their team yet

        // One request per team: someone else cannot ask for it (and their user is not created)
        String late = "late" + UUID.randomUUID().toString().substring(0, 6);
        post("/api/auth/register", signUp(late, team), null).andExpect(status().isConflict());
        post("/api/auth/login", "{\"username\":\"" + late + "\",\"password\":\"password123\"}", null).andExpect(status().isUnauthorized());

        post("/api/admin/requests/CLAIM/" + team + "/approve", "", admin).andExpect(status().isNoContent());
        get("/api/auth/me", token).andExpect(jsonPath("$.teamIds[0]").value(team));
        get("/api/teams", admin)
                .andExpect(jsonPath("$[?(@.id == " + team + ")].captainUsername").value(org.hamcrest.Matchers.contains(user)))
                .andExpect(jsonPath("$[?(@.id == " + team + ")].captainPending").value(org.hamcrest.Matchers.contains(false)));
        get("/api/me/requests", token).andExpect(jsonPath("$.decisions[0].type").value("CLAIM"));
    }

    @Test
    void aRejectedClaimLeavesTheUserFreeToAskAgainOrCreateATeam() throws Exception {
        String admin = admin();
        long team = read(post("/api/teams/register", "{\"name\":\"Pedido " + UUID.randomUUID() + "\"}", admin)).get("id").asLong();
        String token = read(post("/api/auth/register", signUp("capi" + UUID.randomUUID().toString().substring(0, 6), team), null)).get("token").asText();
        post("/api/me/claims", "{\"teamId\":" + team + "}", token).andExpect(status().isConflict()); // already asked

        post("/api/admin/requests/CLAIM/" + team + "/reject", "{\"reason\":\"No te conocemos\"}", admin).andExpect(status().isNoContent());
        get("/api/me/requests", token)
                .andExpect(jsonPath("$.pending.length()").value(0))
                .andExpect(jsonPath("$.decisions[0].reason").value("No te conocemos"));
        get("/api/public/teams/unclaimed", null).andExpect(jsonPath("$[*].id").value(hasItem((int) team)));

        // Ask again (from the app) or create a team instead
        post("/api/me/claims", "{\"teamId\":" + team + "}", token).andExpect(status().isNoContent());
        post("/api/admin/requests/CLAIM/" + team + "/reject", "", admin).andExpect(status().isNoContent());
        post("/api/teams/register", "{\"name\":\"Propio " + UUID.randomUUID() + "\"}", token).andExpect(status().isOk());

        // A team with a real captain cannot be claimed
        post("/api/me/claims", "{\"teamId\":" + newTeam(newUser()) + "}", newUser()).andExpect(status().isConflict());
    }

    private static String signUp(String user, long claimTeamId) {
        return "{\"username\":\"" + user + "\",\"password\":\"password123\",\"claimTeamId\":" + claimTeamId + "}";
    }
}
