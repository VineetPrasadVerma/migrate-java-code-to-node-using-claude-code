package com.company.orders.util;

/**
 * Monetary rounding utilities.
 *
 * LEGACY NOTE: This system uses double for monetary arithmetic, which can accumulate
 * floating-point rounding errors in long chains of calculations. The round() method
 * mitigates this by rounding intermediate results to 2 decimal places after each
 * operation. A production financial system should use BigDecimal with HALF_UP rounding.
 *
 * The round() method uses HALF_UP semantics: 0.005 rounds to 0.01, not 0.00.
 * This matches the rounding behaviour expected by the test suite and known clients.
 */
public final class MoneyUtils {

    private MoneyUtils() {}

    /**
     * Rounds a monetary value to 2 decimal places using HALF_UP rounding.
     */
    public static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * Sums a list of doubles, rounding the accumulated total after each addition
     * to prevent floating-point drift in long lists.
     */
    public static double sumRounded(double... values) {
        double total = 0.0;
        for (double v : values) {
            total = round(total + v);
        }
        return total;
    }
}
