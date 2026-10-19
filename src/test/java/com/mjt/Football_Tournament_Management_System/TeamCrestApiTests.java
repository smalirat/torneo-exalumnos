package com.mjt.Football_Tournament_Management_System;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TeamCrestApiTests extends ApiTestSupport {

    @Test
    void captainOrAdminSetsTheCrestAndEveryTeamReferenceCarriesItsUrl() throws Exception {
        String captain = newUser();
        long team = read(post("/api/teams/register", "{\"name\":\"Escudo " + UUID.randomUUID() + "\"}", captain)).get("id").asLong();
        post("/api/admin/requests/teams/" + team + "/approve-all", "", admin()).andExpect(status().isOk()); // starts in review
        get("/api/public/teams/" + team, null).andExpect(jsonPath("$.crest").doesNotExist());

        byte[] png = png();
        upload(team, png, "image/png", newUser()).andExpect(status().isForbidden()); // someone else's captain
        String url = read(upload(team, png, "image/png", captain).andExpect(status().isOk())).get("crest").asText();
        get("/api/public/teams/" + team, null).andExpect(jsonPath("$.crest").value(url));
        get("/api/teams", captain).andExpect(jsonPath("$[0].crest").value(url));

        var image = get(url, null)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(png, image);

        // A new upload changes the URL (version), so cached images never go stale
        Thread.sleep(1100);
        String url2 = read(upload(team, png, "image/png", admin()).andExpect(status().isOk())).get("crest").asText();
        assertNotEquals(url, url2);

        delete("/api/teams/" + team + "/crest", captain).andExpect(status().isOk()).andExpect(jsonPath("$.crest").doesNotExist());
        get("/api/public/teams/" + team + "/crest", null).andExpect(status().isNotFound());
    }

    @Test
    void rejectsFilesThatAreNotImages() throws Exception {
        String admin = admin();
        long team = newTeam(admin);
        upload(team, "hola".getBytes(), "text/plain", admin).andExpect(status().isBadRequest());
        upload(team, "no es png".getBytes(), "image/png", admin).andExpect(status().isBadRequest());
        upload(team, png(), "image/png", null).andExpect(status().isUnauthorized());
    }

    private ResultActions upload(long team, byte[] data, String type, String token) throws Exception {
        var req = multipart("/api/teams/" + team + "/crest").file(new MockMultipartFile("file", "escudo", type, data));
        req.with(r -> { r.setMethod("PUT"); return r; });
        if (token != null) req.header("Authorization", "Bearer " + token);
        return mvc.perform(req);
    }

    private static byte[] png() throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), "png", out);
        return out.toByteArray();
    }
}
