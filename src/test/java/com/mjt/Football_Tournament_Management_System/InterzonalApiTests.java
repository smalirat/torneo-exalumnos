package com.mjt.Football_Tournament_Management_System;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class InterzonalApiTests extends ApiTestSupport {

    @Test
    void crossesTheTablesByPositionWithoutRepeatingRivals() throws Exception {
        String admin = admin();
        long t = newTournament(admin);
        var a = newCategory(admin, t, "A");
        long z1 = a.get("zones").get(0).get("id").asLong();
        long z2 = newZone(admin, a.get("id").asLong(), "Zona 2");
        long zb = newCategory(admin, t, "B").get("zones").get(0).get("id").asLong();
        List<Long> one = ranked(admin, t, z1, 3), two = ranked(admin, t, z2, 3), b = ranked(admin, t, zb, 2);

        String body = "{\"pairs\":[{\"zoneA\":" + z1 + ",\"zoneB\":" + z2 + "}],\"rounds\":2,\"start\":\"2026-11-01T09:00:00\"}";
        post("/api/admin/tournaments/" + t + "/interzonal?preview=true", body, newUser()).andExpect(status().isForbidden());
        var preview = read(post("/api/admin/tournaments/" + t + "/interzonal?preview=true", body, admin).andExpect(status().isOk()));
        assertThat(preview.size()).isEqualTo(6);
        int before = read(get("/api/public/tournaments/" + t + "/fixtures", null)).size();

        var created = read(post("/api/admin/tournaments/" + t + "/interzonal", body, admin).andExpect(status().isOk()));
        assertThat(read(get("/api/public/tournaments/" + t + "/fixtures", null)).size()).isEqualTo(before + 6);
        // Match day 1 (after the zone rounds): 1º vs 1º, 2º vs 2º, 3º vs 3º, Zona 1 at home
        var m0 = created.get(0);
        assertThat(m0.get("roundLabel").asText()).isEqualTo("Interzonal");
        assertThat(m0.get("zoneId").isNull()).isTrue();
        assertThat(m0.get("homeTeam").get("id").asLong()).isEqualTo(one.get(0));
        assertThat(m0.get("awayTeam").get("id").asLong()).isEqualTo(two.get(0));
        assertThat(created.get(2).get("homeTeam").get("id").asLong()).isEqualTo(one.get(2));
        int round1 = m0.get("round").asInt();
        // Match day 2, a week later: 1º vs 2º, Zona 2 at home
        var m3 = created.get(3);
        assertThat(m3.get("round").asInt()).isEqualTo(round1 + 1);
        assertThat(m3.get("scheduledAt").asText()).startsWith("2026-11-08T09:00");
        assertThat(m3.get("awayTeam").get("id").asLong()).isEqualTo(one.get(0));
        assertThat(m3.get("homeTeam").get("id").asLong()).isEqualTo(two.get(1));

        // Intercategory, inverse: 1º of B vs último of Zona 1; with 3 teams in the bigger zone, at most 3 match days
        String inter = "{\"pairs\":[{\"zoneA\":" + zb + ",\"zoneB\":" + z1 + "}],\"rounds\":1,\"crossing\":\"INVERSE\"}";
        var cross = read(post("/api/admin/tournaments/" + t + "/interzonal?preview=true", inter, admin).andExpect(status().isOk()));
        assertThat(cross.size()).isEqualTo(2);
        assertThat(cross.get(0).get("roundLabel").asText()).isEqualTo("Intercategoría");
        assertThat(cross.get(0).get("homeTeam").get("id").asLong()).isEqualTo(b.get(0));
        assertThat(cross.get(0).get("awayTeam").get("id").asLong()).isEqualTo(one.get(2));
        post("/api/admin/tournaments/" + t + "/interzonal?preview=true", inter.replace("\"rounds\":1", "\"rounds\":4"), admin)
                .andExpect(status().isBadRequest());
        post("/api/admin/tournaments/" + t + "/interzonal?preview=true", "{\"pairs\":[{\"zoneA\":" + z1 + ",\"zoneB\":" + z2
                + "},{\"zoneA\":" + z1 + ",\"zoneB\":" + zb + "}],\"rounds\":1}", admin).andExpect(status().isBadRequest());
    }

    /** Teams of a zone that play everyone; the first in the list always wins: the table is the list order */
    private List<Long> ranked(String admin, long tournament, long zone, int n) throws Exception {
        var out = new ArrayList<Long>();
        for (int i = 0; i < n; i++) {
            long team = newTeam(admin);
            long reg = enroll(admin, team, tournament);
            patch("/api/admin/registrations/" + reg + "/zone", "{\"zoneId\":" + zone + "}", admin).andExpect(status().isOk());
            out.add(team);
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                long match = read(post("/api/admin/fixtures/manual", "{\"zoneId\":" + zone + ",\"fixtures\":[{\"homeTeamId\":"
                        + out.get(i) + ",\"awayTeamId\":" + out.get(j) + ",\"round\":1}]}", admin)).get(0).get("id").asLong();
                patch("/api/admin/matches/" + match + "/score", "{\"homeGoals\":1,\"awayGoals\":0,\"events\":[]}", admin)
                        .andExpect(status().isOk());
            }
        }
        return out;
    }
}
