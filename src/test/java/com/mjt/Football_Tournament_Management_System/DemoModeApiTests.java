package com.mjt.Football_Tournament_Management_System;

import com.mjt.Football_Tournament_Management_System.config.AdminBootstrap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The public demo: a "demo" admin and a few captains with a known password (published in the README) */
@TestPropertySource(properties = {"app.demo.enabled=true", "app.demo.password=demo-publica-2026",
        "app.demo.captains=capitan-demo-test, no-existe"})
class DemoModeApiTests extends ApiTestSupport {
    @Autowired AdminBootstrap bootstrap;

    @Test
    void theDemoAdminAndTheListedCaptainsUseTheDemoPassword() throws Exception {
        String token = login("demo", "demo-publica-2026");
        get("/api/auth/me", token).andExpect(jsonPath("$.role").value("ADMIN"));
        get("/api/admin/requests", token).andExpect(status().isOk());

        // A captain with their own password; at the next start (here: run again) they get the demo password too
        post("/api/auth/register", "{\"username\":\"capitan-demo-test\",\"password\":\"propia-1234\",\"email\":\"cap@demo.example\","
                + "\"phone\":\"11 5555-1234\",\"teamName\":\"Demo Test FC\"}", null).andExpect(status().isCreated());
        bootstrap.run(null);
        get("/api/auth/me", login("capitan-demo-test", "demo-publica-2026")).andExpect(jsonPath("$.role").value("CAPTAIN"));

        // Nothing is published on the site, and the real admin keeps its own password
        get("/api/public/league", null).andExpect(jsonPath("$.demo").doesNotExist());
        get("/api/auth/me", admin()).andExpect(jsonPath("$.username").value("admin"));
    }
}
