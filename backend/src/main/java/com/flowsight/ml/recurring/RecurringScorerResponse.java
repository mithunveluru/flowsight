package com.flowsight.ml.recurring;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** The result object of ml-service's recurring-scorer: one score per request group, in order. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RecurringScorerResponse(List<Score> scores) {

    /** Day offsets are relative to the request's asOf; period is a RecurringPeriod name or null. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Score(
        double probability,
        boolean recurring,
        String period,
        @JsonProperty("interval_days") double intervalDays,
        @JsonProperty("next_in_days") Integer nextInDays,
        @JsonProperty("next_low_in_days") Integer nextLowInDays,
        @JsonProperty("next_high_in_days") Integer nextHighInDays,
        double amount,
        @JsonProperty("charges_per_period") int chargesPerPeriod
    ) {
    }
}
