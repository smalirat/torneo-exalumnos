package com.mjt.Football_Tournament_Management_System;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EditionBuilderApiTests extends ApiTestSupport {

    /**
     * Like Apertura → Clausura 2026: one category in two zones; the top places go to A, the promotions (3º of a
     * zone vs 4º of the other) decide, the ones below them go to B.
     */
    @Test
    void proposesCategoriesFromTheResultsAndAppliesThem() throws Exception {
        String admin = admin();
        long source = newTournament(admin);
        var unica = newCategory(admin, source, "Única");
        long z1 = unica.get("zones").get(0).get("id").asLong();
        long z2 = newZone(admin, unica.get("id").asLong(), "Zona 2");
        List<Long> zone1 = teamsIn(admin, source, z1, 5), zone2 = teamsIn(admin, source, z2, 4);
        playRanked(admin, z1, zone1);
        playRanked(admin, z2, zone2);
        // 3º Zona 1 beats 4º Zona 2; 3º Zona 2 vs 4º Zona 1 is not played yet
        long won = special(admin, source, zone1.get(2), zone2.get(3));
        patch("/api/admin/matches/" + won + "/score", "{\"homeGoals\":2,\"awayGoals\":0,\"events\":[]}", admin).andExpect(status().isOk());
        special(admin, source, zone2.get(2), zone1.get(3));

        long target = newTournament(admin);
        long a = newCategory(admin, target, "A").get("id").asLong();
        var b = newCategory(admin, target, "B");
        newZone(admin, b.get("id").asLong(), "Zona 2");
        long newcomer = newTeam(admin);
        enroll(admin, newcomer, target);

        get("/api/admin/tournaments/" + target + "/build/proposal?from=" + source, newUser()).andExpect(status().isForbidden());
        var proposal = read(get("/api/admin/tournaments/" + target + "/build/proposal?from=" + source, admin).andExpect(status().isOk()));
        assertThat(proposal.get("targetHasMatches").asBoolean()).isFalse();
        Map<Long, JsonNode> rows = new HashMap<>();
        for (var row : proposal.get("teams")) rows.put(row.get("team").get("id").asLong(), row);
        long bId = b.get("id").asLong();

        expect(rows, zone1.get(0), a, "STAYS", false);
        expect(rows, zone2.get(1), a, "STAYS", false);
        expect(rows, zone1.get(2), a, "PROMO_WON", false);
        expect(rows, zone2.get(3), bId, "PROMO_LOST", false);
        expect(rows, zone2.get(2), a, "PROMO_PENDING", true);
        expect(rows, zone1.get(3), a, "PROMO_PENDING", true);
        expect(rows, zone1.get(4), bId, "DESCENDS", false);
        expect(rows, newcomer, bId, "NEW", true);
        assertThat(rows.get(zone1.get(0)).get("sourceGroup").asText()).isEqualTo("Zona 1");
        assertThat(rows.get(zone1.get(0)).get("position").asInt()).isEqualTo(1);

        // The admin answers: 4º Zona 1 goes to B; everything else as proposed, with the fixture
        var assignments = new ArrayList<String>();
        for (var row : proposal.get("teams")) {
            long team = row.get("team").get("id").asLong();
            long category = team == zone1.get(3) ? bId : row.get("categoryId").asLong();
            assignments.add("{\"teamId\":" + team + ",\"categoryId\":" + category + "}");
        }
        var result = read(post("/api/admin/tournaments/" + target + "/build", "{\"assignments\":[" + String.join(",", assignments)
                + "],\"zoneMode\":\"BALANCED\",\"fixtureStart\":\"2026-11-01T09:00:00\",\"minutesBetween\":70}", admin)
                .andExpect(status().isOk()));
        assertThat(result.get("teams").asInt()).isEqualTo(10);
        // A: 6 teams round robin = 15 matches; B: 4 teams in two zones of 2 = 1 + 1
        assertThat(result.get("matches").asInt()).isEqualTo(17);

        var regs = read(get("/api/public/tournaments/" + target + "/registrations", null));
        assertThat(regs.size()).isEqualTo(10);
        for (var r : regs) assertThat(r.get("zoneId").isNull()).isFalse();
        // Built once: the edition has matches now
        post("/api/admin/tournaments/" + target + "/build", "{\"assignments\":[" + assignments.get(0) + "]}", admin)
                .andExpect(status().isConflict());
    }

    /** Same structure (A, B in two zones): the definitions by position are copied, plus an interzonal day */
    @Test
    void copiesTheDefinitionsAndAddsAnInterzonalDay() throws Exception {
        String admin = admin();
        long source = newTournament(admin);
        long sa = newCategory(admin, source, "A").get("zones").get(0).get("id").asLong();
        var sb = newCategory(admin, source, "B");
        long sb1 = sb.get("zones").get(0).get("id").asLong();
        long sb2 = newZone(admin, sb.get("id").asLong(), "Zona 2");
        List<Long> a = teamsIn(admin, source, sa, 4), b1 = teamsIn(admin, source, sb1, 2), b2 = teamsIn(admin, source, sb2, 2);
        playRanked(admin, sa, a);
        playRanked(admin, sb1, b1);
        playRanked(admin, sb2, b2);
        // 4º of A vs 1º of B · Zona 1, by rule
        post("/api/admin/tournaments/" + source + "/special-matches", "{\"type\":\"PROMOTION\",\"home\":{\"zoneId\":" + sa
                + ",\"position\":4},\"away\":{\"zoneId\":" + sb1 + ",\"position\":1}}", admin).andExpect(status().isOk());

        long target = newTournament(admin);
        var ta = newCategory(admin, target, "A");
        long taZone = ta.get("zones").get(0).get("id").asLong();
        var tb = newCategory(admin, target, "B");
        long tb1 = tb.get("zones").get(0).get("id").asLong();
        newZone(admin, tb.get("id").asLong(), "Zona 2");

        var proposal = read(get("/api/admin/tournaments/" + target + "/build/proposal?from=" + source, admin).andExpect(status().isOk()));
        assertThat(proposal.get("zones").size()).isEqualTo(3);
        assertThat(proposal.get("zones").get(1).get("label").asText()).isEqualTo("B · Zona 1");
        var defs = proposal.get("definitions");
        assertThat(defs.size()).isEqualTo(1);
        assertThat(defs.get(0).get("homeZoneId").asLong()).isEqualTo(taZone);
        assertThat(defs.get(0).get("homePosition").asInt()).isEqualTo(4);
        assertThat(defs.get(0).get("awayZoneId").asLong()).isEqualTo(tb1);

        var assignments = new ArrayList<String>();
        for (var row : proposal.get("teams")) {
            assignments.add("{\"teamId\":" + row.get("team").get("id").asLong() + ",\"categoryId\":" + row.get("categoryId").asLong() + "}");
        }
        var d = defs.get(0);
        var result = read(post("/api/admin/tournaments/" + target + "/build", "{\"assignments\":[" + String.join(",", assignments)
                + "],\"zoneMode\":\"BALANCED\",\"fixtureStart\":\"2027-03-07T09:00:00\",\"interzonal\":true,\"definitions\":[{\"type\":\""
                + d.get("type").asText() + "\",\"homeZoneId\":" + d.get("homeZoneId") + ",\"homePosition\":4,\"awayZoneId\":"
                + d.get("awayZoneId") + ",\"awayPosition\":1}]}", admin).andExpect(status().isOk()));
        assertThat(result.get("definitions").asInt()).isEqualTo(1);
        // A: 4 teams = 6 matches; B: 2 + 2 = 1 + 1, plus an interzonal day of 2
        assertThat(result.get("matches").asInt()).isEqualTo(10);
        var all = read(get("/api/public/tournaments/" + target + "/fixtures", null));
        int interzonal = 0;
        for (var m : all) if ("Interzonal".equals(m.get("roundLabel").asText(null))) {
            interzonal++;
            assertThat(m.get("zoneId").isNull()).isTrue();
            // One match day a week (A: 3 rounds from 7/3), the interzonal the week after the last one
            assertThat(m.get("scheduledAt").asText()).startsWith("2027-03-28T");
            assertThat(m.get("round").asInt()).isEqualTo(4);
        }
        assertThat(interzonal).isEqualTo(2);

        // The promotion follows the table of A as results come in
        long first = -1;
        for (var m : all) if (m.get("zoneId").asLong() == taZone) { first = m.get("id").asLong(); break; }
        var played = read(patch("/api/admin/matches/" + first + "/score", "{\"homeGoals\":0,\"awayGoals\":3,\"events\":[]}", admin)
                .andExpect(status().isOk()));
        var table = read(get("/api/public/zones/" + taZone + "/standings", null));
        long fourth = table.get(3).get("team").get("id").asLong();
        assertThat(played.get("homeTeam").get("id").asLong()).isNotNull();
        var promo = read(get("/api/public/tournaments/" + target + "/special-matches", null)).get(0);
        assertThat(promo.get("homeTeam").get("id").asLong()).isEqualTo(fourth);
    }

    @Test
    void adminPutsTeamsStraightIntoAZoneAndTakesThemOut() throws Exception {
        String admin = admin();
        long tournament = newTournament(admin);
        long zone = newCategory(admin, tournament, "A").get("zones").get(0).get("id").asLong();
        long existing = newTeam(admin);
        String name = "Nuevo " + UUID.randomUUID();

        post("/api/admin/zones/" + zone + "/teams", "{\"teamId\":" + existing + "}", newUser()).andExpect(status().isForbidden());
        long reg = read(post("/api/admin/zones/" + zone + "/teams", "{\"teamId\":" + existing + "}", admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.zoneId").value(zone)).andExpect(jsonPath("$.approved").value(true)))
                .get("id").asLong();
        post("/api/admin/zones/" + zone + "/teams", "{\"teamId\":" + existing + "}", admin).andExpect(status().isConflict());
        long created = read(post("/api/admin/zones/" + zone + "/teams", "{\"teamName\":\"" + name + "\"}", admin)
                .andExpect(status().isOk())).get("team").get("id").asLong();
        post("/api/admin/zones/" + zone + "/teams", "{\"teamName\":\"" + name + "\"}", admin).andExpect(status().isConflict());
        post("/api/admin/zones/" + zone + "/teams", "{}", admin).andExpect(status().isBadRequest());
        get("/api/public/zones/" + zone + "/registrations", null).andExpect(jsonPath("$.length()").value(2));

        // With a match it cannot be taken out (it would be a withdrawal)
        post("/api/admin/fixtures/manual", "{\"zoneId\":" + zone + ",\"fixtures\":[{\"homeTeamId\":" + existing
                + ",\"awayTeamId\":" + created + "}]}", admin).andExpect(status().isOk());
        delete("/api/admin/registrations/" + reg, admin).andExpect(status().isConflict());
        long empty = read(post("/api/admin/zones/" + zone + "/teams", "{\"teamId\":" + newTeam(admin) + "}", admin)).get("id").asLong();
        delete("/api/admin/registrations/" + empty, admin).andExpect(status().isNoContent());
        get("/api/public/zones/" + zone + "/registrations", null).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void editionHasBannerAndLogo() throws Exception {
        String admin = admin();
        long tournament = newTournament(admin);
        var png = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(4, 2, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", png);
        var file = new org.springframework.mock.web.MockMultipartFile("file", "b.png", "image/png", png.toByteArray());
        var upload = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart("/api/admin/tournaments/" + tournament + "/images/banner").file(file)
                .with(r -> { r.setMethod("PUT"); return r; })
                .header("Authorization", "Bearer " + admin);
        mvc.perform(upload).andExpect(status().isOk()).andExpect(jsonPath("$.bannerVersion").isNumber())
                .andExpect(jsonPath("$.logoVersion").isEmpty());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/public/tournaments/" + tournament + "/images/banner"))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png"));
        get("/api/public/tournaments/" + tournament + "/images/logo", null).andExpect(status().isNotFound());
        delete("/api/admin/tournaments/" + tournament + "/images/banner", admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.bannerVersion").isEmpty());
        get("/api/public/tournaments/" + tournament + "/images/banner", null).andExpect(status().isNotFound());
    }

    private void expect(Map<Long, JsonNode> rows, long team, long category, String reason, boolean question) {
        var row = rows.get(team);
        assertThat(row).as("team %d in the proposal", team).isNotNull();
        assertThat(row.get("reason").asText()).as("reason of team %d", team).isEqualTo(reason);
        assertThat(row.get("categoryId").asLong()).as("category of team %d", team).isEqualTo(category);
        assertThat(row.get("question").asBoolean()).as("question for team %d", team).isEqualTo(question);
    }

    private List<Long> teamsIn(String admin, long tournament, long zone, int n) throws Exception {
        var out = new ArrayList<Long>();
        for (int i = 0; i < n; i++) {
            long team = newTeam(admin);
            long reg = enroll(admin, team, tournament);
            patch("/api/admin/registrations/" + reg + "/zone", "{\"zoneId\":" + zone + "}", admin).andExpect(status().isOk());
            out.add(team);
        }
        return out;
    }

    /** Everyone plays everyone and the first in the list always wins 1-0: the final table is the list order */
    private void playRanked(String admin, long zone, List<Long> teams) throws Exception {
        for (int i = 0; i < teams.size(); i++) {
            for (int j = i + 1; j < teams.size(); j++) {
                long match = read(post("/api/admin/fixtures/manual", "{\"zoneId\":" + zone + ",\"fixtures\":[{\"homeTeamId\":"
                        + teams.get(i) + ",\"awayTeamId\":" + teams.get(j) + ",\"round\":1}]}", admin)).get(0).get("id").asLong();
                patch("/api/admin/matches/" + match + "/score", "{\"homeGoals\":1,\"awayGoals\":0,\"events\":[]}", admin)
                        .andExpect(status().isOk());
            }
        }
    }

    private long special(String admin, long tournament, long home, long away) throws Exception {
        return read(post("/api/admin/tournaments/" + tournament + "/special-matches", "{\"type\":\"PROMOTION\",\"home\":{\"teamId\":"
                + home + "},\"away\":{\"teamId\":" + away + "}}", admin).andExpect(status().isOk())).get("id").asLong();
    }
}
