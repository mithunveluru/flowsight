package com.flowsight.controller;

import com.flowsight.analytics.simulation.SimulationService;
import com.flowsight.dto.simulation.FlexibilityScore;
import com.flowsight.dto.simulation.ScenarioRequest;
import com.flowsight.dto.simulation.SimulationResult;
import com.flowsight.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/simulate")
@RequiredArgsConstructor
public class SimulationController {

    private final SimulationService simulationService;

    // run a scenario against the user's baseline; stateless, idempotent
    @PostMapping
    public ResponseEntity<SimulationResult> simulate(
        @Valid @RequestBody ScenarioRequest scenario,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(simulationService.simulate(user.getId(), scenario));
    }

    // current flexibility, no scenario; backs the desktop companion's gauge
    @GetMapping("/flexibility")
    public ResponseEntity<FlexibilityScore> currentFlexibility(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(simulationService.currentFlexibility(user.getId()));
    }
}
