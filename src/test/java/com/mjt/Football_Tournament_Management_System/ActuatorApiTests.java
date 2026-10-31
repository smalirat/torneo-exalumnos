package com.mjt.Football_Tournament_Management_System;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.test.context.TestPropertySource;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Prometheus scrapes without a token; nothing else of the actuator is exposed. Empty management port = same as the app here, so MockMvc reaches it. */
@AutoConfigureObservability // tests turn metrics export off by default
@TestPropertySource(properties = "management.server.port=")
class ActuatorApiTests extends ApiTestSupport {

    @Test
    void prometheusAndHealthAreOpen() throws Exception {
        get("/api/public/league", null); // at least one request, so its timer exists
        get("/actuator/prometheus", null)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("jvm_memory_used_bytes")))
                .andExpect(content().string(containsString("http_server_requests_seconds_bucket")))
                .andExpect(content().string(containsString("hikaricp_connections_active")));
        get("/actuator/health", null).andExpect(status().isOk());
    }

    @Test
    void otherEndpointsAreNotExposed() throws Exception {
        get("/actuator/env", null).andExpect(status().is4xxClientError());
        get("/actuator/heapdump", null).andExpect(status().is4xxClientError());
    }
}
