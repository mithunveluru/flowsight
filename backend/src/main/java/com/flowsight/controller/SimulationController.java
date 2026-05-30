package com.flowsight.controller;

import com.flowsight.analytics.simulation.SimulationService;
import com.flowsight.dto.simulation.ScenarioRequest;
import com.flowsight.dto.simulation.SimulationResult;
import com.flowsight.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/simulate")
@RequiredArgsConstructor
public class SimulationController {

    private final SimulationService simulationService;

    /**
     * Runs a scenario against the authenticated user's actual financial baseline.
     * Stateless: no persistence, idempotent on identical inputs.
     */
    @PostMapping
    public ResponseEntity<SimulationResult> simulate(
        @Valid @RequestBody ScenarioRequest scenario,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(simulationService.simulate(user.getId(), scenario));
    }
}
