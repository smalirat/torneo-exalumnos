package com.mjt.Football_Tournament_Management_System;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MockMvc helpers shared by the API test classes (H2, profile "test", admin/admin123). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class ApiTestSupport {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    String admin() throws Exception { return login("admin", "admin123"); }

    String login(String user, String pass) throws Exception {
        return read(post("/api/auth/login", "{\"username\":\"" + user + "\",\"password\":\"" + pass + "\"}", null)
                .andExpect(status().isOk())).get("token").asText();
    }

    /** Registers a fresh captain user and returns its token. */
    String newUser() throws Exception {
        String user = "u" + UUID.randomUUID().toString().substring(0, 8);
        return read(post("/api/auth/register", "{\"username\":\"" + user + "\",\"password\":\"password123\",\"teamName\":\"Team "
                + UUID.randomUUID() + "\"}", null).andExpect(status().isCreated())).get("token").asText();
    }

    long newTournament(String token) throws Exception {
        var today = LocalDate.now();
        return read(post("/api/admin/tournaments", "{\"name\":\"Apertura\",\"registrationStart\":\"" + today.minusDays(1)
                + "\",\"registrationEnd\":\"" + today.plusDays(10) + "\"}", token).andExpect(status().isOk()))
                .get("id").asLong();
    }

    JsonNode newCategory(String token, long tournamentId, String name) throws Exception {
        return read(post("/api/admin/tournaments/" + tournamentId + "/categories", "{\"name\":\"" + name + "\"}", token)
                .andExpect(status().isOk()));
    }

    long newZone(String token, long categoryId, String name) throws Exception {
        return read(post("/api/admin/categories/" + categoryId + "/zones", "{\"name\":\"" + name + "\"}", token)
                .andExpect(status().isOk())).get("id").asLong();
    }

    /**
     * A team: for a captain, the one they signed up with (captains have a single team); for the admin, a new one.
     * Approved right away (a captain's team starts in review).
     */
    long newTeam(String token) throws Exception {
        var mine = read(get("/api/teams", token));
        boolean captain = "CAPTAIN".equals(read(get("/api/auth/me", token)).get("role").asText());
        long id = captain && mine.size() > 0 ? mine.get(0).get("id").asLong()
                : read(post("/api/teams/register", "{\"name\":\"Team " + UUID.randomUUID() + "\"}", token)
                        .andExpect(status().isOk())).get("id").asLong();
        post("/api/admin/requests/teams/" + id + "/approve-all", "", admin()).andExpect(status().isOk());
        return id;
    }

    /** Registers the team in the tournament (no zone yet) and returns the registration id. */
    long enroll(String token, long teamId, long tournamentId) throws Exception {
        return read(post("/api/teams/" + teamId + "/tournaments/" + tournamentId + "/register", "", token)
                .andExpect(status().isOk())).get("id").asLong();
    }

    ResultActions get(String url, String token) throws Exception { return send(MockMvcRequestBuilders.get(url), null, token); }
    ResultActions post(String url, String body, String token) throws Exception { return send(MockMvcRequestBuilders.post(url), body, token); }
    ResultActions put(String url, String body, String token) throws Exception { return send(MockMvcRequestBuilders.put(url), body, token); }
    ResultActions patch(String url, String body, String token) throws Exception { return send(MockMvcRequestBuilders.patch(url), body, token); }
    ResultActions delete(String url, String token) throws Exception { return send(MockMvcRequestBuilders.delete(url), null, token); }

    private ResultActions send(MockHttpServletRequestBuilder req, String body, String token) throws Exception {
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) req.header("Authorization", "Bearer " + token);
        return mvc.perform(req);
    }

    JsonNode read(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }
}
