package com.example.ratelimiter.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DemoRateLimiterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void demoEndpointCreatesVisibleRateLimiterStats() throws Exception {
        mockMvc.perform(get("/demo/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("demo:orders"))
                .andExpect(jsonPath("$.status").value("created"));

        mockMvc.perform(get("/api/ratelimit/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.key == 'demo:orders')]").exists())
                .andExpect(jsonPath("$[?(@.key == 'demo:orders')].allowedRequests").value(1));
    }
}
