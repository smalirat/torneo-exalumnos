package com.mjt.Football_Tournament_Management_System;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthAndTeamApiTests {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void adminIsBootstrappedAndCanLogin() throws Exception {
        String token = login("admin", "admin123");
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void wrongPasswordReturns401() throws Exception {
        post("/api/auth/login", "{\"username\":\"admin\",\"password\":\"nope\"}", null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Usuario o contraseña incorrectos"));
    }

    @Test
    void registerValidatesAndRejectsDuplicates() throws Exception {
        post("/api/auth/register", "{\"username\":\"x\",\"password\":\"short\"}", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").exists())
                .andExpect(jsonPath("$.errors.password").exists());

        String user = uniqueUser();
        register(user);
        post("/api/auth/register", creds(user), null).andExpect(status().isConflict());
    }

    @Test
    void protectedEndpointsRequireTokenAndRole() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());

        String captain = register(uniqueUser());
        post("/api/admin/tournaments", tournamentJson(), captain).andExpect(status().isForbidden());
    }

    @Test
    void missingResourcesReturn404() throws Exception {
        mvc.perform(get("/api/public/tournaments/999999/fixtures"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Campeonato 999999 no encontrado"));
    }

    @Test
    void onlyTheCaptainOrAdminCanModifyATeam() throws Exception {
        String owner = register(uniqueUser());
        String other = register(uniqueUser());
        String admin = login("admin", "admin123");

        long teamId = read(post("/api/teams/register", "{\"name\":\"Team " + UUID.randomUUID() + "\"}", owner)
                .andExpect(status().isOk())).get("id").asLong();
        String players = "{\"players\":[{\"fullName\":\"Juan Pérez\",\"jerseyNo\":10}]}";

        post("/api/teams/" + teamId + "/players", players, other).andExpect(status().isForbidden());
        post("/api/teams/" + teamId + "/players", players, owner).andExpect(status().isOk());
        post("/api/teams/" + teamId + "/players", players, admin).andExpect(status().isOk());

        // Team owner shows up in /me, and the captain is never serialized
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("$.teamIds[0]").value(teamId));
    }

    @Test
    void registeringTeamTwiceInTournamentReturns409() throws Exception {
        String admin = login("admin", "admin123");
        String captain = register(uniqueUser());
        long tournamentId = read(post("/api/admin/tournaments", tournamentJson(), admin)
                .andExpect(status().isOk())).get("id").asLong();
        long teamId = read(post("/api/teams/register", "{\"name\":\"Team " + UUID.randomUUID() + "\"}", captain))
                .get("id").asLong();

        String url = "/api/teams/" + teamId + "/tournaments/" + tournamentId + "/register";
        post(url, "", captain).andExpect(status().isOk());
        post(url, "", captain).andExpect(status().isConflict());
    }

    @Test
    void roundRobinWithOddTeamCountSkipsByeSlots() throws Exception {
        String admin = login("admin", "admin123");
        long tournamentId = read(post("/api/admin/tournaments", tournamentJson(), admin)).get("id").asLong();
        for (int i = 0; i < 3; i++) {
            String captain = register(uniqueUser());
            long teamId = read(post("/api/teams/register", "{\"name\":\"Team " + UUID.randomUUID() + "\"}", captain))
                    .get("id").asLong();
            post("/api/teams/" + teamId + "/tournaments/" + tournamentId + "/register", "", captain)
                    .andExpect(status().isOk());
        }

        post("/api/admin/tournaments/" + tournamentId + "/fixtures/round-robin?start=2026-11-01T10:00:00", "", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[*].homeTeam.id").isNotEmpty());
    }

    // --- helpers ---

    private String tournamentJson() {
        var today = LocalDate.now();
        return "{\"name\":\"Apertura\",\"registrationStart\":\"" + today.minusDays(1)
                + "\",\"registrationEnd\":\"" + today.plusDays(10) + "\"}";
    }

    private String uniqueUser() { return "u" + UUID.randomUUID().toString().substring(0, 8); }

    private String creds(String user) {
        return "{\"username\":\"" + user + "\",\"password\":\"password123\"}";
    }

    private String register(String user) throws Exception {
        return read(post("/api/auth/register", creds(user), null).andExpect(status().isCreated()))
                .get("token").asText();
    }

    private String login(String user, String pass) throws Exception {
        return read(post("/api/auth/login",
                "{\"username\":\"" + user + "\",\"password\":\"" + pass + "\"}", null)
                .andExpect(status().isOk())).get("token").asText();
    }

    private ResultActions post(String url, String body, String token) throws Exception {
        var req = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) req.header("Authorization", "Bearer " + token);
        return mvc.perform(req);
    }

    private JsonNode read(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }
}
