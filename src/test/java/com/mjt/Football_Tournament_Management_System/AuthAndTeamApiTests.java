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
        long teamId = newTeam(captain);

        enroll(teamId, tournamentId, captain)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zoneId").doesNotExist()); // unassigned until the admin places it
        enroll(teamId, tournamentId, captain).andExpect(status().isConflict());
    }

    @Test
    void roundRobinWithOddTeamCountSkipsByeSlots() throws Exception {
        String admin = login("admin", "admin123");
        JsonNode category = newCategory(admin, newTournament(admin), "A");
        long zoneId = category.get("zones").get(0).get("id").asLong();
        for (int i = 0; i < 3; i++) {
            String captain = register(uniqueUser());
            enrollInZone(newTeam(captain), category, captain, admin);
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
        enrollInZone(home, categoryB, admin, admin);
        enrollInZone(away, categoryB, admin, admin);

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
        JsonNode category = newCategory(admin, tournamentId, "A");
        long categoryId = category.get("id").asLong();
        long teamId = read(post("/api/teams/register", "{\"name\":\"Team " + UUID.randomUUID()
                + "\",\"phone\":\"1155554444\",\"email\":\"capi@example.com\"}", captain)
                .andExpect(jsonPath("$.captainId").isNumber())
                .andExpect(jsonPath("$.captain").doesNotExist())).get("id").asLong();
        enrollInZone(teamId, category, captain, admin);

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
    void onlyAdminAssignsZoneWithinTheSameTournament() throws Exception {
        String admin = login("admin", "admin123");
        String captain = register(uniqueUser());
        long tournamentId = newTournament(admin);
        JsonNode categoryA = newCategory(admin, tournamentId, "A");
        JsonNode categoryB = newCategory(admin, tournamentId, "B");
        JsonNode otherTournament = newCategory(admin, newTournament(admin), "A");

        long regId = read(enroll(newTeam(captain), tournamentId, captain)).get("id").asLong();
        String url = "/api/admin/registrations/" + regId + "/zone";
        long zoneA = categoryA.get("zones").get(0).get("id").asLong();
        long zoneB = categoryB.get("zones").get(0).get("id").asLong();

        patch(url, "{\"zoneId\":" + zoneA + "}", captain).andExpect(status().isForbidden());
        patch(url, "{\"zoneId\":" + zoneA + "}", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").value(categoryA.get("id").asLong()));
        patch(url, "{\"zoneId\":" + zoneB + "}", admin) // another category, same tournament: allowed
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zoneId").value(zoneB));
        patch(url, "{\"zoneId\":" + otherTournament.get("zones").get(0).get("id").asLong() + "}", admin)
                .andExpect(status().isConflict());
    }

    @Test
    void deletingZonesAndCategoriesIsGuarded() throws Exception {
        String admin = login("admin", "admin123");
        String captain = register(uniqueUser());
        long tournamentId = newTournament(admin);
        JsonNode used = newCategory(admin, tournamentId, "A");
        JsonNode empty = newCategory(admin, tournamentId, "B");
        enrollInZone(newTeam(captain), used, captain, admin);

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
        enrollInZone(inZone, category, admin, admin);

        post("/api/admin/fixtures/manual", manualFixture(zoneId, inZone, outside), admin)
                .andExpect(status().isConflict());
        post("/api/admin/fixtures/manual", manualFixture(zoneId, inZone, inZone), admin)
                .andExpect(status().isConflict());
    }

    // --- standings ---

    @Test
    void walkoverGivesThreeGoalsAndPresenceOnlyToTheTeamThatShowedUp() throws Exception {
        String admin = login("admin", "admin123");
        long zone = newZone(admin);
        long shows = newTeamNamed(admin, "Show " + UUID.randomUUID());
        long absent = newTeamNamed(admin, "Absent " + UUID.randomUUID());
        assignToZone(admin, zone, shows, absent);

        long match = scheduleMatch(admin, zone, shows, absent);
        score(admin, match, "{\"homeGoals\":0,\"awayGoals\":5,\"awayPresent\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeGoals").value(3))
                .andExpect(jsonPath("$.awayGoals").value(0))
                .andExpect(jsonPath("$.awayPresent").value(false));

        mvc.perform(get("/api/public/zones/" + zone + "/standings"))
                .andExpect(jsonPath("$[0].team.id").value(shows))
                .andExpect(jsonPath("$[0].points").value(4)) // 3 win + 1 presence
                .andExpect(jsonPath("$[0].presencePoints").value(1))
                .andExpect(jsonPath("$[1].points").value(0))
                .andExpect(jsonPath("$[1].presencePoints").value(0))
                .andExpect(jsonPath("$[1].lost").value(1));
    }

    @Test
    void scoreWithoutGoalsWhenBothTeamsShowedUpIs400() throws Exception {
        String admin = login("admin", "admin123");
        long zone = newZone(admin);
        long a = newTeam(admin), b = newTeam(admin);
        assignToZone(admin, zone, a, b);
        score(admin, scheduleMatch(admin, zone, a, b), "{}").andExpect(status().isBadRequest());
    }

    @Test
    void headToHeadBreaksTieBeforeAlphabeticalOrder() throws Exception {
        String admin = login("admin", "admin123");
        long zone = newZone(admin);
        String suffix = UUID.randomUUID().toString();
        long z = newTeamNamed(admin, "ZZZ " + suffix);
        long a = newTeamNamed(admin, "AAA " + suffix);
        long c = newTeamNamed(admin, "CCC " + suffix);
        long d = newTeamNamed(admin, "DDD " + suffix);
        assignToZone(admin, zone, z, a, c, d);

        // Z and A both end with 5 pts, DIF 0, GF 1; Z won the match between them
        score(admin, scheduleMatch(admin, zone, z, a), "{\"homeGoals\":1,\"awayGoals\":0}");
        score(admin, scheduleMatch(admin, zone, c, z), "{\"homeGoals\":1,\"awayGoals\":0}");
        score(admin, scheduleMatch(admin, zone, a, d), "{\"homeGoals\":1,\"awayGoals\":0}");

        mvc.perform(get("/api/public/zones/" + zone + "/standings"))
                .andExpect(jsonPath("$[0].team.id").value(z))
                .andExpect(jsonPath("$[0].points").value(5))
                .andExpect(jsonPath("$[1].team.id").value(a))
                .andExpect(jsonPath("$[1].points").value(5))
                .andExpect(jsonPath("$[2].team.id").value(c))
                .andExpect(jsonPath("$[3].team.id").value(d));
    }

    @Test
    void fairPlayThenAlphabeticalBreakFullTies() throws Exception {
        String admin = login("admin", "admin123");
        String suffix = UUID.randomUUID().toString();

        // Draw with a yellow card for AAA: ZZZ goes first on fair play
        long zone1 = newZone(admin);
        long a1 = newTeamNamed(admin, "AAA1 " + suffix), z1 = newTeamNamed(admin, "ZZZ1 " + suffix);
        assignToZone(admin, zone1, a1, z1);
        score(admin, scheduleMatch(admin, zone1, a1, z1), "{\"homeGoals\":1,\"awayGoals\":1,\"homeYellowCards\":1}");
        mvc.perform(get("/api/public/zones/" + zone1 + "/standings"))
                .andExpect(jsonPath("$[0].team.id").value(z1))
                .andExpect(jsonPath("$[1].fairPlay").value(1));

        // Same draw without cards: alphabetical
        long zone2 = newZone(admin);
        long a2 = newTeamNamed(admin, "AAA2 " + suffix), z2 = newTeamNamed(admin, "ZZZ2 " + suffix);
        assignToZone(admin, zone2, z2, a2);
        score(admin, scheduleMatch(admin, zone2, z2, a2), "{\"homeGoals\":1,\"awayGoals\":1}");
        mvc.perform(get("/api/public/zones/" + zone2 + "/standings"))
                .andExpect(jsonPath("$[0].team.id").value(a2))
                .andExpect(jsonPath("$[0].points").value(2)); // draw + presence
    }

    // --- helpers ---

    /** New tournament with one category; returns the id of its single zone. */
    private long newZone(String admin) throws Exception {
        long tournamentId = newTournament(admin);
        long zoneId = newCategory(admin, tournamentId, "A").get("zones").get(0).get("id").asLong();
        zoneTournaments.put(zoneId, tournamentId);
        return zoneId;
    }

    private long newTeamNamed(String token, String name) throws Exception {
        return read(post("/api/teams/register", "{\"name\":\"" + name + "\"}", token)
                .andExpect(status().isOk())).get("id").asLong();
    }

    private void assignToZone(String admin, long zoneId, long... teamIds) throws Exception {
        long tournamentId = zoneTournaments.get(zoneId);
        for (long teamId : teamIds) {
            long regId = read(enroll(teamId, tournamentId, admin).andExpect(status().isOk())).get("id").asLong();
            patch("/api/admin/registrations/" + regId + "/zone", "{\"zoneId\":" + zoneId + "}", admin)
                    .andExpect(status().isOk());
        }
    }

    private final java.util.Map<Long, Long> zoneTournaments = new java.util.HashMap<>();

    private long scheduleMatch(String admin, long zoneId, long home, long away) throws Exception {
        return read(post("/api/admin/fixtures/manual", manualFixture(zoneId, home, away), admin)
                .andExpect(status().isOk())).get(0).get("id").asLong();
    }

    private ResultActions score(String admin, long matchId, String body) throws Exception {
        return patch("/api/admin/matches/" + matchId + "/score", body, admin);
    }

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

    private ResultActions enroll(long teamId, long tournamentId, String token) throws Exception {
        return post("/api/teams/" + teamId + "/tournaments/" + tournamentId + "/register", "", token);
    }

    /** Registers the team in the category's tournament and assigns it to the category's first zone. */
    private void enrollInZone(long teamId, JsonNode category, String token, String admin) throws Exception {
        long regId = read(enroll(teamId, category.get("tournamentId").asLong(), token)
                .andExpect(status().isOk())).get("id").asLong();
        patch("/api/admin/registrations/" + regId + "/zone",
                "{\"zoneId\":" + category.get("zones").get(0).get("id").asLong() + "}", admin)
                .andExpect(status().isOk());
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
