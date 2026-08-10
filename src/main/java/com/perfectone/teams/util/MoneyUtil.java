package com.perfectone.teams.util;

import java.text.DecimalFormat;

/**
 * Formats bounty amounts as US-dollar-style currency strings, e.g. {@code $1,250} or
 * {@code $1,250.50}.
 */
public final class MoneyUtil {

    private static final DecimalFormat WHOLE = new DecimalFormat("#,##0");
    private static final DecimalFormat FRACTIONAL = new DecimalFormat("#,##0.00");

    private MoneyUtil() {
    }

    public static String format(double amount) {
        double rounded = Math.round(amount * 100.0) / 100.0;
        DecimalFormat fmt = rounded == Math.floor(rounded) ? WHOLE : FRACTIONAL;
        return "$" + fmt.format(rounded);
    }
}
