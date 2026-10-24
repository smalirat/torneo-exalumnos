package com.mjt.Football_Tournament_Management_System;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PlayerDniApiTests extends ApiTestSupport {

    /** A DNI nobody used yet in this test run (8 digits) */
    private static String freshDni() { return String.valueOf(20_000_000 + new Random().nextInt(70_000_000)); }

    @Test
    void dniIsUniqueOptionalAndPrivate() throws Exception {
        String admin = admin();
        long team = newTeam(admin), other = newTeam(admin);
        String dni = freshDni();
        String dotted = dni.substring(0, 2) + "." + dni.substring(2, 5) + "." + dni.substring(5);

        // Stored as digits only; without a DNI is still allowed for now
        var added = read(post("/api/teams/" + team + "/players", "{\"players\":[{\"fullName\":\"Pérez, Juan\",\"jerseyNo\":9,\"dni\":\""
                + dotted + "\"},{\"fullName\":\"Sin Documento\",\"jerseyNo\":5}]}", admin).andExpect(status().isOk()));
        assertThat(added.get(0).get("dni").asText()).isEqualTo(dni);
        long noDni = added.get(1).get("id").asLong();
        assertThat(added.get(1).get("dni").asText()).isEmpty();

        // The same DNI is the same person: not twice in the same roster (in another team it is a transfer: TransferApiTests)
        post("/api/teams/" + team + "/players", "{\"players\":[{\"fullName\":\"Otro\",\"jerseyNo\":1,\"dni\":\"" + dni + "\"}]}", admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Pérez, Juan")));
        post("/api/teams/" + other + "/players", "{\"players\":[{\"fullName\":\"Corto\",\"jerseyNo\":1,\"dni\":\"123\"}]}", admin)
                .andExpect(status().isBadRequest());

        // Loaded later, directly; a jersey change keeps it; blank removes it
        String later = freshDni();
        patch("/api/teams/" + team + "/players/" + noDni, "{\"dni\":\"" + later + "\"}", admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.dni").value(later));
        patch("/api/teams/" + team + "/players/" + noDni, "{\"jerseyNo\":7}", admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.dni").value(later)).andExpect(jsonPath("$.jerseyNo").value(7));
        patch("/api/teams/" + team + "/players/" + noDni, "{\"dni\":\"" + dni + "\"}", admin).andExpect(status().isConflict());
        patch("/api/teams/" + team + "/players/" + noDni, "{\"dni\":\"\"}", admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.dni").value(""));

        // Personal data: in the private roster, never in the public list
        get("/api/teams/" + team + "/roster", admin).andExpect(jsonPath("$[?(@.fullName == 'Pérez, Juan')].dni").value(dni));
        get("/api/teams/" + team + "/roster", newUser()).andExpect(status().isForbidden());
        get("/api/public/teams/" + team + "/players", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dni").doesNotExist());
    }
}
