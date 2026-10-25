package com.mjt.Football_Tournament_Management_System;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** A person (DNI) plays for one team at a time; transfers between editions, or within one if they did not play it */
class TransferApiTests extends ApiTestSupport {

    private static String freshDni() { return String.valueOf(20_000_000 + new Random().nextInt(70_000_000)); }

    @Test
    void aTransferMovesThePersonAndKeepsTheHistoryWithTheOldTeam() throws Exception {
        String admin = admin();
        String captain = newUser();
        long gulp = newTeam(admin), doblas = newTeam(captain);
        String dni = freshDni();

        // Apertura: plays and scores for GULP
        long apertura = newTournament(admin);
        long zone = newCategory(admin, apertura, "A").get("zones").get(0).get("id").asLong();
        long juan = read(post("/api/teams/" + gulp + "/players", "{\"players\":[{\"fullName\":\"Pérez, Juan\",\"jerseyNo\":9,\"dni\":\""
                + dni + "\"}]}", admin)).get(0).get("id").asLong();
        long rival = newTeam(admin);
        for (long team : new long[]{gulp, rival}) {
            post("/api/admin/zones/" + zone + "/teams", "{\"teamId\":" + team + "}", admin).andExpect(status().isOk());
        }
        long match = read(post("/api/admin/fixtures/manual", "{\"zoneId\":" + zone + ",\"fixtures\":[{\"homeTeamId\":" + gulp
                + ",\"awayTeamId\":" + rival + "}]}", admin)).get(0).get("id").asLong();
        patch("/api/admin/matches/" + match + "/score", "{\"events\":[{\"playerId\":" + juan + ",\"type\":\"GOAL\"}]}", admin)
                .andExpect(status().isOk());

        // DOBLAS also plays the Apertura: Juan already played it for GULP, no transfer into it
        post("/api/admin/zones/" + zone + "/teams", "{\"teamId\":" + doblas + "}", admin).andExpect(status().isOk());
        post("/api/teams/" + doblas + "/players", "{\"players\":[{\"fullName\":\"Juan\",\"jerseyNo\":10,\"dni\":\"" + dni + "\"}]}", captain)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("ya jugó")));

        // Clausura: another DOBLAS-like team that did not play the Apertura can take him; the captain asks, the admin approves
        String captain2 = newUser();
        long vilma = newTeam(captain2);
        var asked = read(post("/api/teams/" + vilma + "/players", "{\"players\":[{\"fullName\":\"Juan\",\"jerseyNo\":10,\"dni\":\""
                + dni + "\"}]}", captain2).andExpect(status().isOk())).get(0);
        assertThat(asked.get("approved").asBoolean()).isFalse();
        assertThat(asked.get("transferFrom").asText()).isNotBlank();
        assertThat(asked.get("fullName").asText()).isEqualTo("Pérez, Juan"); // the person's name
        long newFile = asked.get("id").asLong();
        get("/api/admin/requests", admin)
                .andExpect(jsonPath("$[?(@.id == " + newFile + ")].subject").value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("pase desde"))));
        // Still in GULP until approved
        get("/api/public/teams/" + gulp + "/players", null).andExpect(jsonPath("$[?(@.id == " + juan + ")]").isNotEmpty());

        post("/api/admin/requests/PLAYER_ADD/" + newFile + "/approve", "", admin).andExpect(status().isNoContent());
        get("/api/public/teams/" + gulp + "/players", null).andExpect(jsonPath("$[?(@.id == " + juan + ")]").isEmpty());
        get("/api/public/teams/" + vilma + "/players", null).andExpect(jsonPath("$[?(@.id == " + newFile + ")]").isNotEmpty());
        // His Apertura goal stays with GULP
        get("/api/public/tournaments/" + apertura + "/scorers", null)
                .andExpect(jsonPath("$[0].player.id").value(juan))
                .andExpect(jsonPath("$[0].team.id").value(gulp));
    }

    /** A suspension is the person's: it follows them to the new team and into the next season until served */
    @Test
    void suspensionsFollowThePersonAcrossTeamsAndSeasons() throws Exception {
        String admin = admin();
        int year = 1900 + new Random().nextInt(90);
        long season = read(post("/api/admin/tournaments", "{\"name\":\"Clausura " + year + "\",\"seasonYear\":" + year
                + ",\"stage\":\"CLAUSURA\"}", admin)).get("seasonId").asLong();
        long next = read(post("/api/admin/tournaments", "{\"name\":\"Apertura " + (year + 1) + "\",\"seasonYear\":" + (year + 1)
                + ",\"stage\":\"APERTURA\"}", admin)).get("seasonId").asLong();
        long gulp = newTeam(admin), vilma = newTeam(admin);
        String dni = freshDni();
        long juan = read(post("/api/teams/" + gulp + "/players", "{\"players\":[{\"fullName\":\"Pérez, Juan\",\"jerseyNo\":9,\"dni\":\""
                + dni + "\"}]}", admin)).get(0).get("id").asLong();
        long red = read(post("/api/admin/seasons/" + season + "/sanctions", "{\"playerId\":" + juan
                + ",\"type\":\"DIRECT_RED\",\"matchesSuspended\":3,\"matchesServed\":1}", admin).andExpect(status().isOk())).get("id").asLong();
        long served = read(post("/api/admin/seasons/" + season + "/sanctions", "{\"playerId\":" + juan
                + ",\"type\":\"DOUBLE_YELLOW\",\"matchesSuspended\":1,\"matchesServed\":1}", admin)).get("id").asLong();

        // Transfer to VILMA (by the admin: direct)
        post("/api/teams/" + vilma + "/players", "{\"players\":[{\"fullName\":\"Juan\",\"jerseyNo\":4,\"dni\":\"" + dni + "\"}]}", admin)
                .andExpect(status().isOk());

        // In its season it shows the team he plays for now, and where he got it
        get("/api/public/seasons/" + season + "/sanctions", null)
                .andExpect(jsonPath("$[?(@.id == " + red + ")].team.id").value((int) vilma))
                .andExpect(jsonPath("$[?(@.id == " + red + ")].receivedWith.id").value((int) gulp));
        // Next season: the pending one carries over (2 matches left), the served one does not
        get("/api/public/seasons/" + next + "/sanctions", null)
                .andExpect(jsonPath("$[?(@.id == " + red + ")].pending").value(2))
                .andExpect(jsonPath("$[?(@.id == " + red + ")].seasonYear").value(year))
                .andExpect(jsonPath("$[?(@.id == " + red + ")].team.id").value((int) vilma))
                .andExpect(jsonPath("$[?(@.id == " + served + ")]").isEmpty());
    }

    @Test
    void theSameDniOnTwoPlayersWithoutOneMergesThem() throws Exception {
        String admin = admin();
        long a = newTeam(admin), b = newTeam(admin);
        // The same person imported twice without DNI: in A (left already) and in B
        long old = read(post("/api/teams/" + a + "/players", "{\"players\":[{\"fullName\":\"Gómez\",\"jerseyNo\":1}]}", admin)).get(0).get("id").asLong();
        long now = read(post("/api/teams/" + b + "/players", "{\"players\":[{\"fullName\":\"Gómez, Luis\",\"jerseyNo\":5}]}", admin)).get(0).get("id").asLong();
        String dni = freshDni();
        patch("/api/teams/" + b + "/players/" + now, "{\"dni\":\"" + dni + "\"}", admin).andExpect(status().isOk());
        // While both are in a roster, it would be two teams at once
        patch("/api/teams/" + a + "/players/" + old, "{\"dni\":\"" + dni + "\"}", admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("pase")));
        // He scored for A and left it (file closed, goal kept): now giving the old file the DNI merges both people
        long t = newTournament(admin);
        long zone = newCategory(admin, t, "A").get("zones").get(0).get("id").asLong();
        long rival = newTeam(admin);
        for (long team : new long[]{a, rival}) post("/api/admin/zones/" + zone + "/teams", "{\"teamId\":" + team + "}", admin);
        long match = read(post("/api/admin/fixtures/manual", "{\"zoneId\":" + zone + ",\"fixtures\":[{\"homeTeamId\":" + a
                + ",\"awayTeamId\":" + rival + "}]}", admin)).get(0).get("id").asLong();
        patch("/api/admin/matches/" + match + "/score", "{\"events\":[{\"playerId\":" + old + ",\"type\":\"GOAL\"}]}", admin);
        delete("/api/teams/" + a + "/players/" + old, admin).andExpect(status().isNoContent());
        patch("/api/teams/" + a + "/players/" + old, "{\"dni\":\"" + dni + "\"}", admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.dni").value(dni)).andExpect(jsonPath("$.gone").value(true));
        get("/api/teams/" + b + "/roster", admin).andExpect(jsonPath("$[0].dni").value(dni));
        get("/api/public/tournaments/" + t + "/scorers", null).andExpect(jsonPath("$[0].player.id").value(old));
    }
}
