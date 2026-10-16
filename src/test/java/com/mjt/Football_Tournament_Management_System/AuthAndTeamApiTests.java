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
        long tournamentId = newTournament(admin);
        long categoryA = newCategory(admin, tournamentId, "A").get("id").asLong();
        long categoryB = newCategory(admin, tournamentId, "B").get("id").asLong();
        long teamId = newTeam(captain);

        enroll(teamId, categoryA, captain).andExpect(status().isOk());
        enroll(teamId, categoryA, captain).andExpect(status().isConflict());
        // One zone per tournament: a second category of the same tournament is rejected too
        enroll(teamId, categoryB, captain).andExpect(status().isConflict());
    }

    @Test
    void roundRobinWithOddTeamCountSkipsByeSlots() throws Exception {
        String admin = login("admin", "admin123");
        JsonNode category = newCategory(admin, newTournament(admin), "A");
        long zoneId = category.get("zones").get(0).get("id").asLong();
        for (int i = 0; i < 3; i++) {
            String captain = register(uniqueUser());
            enroll(newTeam(captain), category.get("id").asLong(), captain).andExpect(status().isOk());
        }

        post("/api/admin/zones/" + zoneId + "/fixtures/round-robin?start=2026-11-01T10:00:00", "", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].zoneId").value(zoneId));
        mvc.perform(get("/api/public/zones/" + zoneId + "/fixtures"))
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void dashboardOnlyIncludesMatchesOfThatTournament() throws Exception {
        String admin = login("admin", "admin123");
        long tournamentA = newTournament(admin);
        JsonNode categoryB = newCategory(admin, newTournament(admin), "A");
        long tournamentB = categoryB.get("tournamentId").asLong();
        long home = newTeam(admin);
        long away = newTeam(admin);
        enroll(home, categoryB.get("id").asLong(), admin).andExpect(status().isOk());
        enroll(away, categoryB.get("id").asLong(), admin).andExpect(status().isOk());

        // 'away' plays as visitor in tournament B only
        post("/api/admin/fixtures/manual", manualFixture(categoryB.get("zones").get(0).get("id").asLong(), home, away), admin)
                .andExpect(status().isOk());

        mvc.perform(get("/api/teams/" + away + "/tournaments/" + tournamentA + "/dashboard")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fixtures.length()").value(0));
        mvc.perform(get("/api/teams/" + away + "/tournaments/" + tournamentB + "/dashboard")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.fixtures.length()").value(1));
    }

    @Test
    void publicRegistrationsDoNotExposeTeamContactData() throws Exception {
        String admin = login("admin", "admin123");
        String captain = register(uniqueUser());
        long tournamentId = newTournament(admin);
        long categoryId = newCategory(admin, tournamentId, "A").get("id").asLong();
        long teamId = read(post("/api/teams/register", "{\"name\":\"Team " + UUID.randomUUID()
                + "\",\"phone\":\"1155554444\",\"email\":\"capi@example.com\"}", captain)
                .andExpect(jsonPath("$.captainId").isNumber())
                .andExpect(jsonPath("$.captain").doesNotExist())).get("id").asLong();
        enroll(teamId, categoryId, captain).andExpect(status().isOk());

        mvc.perform(get("/api/public/tournaments/" + tournamentId + "/registrations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].team.id").value(teamId))
                .andExpect(jsonPath("$[0].categoryId").value(categoryId))
                .andExpect(jsonPath("$[0].team.phone").doesNotExist())
                .andExpect(jsonPath("$[0].team.email").doesNotExist());
    }

    // --- categories & zones ---

    @Test
    void newCategoryHasADefaultZoneAndReportsZoneCount() throws Exception {
        String admin = login("admin", "admin123");
        long tournamentId = newTournament(admin);
        JsonNode category = newCategory(admin, tournamentId, "A");
        org.junit.jupiter.api.Assertions.assertEquals(1, category.get("zoneCount").asInt());
        org.junit.jupiter.api.Assertions.assertEquals("Zona 1", category.get("zones").get(0).get("name").asText());

        post("/api/admin/categories/" + category.get("id").asLong() + "/zones", "{\"name\":\"Zona 2\"}", admin)
                .andExpect(status().isOk());

        mvc.perform(get("/api/public/tournaments/" + tournamentId + "/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("A"))
                .andExpect(jsonPath("$[0].zoneCount").value(2))
                .andExpect(jsonPath("$[0].zones[1].name").value("Zona 2"));
    }

    @Test
    void teamStartsInFirstZoneAndAdminCanMoveItWithinCategory() throws Exception {
        String admin = login("admin", "admin123");
        String captain = register(uniqueUser());
        long tournamentId = newTournament(admin);
        JsonNode categoryA = newCategory(admin, tournamentId, "A");
        JsonNode categoryB = newCategory(admin, tournamentId, "B");
        long zone1 = categoryA.get("zones").get(0).get("id").asLong();
        long zone2 = read(post("/api/admin/categories/" + categoryA.get("id").asLong() + "/zones",
                "{\"name\":\"Zona 2\"}", admin)).get("id").asLong();

        JsonNode reg = read(enroll(newTeam(captain), categoryA.get("id").asLong(), captain)
                .andExpect(jsonPath("$.zoneId").value(zone1)));
        String moveUrl = "/api/admin/registrations/" + reg.get("id").asLong() + "/zone";

        patch(moveUrl, "{\"zoneId\":" + zone2 + "}", captain).andExpect(status().isForbidden());
        patch(moveUrl, "{\"zoneId\":" + zone2 + "}", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zoneId").value(zone2));
        patch(moveUrl, "{\"zoneId\":" + categoryB.get("zones").get(0).get("id").asLong() + "}", admin)
                .andExpect(status().isConflict());
    }

    @Test
    void deletingZonesAndCategoriesIsGuarded() throws Exception {
        String admin = login("admin", "admin123");
        String captain = register(uniqueUser());
        long tournamentId = newTournament(admin);
        JsonNode used = newCategory(admin, tournamentId, "A");
        JsonNode empty = newCategory(admin, tournamentId, "B");
        enroll(newTeam(captain), used.get("id").asLong(), captain).andExpect(status().isOk());

        delete("/api/admin/zones/" + empty.get("zones").get(0).get("id").asLong(), admin)
                .andExpect(status().isConflict()); // last zone of the category
        delete("/api/admin/categories/" + used.get("id").asLong(), admin)
                .andExpect(status().isConflict()); // has a team
        delete("/api/admin/categories/" + empty.get("id").asLong(), admin)
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/public/tournaments/" + tournamentId + "/categories"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void manualFixtureRejectsTeamsOutsideTheZone() throws Exception {
        String admin = login("admin", "admin123");
        JsonNode category = newCategory(admin, newTournament(admin), "A");
        long zoneId = category.get("zones").get(0).get("id").asLong();
        long inZone = newTeam(admin);
        long outside = newTeam(admin);
        enroll(inZone, category.get("id").asLong(), admin).andExpect(status().isOk());

        post("/api/admin/fixtures/manual", manualFixture(zoneId, inZone, outside), admin)
                .andExpect(status().isConflict());
        post("/api/admin/fixtures/manual", manualFixture(zoneId, inZone, inZone), admin)
                .andExpect(status().isConflict());
    }

    // --- helpers ---

    private String tournamentJson() {
        var today = LocalDate.now();
        return "{\"name\":\"Apertura\",\"registrationStart\":\"" + today.minusDays(1)
                + "\",\"registrationEnd\":\"" + today.plusDays(10) + "\"}";
    }

    private long newTournament(String token) throws Exception {
        return read(post("/api/admin/tournaments", tournamentJson(), token).andExpect(status().isOk()))
                .get("id").asLong();
    }

    private JsonNode newCategory(String token, long tournamentId, String name) throws Exception {
        return read(post("/api/admin/tournaments/" + tournamentId + "/categories",
                "{\"name\":\"" + name + "\"}", token).andExpect(status().isOk()));
    }

    private long newTeam(String token) throws Exception {
        return read(post("/api/teams/register", "{\"name\":\"Team " + UUID.randomUUID() + "\"}", token)
                .andExpect(status().isOk())).get("id").asLong();
    }

    private ResultActions enroll(long teamId, long categoryId, String token) throws Exception {
        return post("/api/teams/" + teamId + "/categories/" + categoryId + "/register", "", token);
    }

    private String manualFixture(long zoneId, long home, long away) {
        return "{\"zoneId\":" + zoneId + ",\"fixtures\":[{\"homeTeamId\":" + home
                + ",\"awayTeamId\":" + away + ",\"scheduledAt\":\"2026-11-01T10:00:00\"}]}";
    }

    private ResultActions patch(String url, String body, String token) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(url)
                .contentType(MediaType.APPLICATION_JSON).content(body).header("Authorization", "Bearer " + token));
    }

    private ResultActions delete(String url, String token) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(url)
                .header("Authorization", "Bearer " + token));
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
