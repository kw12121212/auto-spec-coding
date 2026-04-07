package org.specdriven.agent.registry;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.BitSet;

/**
 * Parses standard 5-field cron expressions and computes next fire times.
 * Field order: minute hour day-of-month month day-of-week
 * Supports: wildcard, ranges (1-5), steps (star/5), lists (1,3,5).
 */
public final class CronExpression {

    private final String expression;
    private final BitSet minutes;
    private final BitSet hours;
    private final BitSet daysOfMonth;
    private final BitSet months;
    private final BitSet daysOfWeek;

    // Field boundaries: min, max (inclusive)
    private static final int[] MIN = {0, 0, 1, 1, 0};
    private static final int[] MAX = {59, 23, 31, 12, 6};

    private CronExpression(String expression, BitSet minutes, BitSet hours,
                           BitSet daysOfMonth, BitSet months, BitSet daysOfWeek) {
        this.expression = expression;
        this.minutes = minutes;
        this.hours = hours;
        this.daysOfMonth = daysOfMonth;
        this.months = months;
        this.daysOfWeek = daysOfWeek;
    }

    /**
     * Parses a 5-field cron expression.
     *
     * @param expression cron expression string
     * @return parsed CronExpression
     * @throws IllegalArgumentException if the expression is invalid
     */
    public static CronExpression parse(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Cron expression must not be blank");
        }
        String[] fields = expression.trim().split("\\s+");
        if (fields.length != 5) {
            throw new IllegalArgumentException(
                    "Expected 5 fields (M H DoM Mon DoW), got " + fields.length + ": " + expression);
        }

        BitSet[] bitSets = new BitSet[5];
        for (int i = 0; i < 5; i++) {
            bitSets[i] = parseField(fields[i], MIN[i], MAX[i], fieldName(i));
        }
        return new CronExpression(expression.trim(), bitSets[0], bitSets[1], bitSets[2], bitSets[3], bitSets[4]);
    }

    /**
     * Computes the next fire time after the given timestamp.
     *
     * @param expression     cron expression string
     * @param afterTimestamp  epoch millis
     * @return next fire time as epoch millis
     */
    public static long nextFireTime(String expression, long afterTimestamp) {
        return parse(expression).nextFireTime(afterTimestamp);
    }

    /**
     * Computes the next fire time after the given timestamp using this parsed expression.
     *
     * @param afterTimestamp epoch millis
     * @return next fire time as epoch millis
     */
    public long nextFireTime(long afterTimestamp) {
        ZonedDateTime dt = Instant.ofEpochMilli(afterTimestamp).atZone(ZoneId.systemDefault());
        // Start from the next minute
        dt = dt.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1);

        // Brute-force search — cap at 4 years to prevent infinite loops
        ZonedDateTime limit = dt.plusYears(4);
        while (dt.isBefore(limit)) {
            if (!months.get(dt.getMonthValue())) {
                dt = dt.withMonth(dt.getMonthValue() + 1).withDayOfMonth(1)
                        .truncatedTo(ChronoUnit.DAYS).withHour(0).withMinute(0);
                continue;
            }
            if (!daysOfMonth.get(dt.getDayOfMonth())) {
                dt = dt.plusDays(1).truncatedTo(ChronoUnit.DAYS).withHour(0).withMinute(0);
                continue;
            }
            int dow = dt.getDayOfWeek().getValue() % 7; // Sunday=0
            if (!daysOfWeek.get(dow)) {
                dt = dt.plusDays(1).truncatedTo(ChronoUnit.DAYS).withHour(0).withMinute(0);
                continue;
            }
            if (!hours.get(dt.getHour())) {
                dt = dt.plusHours(1).truncatedTo(ChronoUnit.HOURS).withMinute(0);
                continue;
            }
            if (!minutes.get(dt.getMinute())) {
                dt = dt.plusMinutes(1);
                continue;
            }
            return dt.toInstant().toEpochMilli();
        }
        throw new IllegalStateException("No valid fire time found within 4 years for: " + expression);
    }

    @Override
    public String toString() {
        return expression;
    }

    // -------------------------------------------------------------------------
    // Field parsing
    // -------------------------------------------------------------------------

    private static BitSet parseField(String field, int min, int max, String name) {
        BitSet bits = new BitSet(max + 1);
        for (String part : field.split(",")) {
            parsePart(part.trim(), min, max, name, bits);
        }
        return bits;
    }

    private static void parsePart(String part, int min, int max, String name, BitSet bits) {
        int step = 1;
        String rangePart;

        int slash = part.indexOf('/');
        if (slash >= 0) {
            step = Integer.parseInt(part.substring(slash + 1));
            if (step <= 0) {
                throw new IllegalArgumentException("Step must be positive in field " + name + ": " + part);
            }
            rangePart = part.substring(0, slash);
        } else {
            rangePart = part;
        }

        int rangeMin, rangeMax;
        if ("*".equals(rangePart)) {
            rangeMin = min;
            rangeMax = max;
        } else {
            int dash = rangePart.indexOf('-');
            if (dash >= 0) {
                rangeMin = Integer.parseInt(rangePart.substring(0, dash));
                rangeMax = Integer.parseInt(rangePart.substring(dash + 1));
            } else {
                rangeMin = rangeMax = Integer.parseInt(rangePart);
            }
        }

        if (rangeMin < min || rangeMax > max || rangeMin > rangeMax) {
            throw new IllegalArgumentException(
                    "Value out of range [" + min + "-" + max + "] in field " + name + ": " + part);
        }

        for (int i = rangeMin; i <= rangeMax; i += step) {
            bits.set(i);
        }
    }

    private static String fieldName(int index) {
        return switch (index) {
            case 0 -> "minute";
            case 1 -> "hour";
            case 2 -> "day-of-month";
            case 3 -> "month";
            case 4 -> "day-of-week";
            default -> "field-" + index;
        };
    }
}
