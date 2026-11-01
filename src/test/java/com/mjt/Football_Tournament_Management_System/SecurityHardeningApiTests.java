package com.mjt.Football_Tournament_Management_System;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Attacks the API should resist: personal data leaking to the public, hostile input, forged tokens and images */
class SecurityHardeningApiTests extends ApiTestSupport {

    @Test
    void publicEndpointsNeverShowPersonalData() throws Exception {
        String admin = admin();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "privado-" + suffix + "@mail.com", phone = "11 4" + (1000000 + new Random().nextInt(8999999));
        String captain = read(post("/api/auth/register", "{\"username\":\"c" + suffix + "\",\"password\":\"password123\",\"email\":\""
                + email + "\",\"phone\":\"" + phone + "\",\"teamName\":\"Privados " + suffix + "\"}", null)
                .andExpect(status().isCreated())).get("token").asText();
        long team = newTeam(captain);
        String dni = String.valueOf(20_000_000 + new Random().nextInt(70_000_000));
        post("/api/teams/" + team + "/players", "{\"players\":[{\"fullName\":\"Gómez, Ana\",\"jerseyNo\":4,\"dni\":\"" + dni + "\"}]}", captain)
                .andExpect(status().isOk());
        long tournament = newTournament(admin);
        enroll(admin, team, tournament);

        for (String url : List.of("/api/public/league", "/api/public/seasons", "/api/public/tournaments",
                "/api/public/teams/unclaimed", "/api/public/teams/" + team, "/api/public/teams/" + team + "/players",
                "/api/public/teams/" + team + "/players?former=true", "/api/public/teams/" + team + "/registrations",
                "/api/public/tournaments/" + tournament + "/registrations", "/api/public/tournaments/" + tournament + "/fixtures",
                "/api/public/tournaments/" + tournament + "/scorers", "/api/public/tournaments/" + tournament + "/cards")) {
            String body = get(url, null).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertThat(body).as(url).doesNotContain(dni, email, phone, "\"dni\"", "\"email\"", "\"phone\"", "passwordHash", "inviteToken");
        }
    }

    @Test
    void invisibleCharactersAreRemovedFromText() throws Exception {
        String admin = admin();
        // \u0000 would make PostgreSQL fail; ‮ (right-to-left override) would make the name display reversed
        String name = "Equipo\\u0000 \\u202Ede prueba " + UUID.randomUUID().toString().substring(0, 6);
        var team = read(post("/api/teams/register", "{\"name\":\"" + name + "\"}", admin).andExpect(status().isOk()));
        assertThat(team.get("name").asText()).startsWith("Equipo de prueba ").doesNotContain("\u0000", "‮");
    }

    @Test
    void oversizedInputIsRejected() throws Exception {
        post("/api/auth/login", "{\"username\":\"" + "a".repeat(5000) + "\",\"password\":\"x\"}", null)
                .andExpect(status().isBadRequest());
        String admin = admin();
        long team = newTeam(admin);
        post("/api/teams/" + team + "/players", "{\"players\":[{\"fullName\":\"" + "b".repeat(500) + "\",\"jerseyNo\":1}]}", admin)
                .andExpect(status().isBadRequest());
        var many = new StringBuilder("{\"players\":[");
        for (int i = 0; i < 61; i++) many.append(i == 0 ? "" : ",").append("{\"fullName\":\"P").append(i).append("\",\"jerseyNo\":").append(i).append('}');
        post("/api/teams/" + team + "/players", many + "]}", admin).andExpect(status().isBadRequest());
        put("/api/teams/" + team, "{\"name\":\"Ok\",\"phone\":\"<script>\"}", admin).andExpect(status().isBadRequest());
    }

    @Test
    void forgedTokensAreRejected() throws Exception {
        String admin = admin();
        get("/api/auth/me", admin).andExpect(status().isOk());

        // Signed with another key, claiming to be the admin
        var otherKey = Keys.hmacShaKeyFor("another-secret-another-secret-another-secret".getBytes(StandardCharsets.UTF_8));
        String forged = Jwts.builder().issuer("torneo-api").subject("admin").expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(otherKey).compact();
        get("/api/auth/me", forged).andExpect(status().isUnauthorized());
        get("/api/admin/requests", forged).andExpect(status().isUnauthorized());

        // Unsigned ("alg": "none"): the oldest JWT trick
        String unsigned = Jwts.builder().subject("admin").expiration(new Date(System.currentTimeMillis() + 60_000)).compact();
        get("/api/auth/me", unsigned).andExpect(status().isUnauthorized());

        // A real token with one character of the signature changed
        String tampered = admin.substring(0, admin.length() - 2) + (admin.endsWith("A") ? "BB" : "AA");
        get("/api/auth/me", tampered).andExpect(status().isUnauthorized());
    }

    @Test
    void uploadedImagesAreCleanedAndBombsRejected() throws Exception {
        String admin = admin();
        long team = newTeam(admin);

        // A valid PNG with HTML hidden after the image data: what is served is a redrawn image, without it
        byte[] polyglot = concat(png(8, 8), "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8));
        uploadCrest(team, polyglot, "image/png", admin).andExpect(status().isOk());
        byte[] served = get("/api/public/teams/" + team + "/crest", null).andExpect(status().isOk())
                .andExpect(content().contentType("image/png")).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(served, StandardCharsets.ISO_8859_1)).doesNotContain("<script>");

        // The real format decides, not what the browser says: a PNG sent as JPEG is stored and served as PNG
        uploadCrest(team, png(4, 4), "image/jpeg", admin).andExpect(status().isOk());
        get("/api/public/teams/" + team + "/crest", null).andExpect(content().contentType("image/png"));

        // A tiny file that declares 50000 x 50000 pixels (10 GB once decoded) is refused before decoding
        uploadCrest(team, withSize(png(1, 1), 50_000, 50_000), "image/png", admin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("demasiado grande")));

