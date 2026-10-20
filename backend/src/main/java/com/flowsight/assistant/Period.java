package com.flowsight.assistant;

import java.time.LocalDate;
import java.util.Locale;

/** The fixed date ranges the assistant can query; the model picks one by name. */
public enum Period {
    THIS_MONTH, LAST_MONTH, LAST_30_DAYS, LAST_90_DAYS, THIS_YEAR;

    public LocalDate from(LocalDate today) {
        return switch (this) {
            case THIS_MONTH -> today.withDayOfMonth(1);
            case LAST_MONTH -> today.minusMonths(1).withDayOfMonth(1);
            case LAST_30_DAYS -> today.minusDays(29);
            case LAST_90_DAYS -> today.minusDays(89);
            case THIS_YEAR -> today.withDayOfYear(1);
        };
    }

    public LocalDate to(LocalDate today) {
        return this == LAST_MONTH ? today.withDayOfMonth(1).minusDays(1) : today;
    }

    public static Period parse(String value, Period fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
