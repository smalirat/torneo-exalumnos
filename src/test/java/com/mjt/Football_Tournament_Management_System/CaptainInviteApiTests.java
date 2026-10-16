package com.mjt.Football_Tournament_Management_System;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CaptainInviteApiTests extends ApiTestSupport {

    @Test
    void teamCreatedByAdminGetsAPendingCaptainThatIsClaimedWithTheInvitation() throws Exception {
        String admin = admin();
        String name = "Equipo " + UUID.randomUUID();
        long team = read(post("/api/teams/register", "{\"name\":\"" + name + "\"}", admin)
                .andExpect(status().isOk())).get("id").asLong();
        get("/api/teams", admin)
                .andExpect(jsonPath("$[?(@.id == " + team + ")].captainPending").value(true))
                .andExpect(jsonPath("$[?(@.id == " + team + ")].captainUsername").value(org.hamcrest.Matchers.contains((Object) null)));

        // Same link until it is used; not for captains or anonymous users
        String token = read(post("/api/admin/teams/" + team + "/invite", "", admin).andExpect(status().isOk())).get("token").asText();
        assertEquals(token, read(post("/api/admin/teams/" + team + "/invite", "", admin)).get("token").asText());
        post("/api/admin/teams/" + team + "/invite", "", newUser()).andExpect(status().isForbidden());

        get("/api/auth/invites/" + token, null).andExpect(status().isOk()).andExpect(jsonPath("$.teamName").value(name));
        String user = "capi" + UUID.randomUUID().toString().substring(0, 8);
        post("/api/auth/claim", claim(token, "pendiente-yo"), null).andExpect(status().isBadRequest());
        post("/api/auth/claim", claim(token, "admin"), null).andExpect(status().isConflict());
        String captain = read(post("/api/auth/claim", claim(token, user), null).andExpect(status().isOk())).get("token").asText();

        // Logged in as the captain of that team, with the password just chosen
        get("/api/auth/me", captain).andExpect(jsonPath("$.username").value(user)).andExpect(jsonPath("$.teamIds[0]").value(team));
        login(user, "password123");
        get("/api/teams", admin).andExpect(jsonPath("$[?(@.id == " + team + ")].captainUsername").value(user));

        // The link is used up
        get("/api/auth/invites/" + token, null).andExpect(status().isNotFound());
        post("/api/auth/claim", claim(token, user + "x"), null).andExpect(status().isNotFound());
        post("/api/admin/teams/" + team + "/invite", "", admin).andExpect(status().isConflict());
    }

    @Test
    void aCaptainWhoAlreadySignedUpAcceptsTheInvitationWithTheirAccount() throws Exception {
        String admin = admin();
        long team = read(post("/api/teams/register", "{\"name\":\"Equipo " + UUID.randomUUID() + "\"}", admin)).get("id").asLong();
        String token = read(post("/api/admin/teams/" + team + "/invite", "", admin)).get("token").asText();

        String captain = newUser();
        post("/api/me/invites/" + token + "/accept", "", null).andExpect(status().isUnauthorized());
        post("/api/me/invites/" + token + "/accept", "", captain)
                .andExpect(status().isOk()).andExpect(jsonPath("$.teamIds[0]").value(team));
        get("/api/teams", captain).andExpect(jsonPath("$[0].id").value(team));
        get("/api/auth/invites/" + token, null).andExpect(status().isNotFound());

        // Someone who already has a team cannot take a second one
        long other = read(post("/api/teams/register", "{\"name\":\"Otro " + UUID.randomUUID() + "\"}", admin)).get("id").asLong();
        String otherToken = read(post("/api/admin/teams/" + other + "/invite", "", admin)).get("token").asText();
        post("/api/me/invites/" + otherToken + "/accept", "", captain).andExpect(status().isConflict());
        post("/api/me/invites/" + otherToken + "/accept", "", admin).andExpect(status().isConflict());
    }

    @Test
    void signUpCannotUseThePendingPrefixAndCaptainsHaveASingleTeam() throws Exception {
        post("/api/auth/register", "{\"username\":\"pendiente-99\",\"password\":\"password123\"}", null)
                .andExpect(status().isBadRequest());
        String captain = newUser();
        post("/api/teams/register", "{\"name\":\"Uno " + UUID.randomUUID() + "\"}", captain).andExpect(status().isOk());
        post("/api/teams/register", "{\"name\":\"Dos " + UUID.randomUUID() + "\"}", captain).andExpect(status().isConflict());
    }

    private static String claim(String token, String username) {
        return "{\"token\":\"" + token + "\",\"username\":\"" + username + "\",\"password\":\"password123\"}";
    }
}
