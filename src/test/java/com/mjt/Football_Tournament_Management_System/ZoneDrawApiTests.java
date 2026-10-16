package com.mjt.Football_Tournament_Management_System;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ZoneDrawApiTests extends ApiTestSupport {

    @Test
    void drawSpreadsTeamsEvenlyAndFillsTheSmallestZonesFirst() throws Exception {
        String admin = admin();
        long tournament = newTournament(admin);
        var category = newCategory(admin, tournament, "A");
        long zone1 = category.get("zones").get(0).get("id").asLong();
        long zone2 = newZone(admin, category.get("id").asLong(), "Zona 2");
        long zone3 = newZone(admin, category.get("id").asLong(), "Zona 3");

        // Zone 1 already has a team placed by hand: the draw must not move it and must compensate
        long placed = enroll(admin, newTeam(admin), tournament);
        patch("/api/admin/registrations/" + placed + "/zone", "{\"zoneId\":" + zone1 + "}", admin)
                .andExpect(status().isOk());
        List<Long> regs = new ArrayList<>();
        for (int i = 0; i < 8; i++) regs.add(enroll(admin, newTeam(admin), tournament));

        var drawn = read(post("/api/admin/categories/" + category.get("id").asLong() + "/zone-draw",
                "{\"registrationIds\":" + regs + "}", admin).andExpect(status().isOk()));
        assertEquals(8, drawn.size());

        var sizes = new HashMap<Long, Integer>();
        for (var r : read(get("/api/public/tournaments/" + tournament + "/registrations", null))) {
            sizes.merge(r.get("zoneId").asLong(), 1, Integer::sum);
        }
        assertEquals(3, sizes.get(zone1)); // 1 by hand + 2 drawn
        assertEquals(3, sizes.get(zone2));
        assertEquals(3, sizes.get(zone3));
    }

    @Test
    void drawRejectsAssignedTeamsOtherTournamentsAndNonAdmins() throws Exception {
        String admin = admin();
        long tournament = newTournament(admin);
        var category = newCategory(admin, tournament, "A");
        long categoryId = category.get("id").asLong();
        long zone = category.get("zones").get(0).get("id").asLong();

        long assigned = enroll(admin, newTeam(admin), tournament);
        patch("/api/admin/registrations/" + assigned + "/zone", "{\"zoneId\":" + zone + "}", admin);
        post("/api/admin/categories/" + categoryId + "/zone-draw", "{\"registrationIds\":[" + assigned + "]}", admin)
                .andExpect(status().isConflict());

        long elsewhere = enroll(admin, newTeam(admin), newTournament(admin));
        post("/api/admin/categories/" + categoryId + "/zone-draw", "{\"registrationIds\":[" + elsewhere + "]}", admin)
                .andExpect(status().isConflict());

        post("/api/admin/categories/" + categoryId + "/zone-draw", "{\"registrationIds\":[]}", admin)
                .andExpect(status().isBadRequest());
        post("/api/admin/categories/" + categoryId + "/zone-draw", "{\"registrationIds\":[" + elsewhere + "]}", newUser())
                .andExpect(status().isForbidden());
    }
}
