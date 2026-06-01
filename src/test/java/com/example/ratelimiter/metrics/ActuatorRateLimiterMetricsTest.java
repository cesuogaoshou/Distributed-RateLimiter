package com.example.ratelimiter.metrics;

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
class ActuatorRateLimiterMetricsTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void actuatorExposesRateLimiterGauge() throws Exception {
        mockMvc.perform(get("/actuator/metrics/ratelimiter.limiters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("ratelimiter.limiters"))
                .andExpect(jsonPath("$.measurements[0].value").exists());
    }
}
