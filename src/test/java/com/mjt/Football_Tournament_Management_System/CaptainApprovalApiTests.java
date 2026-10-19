package com.mjt.Football_Tournament_Management_System;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CaptainApprovalApiTests extends ApiTestSupport {

    @Test
    void aCaptainsTeamIsInReviewUntilApprovedWithItsRoster() throws Exception {
        String admin = admin(), captain = newUser();
        String name = "Nuevo " + UUID.randomUUID();
        long team = read(post("/api/teams/register", "{\"name\":\"" + name + "\"}", captain).andExpect(status().isOk())).get("id").asLong();
        post("/api/teams/" + team + "/players", "{\"players\":[{\"fullName\":\"Juan Pérez\",\"jerseyNo\":9}]}", captain)
                .andExpect(status().isOk());

        // In review: not public, cannot register, the captain and the admin see it
        get("/api/public/teams/" + team, null).andExpect(status().isNotFound());
        get("/api/public/teams/" + team, captain).andExpect(status().isOk());
        get("/api/teams", captain).andExpect(jsonPath("$[0].approved").value(false));
        get("/api/teams/" + team + "/roster", captain).andExpect(jsonPath("$[0].approved").value(false));
        long tournament = openTournament(admin);
        post("/api/teams/" + team + "/tournaments/" + tournament + "/register", "", captain).andExpect(status().isConflict());

        // One request for the team (its players go with it); captains cannot see the admin list
        get("/api/admin/requests", captain).andExpect(status().isForbidden());
        get("/api/admin/requests", admin)
                .andExpect(jsonPath("$[?(@.teamId == " + team + ")].type").value(org.hamcrest.Matchers.contains("TEAM")));
        post("/api/admin/requests/TEAM/" + team + "/approve", "", admin).andExpect(status().isNoContent());

        get("/api/public/teams/" + team, null).andExpect(jsonPath("$.name").value(name));
        get("/api/public/teams/" + team + "/players", null).andExpect(jsonPath("$[0].fullName").value("Juan Pérez"));
        get("/api/me/requests", captain)
                .andExpect(jsonPath("$.pending.length()").value(0))
                .andExpect(jsonPath("$.decisions[0].type").value("TEAM"))
                .andExpect(jsonPath("$.decisions[0].approved").value(true));
    }

    @Test
    void changesToAnApprovedTeamWaitForTheAdminExceptCosmeticOnes() throws Exception {
        String admin = admin(), captain = newUser();
        long team = newTeam(captain); // approved by the helper
        long kept = read(post("/api/teams/" + team + "/players", "{\"players\":[{\"fullName\":\"Titular\",\"jerseyNo\":1}]}", captain)).get(0).get("id").asLong();
        post("/api/admin/requests/teams/" + team + "/approve-all", "", admin).andExpect(status().isOk());
        String oldName = read(get("/api/public/teams/" + team, null)).get("name").asText();

        String newName = "Renombrado " + UUID.randomUUID();
        put("/api/teams/" + team, "{\"name\":\"" + newName + "\"}", captain).andExpect(jsonPath("$.name").value(oldName));
        long added = read(post("/api/teams/" + team + "/players", "{\"players\":[{\"fullName\":\"Suplente\",\"jerseyNo\":12}]}", captain)).get(0).get("id").asLong();
        delete("/api/teams/" + team + "/players/" + kept, captain).andExpect(status().isNoContent());
        long tournament = openTournament(admin);
        long reg = read(post("/api/teams/" + team + "/tournaments/" + tournament + "/register", "", captain)
                .andExpect(jsonPath("$.approved").value(false))).get("id").asLong();
        delete("/api/teams/" + team, captain).andExpect(status().isConflict()); // registered (even if pending)

        // Nothing changed publicly yet
        get("/api/public/teams/" + team, null).andExpect(jsonPath("$.name").value(oldName));
        get("/api/public/teams/" + team + "/players", null)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(kept));
        get("/api/public/tournaments/" + tournament + "/registrations", null).andExpect(jsonPath("$.length()").value(0));
        get("/api/me/requests", captain).andExpect(jsonPath("$.pending.length()").value(4));

        // Jersey numbers are cosmetic: changed right away
        patch("/api/teams/" + team + "/players/" + kept, "{\"jerseyNo\":10}", captain).andExpect(jsonPath("$.jerseyNo").value(10));

        post("/api/admin/requests/teams/" + team + "/approve-all", "", admin).andExpect(jsonPath("$.approved").value(4));
        get("/api/public/teams/" + team, null).andExpect(jsonPath("$.name").value(newName));
        get("/api/public/teams/" + team + "/players", null)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(added));
        get("/api/public/tournaments/" + tournament + "/registrations", null).andExpect(jsonPath("$[0].id").value(reg));
        post("/api/admin/requests/REGISTRATION/" + reg + "/approve", "", admin).andExpect(status().isConflict()); // already done
    }

    @Test
    void rejectionsDropTheChangeAndTellTheCaptainWhy() throws Exception {
        String admin = admin(), captain = newUser();
        long team = newTeam(captain);
        String oldName = read(get("/api/public/teams/" + team, null)).get("name").asText();
        put("/api/teams/" + team, "{\"name\":\"Otro " + UUID.randomUUID() + "\"}", captain);
        long player = read(post("/api/teams/" + team + "/players", "{\"players\":[{\"fullName\":\"Dudoso\",\"jerseyNo\":5}]}", captain)).get(0).get("id").asLong();

        post("/api/admin/requests/RENAME/" + team + "/reject", "{\"reason\":\"Nombre ofensivo\"}", admin).andExpect(status().isNoContent());
        post("/api/admin/requests/PLAYER_ADD/" + player + "/reject", "", admin).andExpect(status().isNoContent());
        get("/api/public/teams/" + team, null).andExpect(jsonPath("$.name").value(oldName));
        get("/api/teams/" + team + "/roster", captain).andExpect(jsonPath("$.length()").value(0));
        get("/api/me/requests", captain)
                .andExpect(jsonPath("$.decisions[*].reason").value(hasItem("Nombre ofensivo")))
                .andExpect(jsonPath("$.decisions[0].approved").value(false)) // newest first: the two rejections
                .andExpect(jsonPath("$.decisions[1].approved").value(false));

        // Rejecting a new team deletes it; the captain can create another one
        String other = newUser();
        long rejected = read(post("/api/teams/register", "{\"name\":\"Rechazado " + UUID.randomUUID() + "\"}", other)).get("id").asLong();
        post("/api/admin/requests/TEAM/" + rejected + "/reject", "{\"reason\":\"Duplicado\"}", admin).andExpect(status().isNoContent());
        get("/api/teams", other).andExpect(jsonPath("$.length()").value(0));
        get("/api/me/requests", other).andExpect(jsonPath("$.decisions[0].reason").value("Duplicado"));
        post("/api/teams/register", "{\"name\":\"Segundo intento " + UUID.randomUUID() + "\"}", other).andExpect(status().isOk());
    }

    @Test
    void deletingTheTeamIsAlwaysDecidedApart() throws Exception {
        String admin = admin(), captain = newUser();
        long team = newTeam(captain);
        delete("/api/teams/" + team, captain).andExpect(status().isNoContent());
        get("/api/teams", captain).andExpect(jsonPath("$[0].deletionRequested").value(true));
        post("/api/admin/requests/teams/" + team + "/approve-all", "", admin).andExpect(jsonPath("$.approved").value(0));
        post("/api/admin/requests/DELETE_TEAM/" + team + "/approve", "", admin).andExpect(status().isNoContent());
        get("/api/teams", captain).andExpect(jsonPath("$.length()").value(0));
    }

    private long openTournament(String admin) throws Exception {
        var today = LocalDate.now();
        long id = read(post("/api/admin/tournaments", "{\"name\":\"Apertura\",\"registrationStart\":\"" + today
                + "\",\"registrationEnd\":\"" + today.plusDays(5) + "\"}", admin)).get("id").asLong();
        patch("/api/admin/tournaments/" + id + "/status", "{\"status\":\"OPEN\",\"finishActive\":true}", admin).andExpect(status().isOk());
        return id;
    }
}
