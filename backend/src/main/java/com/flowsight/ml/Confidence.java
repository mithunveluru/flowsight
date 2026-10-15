package com.flowsight.ml;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class Confidence {

    private Confidence() {
    }

    // DECIMAL(5,4) column; clamp so a misbehaving model cannot fail the insert
    static BigDecimal of(Double value) {
        if (value == null || value.isNaN()) return null;
        double clamped = Math.max(0.0, Math.min(1.0, value));
        return BigDecimal.valueOf(clamped).setScale(4, RoundingMode.HALF_UP);
    }
}
