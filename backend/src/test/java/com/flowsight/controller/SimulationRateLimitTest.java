package com.flowsight.controller;

import com.flowsight.analytics.simulation.SimulationService;
import com.flowsight.dto.simulation.ScenarioRequest;
import com.flowsight.dto.simulation.ScenarioType;
import com.flowsight.entity.Role;
import com.flowsight.entity.User;
import com.flowsight.security.RateLimiter;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doThrow;

class SimulationRateLimitTest {

    private final SimulationService service = mock(SimulationService.class);
    private final RateLimiter limiter = mock(RateLimiter.class);
    private final SimulationController controller = new SimulationController(service, limiter);
    private final User user = User.builder().id(UUID.randomUUID()).email("sim@example.com").passwordHash("x").role(Role.USER).build();

    private static ScenarioRequest scenario() {
        ScenarioRequest r = new ScenarioRequest();
        r.setType(ScenarioType.values()[0]);
        r.setAmount(new BigDecimal("100"));
        return r;
    }

    @Test
    void runsAreCountedPerUser() {
        ScenarioRequest r = scenario();
        controller.simulate(r, user);
        verify(limiter).check(eq("simulate:" + user.getId()), eq(120), eq(Duration.ofMinutes(1)));
        verify(service).simulate(user.getId(), r);
    }

    @Test
    void overTheLimitNothingIsComputed() {
        doThrow(new RuntimeException("429")).when(limiter).check(eq("simulate:" + user.getId()), eq(120), eq(Duration.ofMinutes(1)));
        try {
            controller.simulate(scenario(), user);
        } catch (RuntimeException expected) {
            // limiter refused
        }
        verifyNoInteractions(service);
    }
}
