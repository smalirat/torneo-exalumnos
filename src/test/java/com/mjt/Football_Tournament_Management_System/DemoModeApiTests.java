package com.mjt.Football_Tournament_Management_System;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The public demo: a "demo" admin whose login the site shows, so anyone can try the panel */
@TestPropertySource(properties = {"app.demo.enabled=true", "app.demo.password=demo-publica-2026"})
class DemoModeApiTests extends ApiTestSupport {

    @Test
    void theDemoLoginIsPublishedAndWorksAsAnAdmin() throws Exception {
        get("/api/public/league", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demo.username").value("demo"))
                .andExpect(jsonPath("$.demo.password").value("demo-publica-2026"));
        String token = login("demo", "demo-publica-2026");
        get("/api/auth/me", token).andExpect(jsonPath("$.role").value("ADMIN"));
        get("/api/admin/requests", token).andExpect(status().isOk());
        // The real admin is still there, with its own private password
        get("/api/auth/me", admin()).andExpect(jsonPath("$.username").value("admin"));
    }
}