        // Not an image at all, or a format we do not take (GIF)
        uploadCrest(team, "<html></html>".getBytes(StandardCharsets.UTF_8), "image/png", admin).andExpect(status().isBadRequest());
        var gif = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "gif", gif);
        uploadCrest(team, gif.toByteArray(), "image/png", admin).andExpect(status().isBadRequest());

        // An SVG with a script, sent as SVG or disguised as PNG/JPEG: never stored (a browser would run it)
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8);
        for (String type : List.of("image/svg+xml", "image/png", "image/jpeg")) {
            uploadCrest(team, svg, type, admin).andExpect(status().isBadRequest());
        }
    }

    @Test
    void otherOriginsCannotCallTheApi() throws Exception {
        // A page on another site asking the browser for permission (CORS preflight): refused
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/api/auth/me")
                        .header("Origin", "https://evil.example").header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        // The configured frontend origin is allowed
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/api/auth/me")
                        .header("Origin", "http://localhost:5173").header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void captainsCannotTouchOtherTeams() throws Exception {
        String admin = admin();
        String owner = newUser(), intruder = newUser();
        long team = newTeam(owner);
        long player = read(post("/api/teams/" + team + "/players", "{\"players\":[{\"fullName\":\"Ajeno, Uno\",\"jerseyNo\":1}]}", owner)
                .andExpect(status().isOk())).get(0).get("id").asLong();
        long tournament = newTournament(admin);

        get("/api/teams/" + team + "/roster", intruder).andExpect(status().isForbidden());
        put("/api/teams/" + team, "{\"name\":\"Robado\"}", intruder).andExpect(status().isForbidden());
        post("/api/teams/" + team + "/players", "{\"players\":[{\"fullName\":\"Intruso\",\"jerseyNo\":2}]}", intruder)
                .andExpect(status().isForbidden());
        patch("/api/teams/" + team + "/players/" + player, "{\"jerseyNo\":99}", intruder).andExpect(status().isForbidden());
        delete("/api/teams/" + team + "/players/" + player, intruder).andExpect(status().isForbidden());
        delete("/api/teams/" + team + "/crest", intruder).andExpect(status().isForbidden());
        uploadCrest(team, png(4, 4), "image/png", intruder).andExpect(status().isForbidden());
        post("/api/teams/" + team + "/tournaments/" + tournament + "/register", "", intruder).andExpect(status().isForbidden());
        get("/api/teams/" + team + "/tournaments/" + tournament + "/dashboard", intruder).andExpect(status().isForbidden());
        delete("/api/teams/" + team, intruder).andExpect(status().isForbidden());
        // Organizer-only areas
        get("/api/admin/requests", intruder).andExpect(status().isForbidden());
        get("/api/admin/banned", intruder).andExpect(status().isForbidden());
        get("/api/admin/protests", intruder).andExpect(status().isForbidden());
        post("/api/admin/teams/" + team + "/invite", "", intruder).andExpect(status().isForbidden());

        // Nothing changed
        get("/api/teams/" + team + "/roster", owner).andExpect(jsonPath("$[0].jerseyNo").value(1)).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void loginDoesNotRevealWhichUsernamesExist() throws Exception {
        String unknown = post("/api/auth/login", "{\"username\":\"nadie-" + UUID.randomUUID() + "\",\"password\":\"password123\"}", null)
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String wrongPassword = post("/api/auth/login", "{\"username\":\"admin\",\"password\":\"incorrecta-" + UUID.randomUUID() + "\"}", null)
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(json.readTree(unknown).get("detail")).isEqualTo(json.readTree(wrongPassword).get("detail"));
    }

    @Test
    void emailsNeedARealLookingDomain() throws Exception {
        String user = "m" + UUID.randomUUID().toString().substring(0, 8);
        for (String email : List.of("ana@localhost", "ana", "ana@mail.c", "a na@mail.com")) {
            post("/api/auth/register", "{\"username\":\"" + user + "\",\"password\":\"password123\",\"email\":\"" + email
                    + "\",\"phone\":\"11 5555-1234\",\"teamName\":\"Mails " + user + "\"}", null).andExpect(status().isBadRequest());
        }
    }

    @Test
    void publicOrGuessableJwtSecretsAreRefused() {
        // The example value is public on GitHub; the others are long enough but trivial to guess
        for (String secret : List.of("change-me-to-a-long-random-secret-of-32-chars-or-more", "a".repeat(64),
                "abababababababababababababababab", "")) {
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                    () -> new com.mjt.Football_Tournament_Management_System.security.JwtService(secret, 60), secret);
        }
    }

    private org.springframework.test.web.servlet.ResultActions uploadCrest(long team, byte[] data, String type, String token) throws Exception {
        return mvc.perform(multipart("/api/teams/" + team + "/crest").file(new MockMultipartFile("file", "escudo", type, data))
                .with(r -> { r.setMethod("PUT"); return r; })
                .header("Authorization", "Bearer " + token));
    }

    private static byte[] png(int w, int h) throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    /** Rewrites the width and height in the PNG header (IHDR, right after the 8-byte signature) and fixes its checksum */
    private static byte[] withSize(byte[] png, int width, int height) {
        byte[] b = png.clone();
        java.nio.ByteBuffer.wrap(b, 16, 8).putInt(width).putInt(height);
        var crc = new CRC32();
        crc.update(b, 12, 17); // chunk type "IHDR" + 13 data bytes
        java.nio.ByteBuffer.wrap(b, 29, 4).putInt((int) crc.getValue());
        return b;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = java.util.Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }
}
