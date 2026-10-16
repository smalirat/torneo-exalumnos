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

    @Test
    void teamListIsScopedToTheCaptainAndPlayersCanBeRemovedUntilTheyHaveEvents() throws Exception {
        String admin = login("admin", "admin123");
        String captain = register(uniqueUser());
        long mine = newTeam(captain);
        newTeam(register(uniqueUser())); // someone else's team

        mvc.perform(get("/api/teams").header("Authorization", "Bearer " + captain))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(mine))
                .andExpect(jsonPath("$[0].playerCount").value(0));

        long zone = newZone(admin);
        long rival = newTeam(admin);
        long scorer = newPlayer(captain, mine, "Goleador");
        long bench = newPlayer(captain, mine, "Suplente");
        assignToZone(admin, zone, mine, rival);
        score(admin, scheduleMatch(admin, zone, mine, rival), "{\"events\":[" + event(scorer, "GOAL") + "]}");

        delete("/api/teams/" + mine + "/players/" + scorer, captain).andExpect(status().isConflict());
        delete("/api/teams/" + mine + "/players/" + bench, register(uniqueUser())).andExpect(status().isForbidden());
        delete("/api/teams/" + mine + "/players/" + bench, captain).andExpect(status().isNoContent());
        mvc.perform(get("/api/teams").header("Authorization", "Bearer " + captain))
                .andExpect(jsonPath("$[0].playerCount").value(1));
    }

    @Test
    void tournamentCanBeEditedAndDeletedOnlyWhileEmpty() throws Exception {
        String admin = login("admin", "admin123");
        long empty = newTournament(admin);
        newCategory(admin, empty, "A");
        String body = "{\"name\":\"Renombrado\",\"registrationStart\":\"2026-01-01\","
                + "\"registrationEnd\":\"2026-12-31\",\"status\":\"LIVE\"}";
        put("/api/admin/tournaments/" + empty, body, admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renombrado"))
                .andExpect(jsonPath("$.status").value("LIVE"));
        delete("/api/admin/tournaments/" + empty, admin).andExpect(status().isNoContent());

        long used = newTournament(admin);
        enroll(newTeam(admin), used, admin).andExpect(status().isOk());
        delete("/api/admin/tournaments/" + used, admin).andExpect(status().isConflict());
    }

    @Test
    void teamCanBeRenamedByOwnerAndDeletedWhileNotRegistered() throws Exception {
        String captain = register(uniqueUser());
        long team = newTeam(captain);
        String newName = "Nuevo " + UUID.randomUUID();
        put("/api/teams/" + team, "{\"name\":\"" + newName + "\"}", register(uniqueUser()))
                .andExpect(status().isForbidden());
        put("/api/teams/" + team, "{\"name\":\"" + newName + "\"}", captain)
                .andExpect(jsonPath("$.name").value(newName));

        long registered = newTeam(captain);
        enroll(registered, newTournament(login("admin", "admin123")), captain).andExpect(status().isOk());
        delete("/api/teams/" + registered, captain).andExpect(status().isConflict());
        newPlayer(captain, team, "Suplente");
        delete("/api/teams/" + team, captain).andExpect(status().isNoContent());
    }

    @Test
    void deletingAMatchRemovesItsEventsAndUndatedMatchesComeLast() throws Exception {
        String admin = login("admin", "admin123");
        long zone = newZone(admin);
        long a = newTeam(admin), b = newTeam(admin);
        long scorer = newPlayer(admin, a, "Goleador");
        assignToZone(admin, zone, a, b);
        long undated = read(post("/api/admin/fixtures/manual", "{\"zoneId\":" + zone
                + ",\"fixtures\":[{\"homeTeamId\":" + a + ",\"awayTeamId\":" + b + "}]}", admin)).get(0).get("id").asLong();
        long dated = scheduleMatch(admin, zone, b, a);
        mvc.perform(get("/api/public/zones/" + zone + "/fixtures"))
                .andExpect(jsonPath("$[0].id").value(dated))
                .andExpect(jsonPath("$[1].id").value(undated));

        score(admin, dated, "{\"events\":[" + event(scorer, "GOAL") + "]}");
        delete("/api/admin/matches/" + dated, admin).andExpect(status().isNoContent());
        mvc.perform(get("/api/public/zones/" + zone + "/fixtures")).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/public/tournaments/" + zoneTournaments.get(zone) + "/scorers"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void teamRegistrationsShowTournamentAndZoneOnceAssigned() throws Exception {
        String admin = login("admin", "admin123");
        long zone = newZone(admin);
        long team = newTeam(admin);
        long other = newTournament(admin);
        enroll(team, other, admin).andExpect(status().isOk()); // unassigned
        assignToZone(admin, zone, team);

        mvc.perform(get("/api/public/teams/" + team + "/registrations"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].tournament.id").value(zoneTournaments.get(zone)))
                .andExpect(jsonPath("$[0].zoneId").value(zone))
                .andExpect(jsonPath("$[0].categoryName").value("A"))
                .andExpect(jsonPath("$[0].zoneCount").value(1))
                .andExpect(jsonPath("$[1].tournament.id").value(other))
                .andExpect(jsonPath("$[1].zoneId").doesNotExist());
    }

    // --- seasons & match details ---

    @Test
    void tournamentsBelongToASeasonWithOneAperturaAndOneClausura() throws Exception {
        String admin = login("admin", "admin123");
        int year = 2100 - (int) (Math.random() * 150); // unique enough across tests in the shared DB
        String body = "{\"name\":\"T\",\"registrationStart\":\"2026-01-01\",\"registrationEnd\":\"2026-12-31\","
                + "\"seasonYear\":" + year + ",\"stage\":\"%s\"}";
        post("/api/admin/tournaments", body.formatted("APERTURA"), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seasonYear").value(year))
                .andExpect(jsonPath("$.stage").value("APERTURA"));
        post("/api/admin/tournaments", body.formatted("CLAUSURA"), admin).andExpect(status().isOk());
        post("/api/admin/tournaments", body.formatted("APERTURA"), admin).andExpect(status().isConflict());
        post("/api/admin/tournaments", "{\"name\":\"T\",\"registrationStart\":\"2026-01-01\","
                + "\"registrationEnd\":\"2026-12-31\",\"stage\":\"APERTURA\"}", admin).andExpect(status().isBadRequest());

        mvc.perform(get("/api/public/seasons"))
                .andExpect(jsonPath("$[?(@.year == " + year + ")].tournaments.length()").value(2));
    }

    @Test
    void interzonalMatchCountsInTheTableOfEachTeamsZone() throws Exception {
        String admin = login("admin", "admin123");
        long tournament = newTournament(admin);
        JsonNode category = newCategory(admin, tournament, "A");
        long zone1 = zoneOf(category, tournament);
        long zone2 = read(post("/api/admin/categories/" + category.get("id").asLong() + "/zones",
                "{\"name\":\"Zona 2\"}", admin)).get("id").asLong();
        zoneTournaments.put(zone2, tournament);
        long a = newTeam(admin), b = newTeam(admin), c = newTeam(admin);
        long scorer = newPlayer(admin, a, "Goleador");
        assignToZone(admin, zone1, a, b);
        assignToZone(admin, zone2, c);

        JsonNode match = read(post("/api/admin/fixtures/manual", "{\"tournamentId\":" + tournament
                + ",\"fixtures\":[{\"homeTeamId\":" + a + ",\"awayTeamId\":" + c + ",\"round\":11,"
                + "\"roundLabel\":\"Interzonal\",\"venue\":\"Siberia\",\"pitch\":\"3\"}]}", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].zoneId").doesNotExist())
                .andExpect(jsonPath("$[0].roundLabel").value("Interzonal"))).get(0);
        score(admin, match.get("id").asLong(), "{\"events\":[" + event(scorer, "GOAL") + "]}");

        mvc.perform(get("/api/public/zones/" + zone1 + "/standings"))
                .andExpect(jsonPath("$[0].team.id").value(a))
                .andExpect(jsonPath("$[0].played").value(1));
        mvc.perform(get("/api/public/zones/" + zone2 + "/standings"))
                .andExpect(jsonPath("$[0].team.id").value(c))
                .andExpect(jsonPath("$[0].lost").value(1));
        mvc.perform(get("/api/public/zones/" + zone2 + "/fixtures")).andExpect(jsonPath("$.length()").value(1));

        // Interzonal teams must be placed in a zone of the tournament
        post("/api/admin/fixtures/manual", "{\"tournamentId\":" + tournament + ",\"fixtures\":[{\"homeTeamId\":"
                + a + ",\"awayTeamId\":" + newTeam(admin) + "}]}", admin).andExpect(status().isConflict());
    }

    @Test
    void roundRobinNumbersRoundsAndMatchDetailsCanBeEdited() throws Exception {
        String admin = login("admin", "admin123");
        long zone = newZone(admin);
        long a = newTeam(admin), b = newTeam(admin), c = newTeam(admin), d = newTeam(admin);
        assignToZone(admin, zone, a, b, c, d);
        JsonNode fixtures = read(post("/api/admin/zones/" + zone + "/fixtures/round-robin?start=2026-11-01T10:00:00", "", admin));
        org.junit.jupiter.api.Assertions.assertEquals(6, fixtures.size());
        org.junit.jupiter.api.Assertions.assertEquals(1, fixtures.get(0).get("round").asInt());
        org.junit.jupiter.api.Assertions.assertEquals(3, fixtures.get(5).get("round").asInt());

        long id = fixtures.get(0).get("id").asLong();
        patch("/api/admin/matches/" + id, "{\"scheduledAt\":\"2026-11-02T09:00:00\",\"round\":1,\"venue\":\"Anexo\","
                + "\"pitch\":\"2\",\"notes\":\"Se juega con pelota nueva\"}", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.venue").value("Anexo"))
                .andExpect(jsonPath("$.notes").value("Se juega con pelota nueva"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    // --- standings ---

    @Test
    void walkoverGivesThreeGoalsAndPresenceOnlyToTheTeamThatShowedUp() throws Exception {
        String admin = login("admin", "admin123");
        long zone = newZone(admin);
        long shows = newTeamNamed(admin, "Show " + UUID.randomUUID());
        long absent = newTeamNamed(admin, "Absent " + UUID.randomUUID());
        long showsPlayer = newPlayer(admin, shows, "Juan Pérez");
        assignToZone(admin, zone, shows, absent);

        long match = scheduleMatch(admin, zone, shows, absent);
        score(admin, match, "{\"awayPresent\":false,\"events\":[" + event(showsPlayer, "GOAL") + "]}")
                .andExpect(status().isBadRequest()); // no events in a walkover
        score(admin, match, "{\"awayPresent\":false}")
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
        // Walkover goals belong to the team only, not to any scorer
        mvc.perform(get("/api/public/tournaments/" + zoneTournaments.get(zone) + "/scorers"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void eventsFromPlayersOfOtherTeamsAre400() throws Exception {
        String admin = login("admin", "admin123");
        long zone = newZone(admin);
        long a = newTeam(admin), b = newTeam(admin), outsider = newTeam(admin);
        long outsiderPlayer = newPlayer(admin, outsider, "Intruso");
        assignToZone(admin, zone, a, b);
        score(admin, scheduleMatch(admin, zone, a, b), "{\"events\":[" + event(outsiderPlayer, "GOAL") + "]}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void scoreIsDerivedFromEventsAndOwnGoalsCountForTheRival() throws Exception {
        String admin = login("admin", "admin123");
        long zone = newZone(admin);
        long home = newTeam(admin), away = newTeam(admin);
        long striker = newPlayer(admin, home, "Goleador");
        long defender = newPlayer(admin, away, "Defensor");
        assignToZone(admin, zone, home, away);
        long match = scheduleMatch(admin, zone, home, away);

        String events = String.join(",", event(striker, "GOAL"), event(striker, "GOAL"),
                event(defender, "OWN_GOAL"), event(defender, "YELLOW_CARD"));
        score(admin, match, "{\"events\":[" + events + "]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeGoals").value(3))
                .andExpect(jsonPath("$.awayGoals").value(0));
        mvc.perform(get("/api/public/matches/" + match + "/events"))
                .andExpect(jsonPath("$.length()").value(4));

        // Re-scoring replaces the previous events
        score(admin, match, "{\"events\":[" + event(striker, "GOAL") + "]}")
                .andExpect(jsonPath("$.homeGoals").value(1));
        mvc.perform(get("/api/public/matches/" + match + "/events"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void tournamentScorersAndCardsRankings() throws Exception {
        String admin = login("admin", "admin123");
        long zone = newZone(admin);
        long home = newTeam(admin), away = newTeam(admin);
        long top = newPlayer(admin, home, "Zapata");
        long second = newPlayer(admin, away, "Acosta");
        long rough = newPlayer(admin, away, "Brusco");
        assignToZone(admin, zone, home, away);

        score(admin, scheduleMatch(admin, zone, home, away), "{\"events\":[" + String.join(",",
                event(top, "GOAL"), event(top, "GOAL"), event(second, "GOAL"), event(second, "OWN_GOAL"),
                event(second, "YELLOW_CARD"), event(rough, "YELLOW_CARD"), event(rough, "RED_CARD")) + "]}")
                .andExpect(status().isOk());

        long tournament = zoneTournaments.get(zone);
        mvc.perform(get("/api/public/tournaments/" + tournament + "/scorers"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].player.id").value(top))
                .andExpect(jsonPath("$[0].goals").value(2))
                .andExpect(jsonPath("$[1].player.id").value(second))
                .andExpect(jsonPath("$[1].goals").value(1)); // own goal not counted
        mvc.perform(get("/api/public/tournaments/" + tournament + "/cards"))
                .andExpect(jsonPath("$[0].player.id").value(rough))
                .andExpect(jsonPath("$[0].redCards").value(1))
                .andExpect(jsonPath("$[1].player.id").value(second))
                .andExpect(jsonPath("$[1].yellowCards").value(1));
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
        winOneNil(admin, scheduleMatch(admin, zone, z, a), z);
        winOneNil(admin, scheduleMatch(admin, zone, c, z), c);
        winOneNil(admin, scheduleMatch(admin, zone, a, d), a);

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

        // 1-1 draw with a yellow card for AAA: ZZZ goes first on fair play
        long zone1 = newZone(admin);
        long a1 = newTeamNamed(admin, "AAA1 " + suffix), z1 = newTeamNamed(admin, "ZZZ1 " + suffix);
        long pa1 = newPlayer(admin, a1, "Jugador A1"), pz1 = newPlayer(admin, z1, "Jugador Z1");
        assignToZone(admin, zone1, a1, z1);
        score(admin, scheduleMatch(admin, zone1, a1, z1), "{\"events\":[" + String.join(",",
                event(pa1, "GOAL"), event(pz1, "GOAL"), event(pa1, "YELLOW_CARD")) + "]}");
        mvc.perform(get("/api/public/zones/" + zone1 + "/standings"))
                .andExpect(jsonPath("$[0].team.id").value(z1))
                .andExpect(jsonPath("$[1].fairPlay").value(1));

        // Same draw without cards: alphabetical
        long zone2 = newZone(admin);
        long a2 = newTeamNamed(admin, "AAA2 " + suffix), z2 = newTeamNamed(admin, "ZZZ2 " + suffix);
        long pa2 = newPlayer(admin, a2, "Jugador A2"), pz2 = newPlayer(admin, z2, "Jugador Z2");
        assignToZone(admin, zone2, z2, a2);
        score(admin, scheduleMatch(admin, zone2, z2, a2), "{\"events\":[" + String.join(",",
                event(pa2, "GOAL"), event(pz2, "GOAL")) + "]}");
        mvc.perform(get("/api/public/zones/" + zone2 + "/standings"))
                .andExpect(jsonPath("$[0].team.id").value(a2))
                .andExpect(jsonPath("$[0].points").value(2)); // draw + presence
    }

    // --- promotions & finals ---

    @Test
    void promotionPicksTeamsByPositionAcrossCategoriesAndStaysOutOfTables() throws Exception {
        String admin = login("admin", "admin123");
        long tournament = newTournament(admin);
        long zoneA = zoneOf(newCategory(admin, tournament, "A"), tournament);
        long zoneB = zoneOf(newCategory(admin, tournament, "B"), tournament);
        String s = UUID.randomUUID().toString();
        long a1 = newTeamNamed(admin, "A1 " + s), a2 = newTeamNamed(admin, "A2 " + s), a3 = newTeamNamed(admin, "A3 " + s);
        long b1 = newTeamNamed(admin, "B1 " + s), b2 = newTeamNamed(admin, "B2 " + s);
        assignToZone(admin, zoneA, a1, a2, a3);
        assignToZone(admin, zoneB, b1, b2);
        // A: a1 > a2 > a3 ; B: b1 > b2
        winOneNil(admin, scheduleMatch(admin, zoneA, a1, a2), a1);
        winOneNil(admin, scheduleMatch(admin, zoneA, a2, a3), a2);
        winOneNil(admin, scheduleMatch(admin, zoneB, b1, b2), b1);

        long promo = read(post("/api/admin/tournaments/" + tournament + "/special-matches",
                specialMatch("PROMOTION", slot(zoneA, 3), slot(zoneB, 1)), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("PROMOTION"))
                .andExpect(jsonPath("$.zoneId").doesNotExist())
                .andExpect(jsonPath("$.homeTeam.id").value(a3))
                .andExpect(jsonPath("$.awayTeam.id").value(b1))
                .andExpect(jsonPath("$.homeSource.position").value(3))
                .andExpect(jsonPath("$.homeSource.categoryName").value("A"))
                .andExpect(jsonPath("$.homeSource.zoneCount").value(1))
                .andExpect(jsonPath("$.awaySource.categoryName").value("B"))).get("id").asLong();

        winOneNil(admin, promo, a3);
        mvc.perform(get("/api/public/zones/" + zoneA + "/standings"))
                .andExpect(jsonPath("$[2].team.id").value(a3))
                .andExpect(jsonPath("$[2].played").value(1)); // promotion not counted
        mvc.perform(get("/api/public/tournaments/" + tournament + "/special-matches"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("FINISHED"));
    }

    @Test
    void finalBetweenWinnersOfTwoTournaments() throws Exception {
        String admin = login("admin", "admin123");
        long apertura = newZone(admin), clausura = newZone(admin);
        long ap1 = newTeam(admin), ap2 = newTeam(admin), cl1 = newTeam(admin), cl2 = newTeam(admin);
        assignToZone(admin, apertura, ap1, ap2);
        assignToZone(admin, clausura, cl1, cl2);
        winOneNil(admin, scheduleMatch(admin, apertura, ap2, ap1), ap2);
        winOneNil(admin, scheduleMatch(admin, clausura, cl1, cl2), cl1);

        post("/api/admin/tournaments/" + zoneTournaments.get(clausura) + "/special-matches",
                specialMatch("FINAL", slot(apertura, 1), slot(clausura, 1)), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeTeam.id").value(ap2))
                .andExpect(jsonPath("$.awayTeam.id").value(cl1))
                .andExpect(jsonPath("$.homeSource.tournamentId").value(zoneTournaments.get(apertura)));
    }

    @Test
    void resolveRecomputesUntilTheMatchIsPlayed() throws Exception {
        String admin = login("admin", "admin123");
        long zone = newZone(admin);
        String s = UUID.randomUUID().toString();
        long first = newTeamNamed(admin, "AAA " + s), second = newTeamNamed(admin, "BBB " + s);
        long fixed = newTeam(admin);
        assignToZone(admin, zone, first, second);

        // No results yet: 1st place is AAA (alphabetical); the other side is a fixed team
        long match = read(post("/api/admin/tournaments/" + zoneTournaments.get(zone) + "/special-matches",
                specialMatch("FINAL", slot(zone, 1), "{\"teamId\":" + fixed + "}"), admin)
                .andExpect(jsonPath("$.homeTeam.id").value(first))
                .andExpect(jsonPath("$.awaySource").doesNotExist())).get("id").asLong();

        winOneNil(admin, scheduleMatch(admin, zone, first, second), second);
        post("/api/admin/matches/" + match + "/resolve", "", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeTeam.id").value(second))
                .andExpect(jsonPath("$.awayTeam.id").value(fixed));

        winOneNil(admin, match, second);
        post("/api/admin/matches/" + match + "/resolve", "", admin).andExpect(status().isConflict());
    }

    @Test
    void drawnFinalIsDecidedOnPenalties() throws Exception {
        String admin = login("admin", "admin123");
        long zone = newZone(admin);
        long a = newTeam(admin), b = newTeam(admin);
        long pa = newPlayer(admin, a, "Jugador A"), pb = newPlayer(admin, b, "Jugador B");
        assignToZone(admin, zone, a, b);
        long fin = read(post("/api/admin/tournaments/" + zoneTournaments.get(zone) + "/special-matches",
                specialMatch("FINAL", "{\"teamId\":" + a + "}", "{\"teamId\":" + b + "}"), admin)).get("id").asLong();
        String draw = "\"events\":[" + event(pa, "GOAL") + "," + event(pb, "GOAL") + "]";

        score(admin, fin, "{" + draw + "}").andExpect(status().isBadRequest()); // penalties missing
        score(admin, fin, "{" + draw + ",\"homePenalties\":4,\"awayPenalties\":4}").andExpect(status().isBadRequest());
        score(admin, fin, "{" + draw + ",\"homePenalties\":3,\"awayPenalties\":4}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeGoals").value(1))
                .andExpect(jsonPath("$.awayPenalties").value(4))
                .andExpect(jsonPath("$.winner.id").value(b));

        // A final won in regular time cannot carry penalties
        score(admin, fin, "{\"events\":[" + event(pa, "GOAL") + "],\"homePenalties\":1,\"awayPenalties\":0}")
                .andExpect(status().isBadRequest());
        score(admin, fin, "{\"events\":[" + event(pa, "GOAL") + "]}")
                .andExpect(jsonPath("$.winner.id").value(a))
                .andExpect(jsonPath("$.homePenalties").doesNotExist());
    }

    @Test
    void regularMatchCannotHavePenalties() throws Exception {
        String admin = login("admin", "admin123");
        long zone = newZone(admin);
        long a = newTeam(admin), b = newTeam(admin);
        assignToZone(admin, zone, a, b);
        long match = scheduleMatch(admin, zone, a, b);
        score(admin, match, "{\"homePenalties\":5,\"awayPenalties\":4}").andExpect(status().isBadRequest());
        score(admin, match, "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.winner").doesNotExist()); // 0-0 draw stands in a regular match
    }

    @Test
    void specialMatchValidation() throws Exception {
        String admin = login("admin", "admin123");
        long zone = newZone(admin);
        long a = newTeam(admin), b = newTeam(admin);
        assignToZone(admin, zone, a, b);
        String url = "/api/admin/tournaments/" + zoneTournaments.get(zone) + "/special-matches";

        post(url, specialMatch("FINAL", "{\"teamId\":" + a + ",\"zoneId\":" + zone + ",\"position\":1}", slot(zone, 2)), admin)
                .andExpect(status().isBadRequest()); // both a team and a rule
        post(url, specialMatch("FINAL", "{\"zoneId\":" + zone + "}", slot(zone, 2)), admin)
                .andExpect(status().isBadRequest()); // rule without position
        post(url, specialMatch("FINAL", slot(zone, 1), slot(zone, 3)), admin)
                .andExpect(status().isConflict()); // only 2 teams
        post(url, specialMatch("REGULAR", slot(zone, 1), slot(zone, 2)), admin)
                .andExpect(status().isBadRequest());
        post(url, specialMatch("FINAL", slot(zone, 1), slot(zone, 1)), admin)
                .andExpect(status().isConflict()); // same team on both sides
    }

    // --- helpers ---

    private long zoneOf(JsonNode category, long tournamentId) {
        long zoneId = category.get("zones").get(0).get("id").asLong();
        zoneTournaments.put(zoneId, tournamentId);
        return zoneId;
    }

    private String slot(long zoneId, int position) {
        return "{\"zoneId\":" + zoneId + ",\"position\":" + position + "}";
    }

    private String specialMatch(String type, String home, String away) {
        return "{\"type\":\"" + type + "\",\"scheduledAt\":\"2026-12-01T10:00:00\",\"home\":" + home
                + ",\"away\":" + away + "}";
    }

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

    private long newPlayer(String token, long teamId, String name) throws Exception {
        return read(post("/api/teams/" + teamId + "/players",
                "{\"players\":[{\"fullName\":\"" + name + "\",\"jerseyNo\":10}]}", token)
                .andExpect(status().isOk())).get(0).get("id").asLong();
    }

    private String event(long playerId, String type) {
        return "{\"playerId\":" + playerId + ",\"type\":\"" + type + "\"}";
    }

    /** The winner scores a single goal through a new player. */
    private void winOneNil(String admin, long matchId, long winnerTeamId) throws Exception {
        long scorer = newPlayer(admin, winnerTeamId, "Goleador " + UUID.randomUUID());
        score(admin, matchId, "{\"events\":[" + event(scorer, "GOAL") + "]}").andExpect(status().isOk());
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

    private ResultActions put(String url, String body, String token) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(url)
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
