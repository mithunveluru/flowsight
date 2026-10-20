package com.flowsight.dto.simulation;

/**
 * Inputs the simulation assumes rather than measures. annualReturnSource is DEFAULT or USER;
 * the return only feeds opportunity-cost figures, never the savings paths.
 */
public record SimulationAssumptions(double annualReturnPercent, String annualReturnSource, String label) {

    public static final double DEFAULT_ANNUAL_RETURN_PERCENT = 8.0;

    public static SimulationAssumptions of(java.math.BigDecimal requested) {
        boolean user = requested != null;
        double rate = user ? requested.doubleValue() : DEFAULT_ANNUAL_RETURN_PERCENT;
        return new SimulationAssumptions(rate, user ? "USER" : "DEFAULT",
            "Assumed " + format(rate) + "% annual return on money invested instead. An assumption, not a forecast.");
    }

    public double annualRate() {
        return annualReturnPercent / 100.0;
    }

    public static String format(double percent) {
        return percent == Math.rint(percent) ? String.valueOf((long) percent) : String.valueOf(percent);
    }
}
