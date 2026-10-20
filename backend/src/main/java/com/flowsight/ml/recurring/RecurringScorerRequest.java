package com.flowsight.ml.recurring;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

/** Body of POST /v1/capabilities/recurring-scorer/predict: one entry per merchant group. */
public record RecurringScorerRequest(@JsonProperty("as_of") String asOf, List<Group> groups) {

    public static RecurringScorerRequest of(LocalDate asOf, List<Group> groups) {
        return new RecurringScorerRequest(asOf.toString(), groups);
    }

    public static final int MAX_GROUPS  = 300;
    public static final int MAX_CHARGES = 120;

    /** days: charge dates as days relative to asOf (all <= 0), oldest first; category may be null. */
    public record Group(List<Integer> days, List<Double> amounts, String category) {
    }
}
