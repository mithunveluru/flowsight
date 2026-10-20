package com.flowsight.ml.forecast;

import com.flowsight.entity.User;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/forecast")
@RequiredArgsConstructor
@Validated
public class ForecastController {

    private final ForecastService forecastService;

    // current month plus the next `months` months, for income, spend, net and each category
    @GetMapping
    public ResponseEntity<ForecastResponse> forecast(
        @RequestParam(defaultValue = "3") @Min(1) @Max(6) int months,
        @AuthenticationPrincipal User user
    ) {
        MonthlyHistory h = forecastService.history(user.getId(), LocalDate.now());
        return ResponseEntity.ok(ForecastResponse.of(forecastService.forecast(user.getId(), h, months + 1), h));
    }
}
