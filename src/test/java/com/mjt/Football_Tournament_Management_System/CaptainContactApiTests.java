package com.mjt.Football_Tournament_Management_System;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CaptainContactApiTests extends ApiTestSupport {

    @Test
    void captainsGiveEmailAndPhoneWhenSigningUpAndCanChangeThem() throws Exception {
        String user = "c" + UUID.randomUUID().toString().substring(0, 8);
        String team = "Team " + UUID.randomUUID();

        // Both required, and valid
        post("/api/auth/register", "{\"username\":\"" + user + "\",\"password\":\"password123\",\"teamName\":\"" + team + "\"}", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.phone").exists());
        post("/api/auth/register", "{\"username\":\"" + user + "\",\"password\":\"password123\",\"email\":\"no-es-mail\",\"phone\":\"llamame\",\"teamName\":\""
                + team + "\"}", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.phone").exists());

        String token = read(post("/api/auth/register", "{\"username\":\"" + user + "\",\"password\":\"password123\",\"email\":\"juan@mail.com\","
                + "\"phone\":\"+54 9 11 5555-1234\",\"teamName\":\"" + team + "\"}", null).andExpect(status().isCreated())).get("token").asText();
        get("/api/auth/me", token)
                .andExpect(jsonPath("$.email").value("juan@mail.com"))
                .andExpect(jsonPath("$.phone").value("+54 9 11 5555-1234"));

        // "Mis datos"
        put("/api/auth/me", "{\"email\":\"juan.perez@mail.com\",\"phone\":\"(011) 4555-1234\"}", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("juan.perez@mail.com"));
        put("/api/auth/me", "{\"email\":\"\",\"phone\":\"(011) 4555-1234\"}", token).andExpect(status().isBadRequest());
        put("/api/auth/me", "{\"email\":\"a@b.com\",\"phone\":\"1\"}", null).andExpect(status().isUnauthorized());

        // The admin sees it next to the team
        get("/api/teams", admin())
                .andExpect(jsonPath("$[?(@.captainUsername == '" + user + "')].captainEmail").value("juan.perez@mail.com"))
                .andExpect(jsonPath("$[?(@.captainUsername == '" + user + "')].captainPhone").value("(011) 4555-1234"));
    }
}
