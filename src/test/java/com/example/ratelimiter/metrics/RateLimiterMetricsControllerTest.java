package com.example.ratelimiter.metrics;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RateLimiterMetricsController.class)
class RateLimiterMetricsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RateLimiterMetricsService service;

    @Test
    void statsReturnsLimiterSnapshots() throws Exception {
        when(service.snapshots()).thenReturn(List.of(
                new RateLimiterMetricsSnapshot("orders", 3, 1, 96)
        ));

        mockMvc.perform(get("/api/ratelimit/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("orders"))
                .andExpect(jsonPath("$[0].allowedRequests").value(3))
                .andExpect(jsonPath("$[0].rejectedRequests").value(1))
                .andExpect(jsonPath("$[0].availablePermits").value(96));
    }

    @Test
    void statsReturnsEmptyArrayWhenNoLimitersExist() throws Exception {
        when(service.snapshots()).thenReturn(List.of());

        mockMvc.perform(get("/api/ratelimit/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }
}
