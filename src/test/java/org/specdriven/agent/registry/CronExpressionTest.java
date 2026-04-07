package org.specdriven.agent.registry;

import org.junit.jupiter.api.Test;

import java.time.*;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class CronExpressionTest {

    // -------------------------------------------------------------------------
    // Parsing: valid expressions
    // -------------------------------------------------------------------------

    @Test
    void parse_wildcardAll() {
        assertDoesNotThrow(() -> CronExpression.parse("* * * * *"));
    }

    @Test
    void parse_range() {
        assertDoesNotThrow(() -> CronExpression.parse("1-5 * * * *"));
    }

    @Test
    void parse_step() {
        assertDoesNotThrow(() -> CronExpression.parse("*/5 * * * *"));
    }

    @Test
    void parse_list() {
        assertDoesNotThrow(() -> CronExpression.parse("1,3,5 * * * *"));
    }

    @Test
    void parse_combined() {
        assertDoesNotThrow(() -> CronExpression.parse("0 9 * * 1-5"));
    }

    // -------------------------------------------------------------------------
    // Parsing: invalid expressions
    // -------------------------------------------------------------------------

    @Test
    void parse_null_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> CronExpression.parse(null));
    }

    @Test
    void parse_blank_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> CronExpression.parse("  "));
    }

    @Test
    void parse_wrongFieldCount_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> CronExpression.parse("* * *"));
    }

    @Test
    void parse_valueOutOfRange_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> CronExpression.parse("60 * * * *"));
    }

    // -------------------------------------------------------------------------
    // nextFireTime: wildcard (every minute)
    // -------------------------------------------------------------------------

    @Test
    void nextFireTime_wildcard_returnsNextMinute() {
        long now = System.currentTimeMillis();
        long next = CronExpression.nextFireTime("* * * * *", now);
        assertTrue(next > now);
        assertTrue(next - now <= 60_000L + 1000); // within ~1 minute
    }

    // -------------------------------------------------------------------------
    // nextFireTime: specific minute
    // -------------------------------------------------------------------------

    @Test
    void nextFireTime_specificMinute() {
        // "30 * * * *" = minute 30 of every hour
        long now = ZonedDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneId.systemDefault())
                .toInstant().toEpochMilli();
        long next = CronExpression.nextFireTime("30 * * * *", now);
        ZonedDateTime nextDt = Instant.ofEpochMilli(next).atZone(ZoneId.systemDefault());
        assertEquals(30, nextDt.getMinute());
        assertEquals(10, nextDt.getHour());
    }

    // -------------------------------------------------------------------------
    // nextFireTime: every 5 minutes
    // -------------------------------------------------------------------------

    @Test
    void nextFireTime_every5Minutes() {
        long now = ZonedDateTime.of(2026, 1, 1, 10, 7, 0, 0, ZoneId.systemDefault())
                .toInstant().toEpochMilli();
        long next = CronExpression.nextFireTime("*/5 * * * *", now);
        ZonedDateTime nextDt = Instant.ofEpochMilli(next).atZone(ZoneId.systemDefault());
        assertEquals(10, nextDt.getMinute());
    }

    // -------------------------------------------------------------------------
    // nextFireTime: specific hour and minute
    // -------------------------------------------------------------------------

    @Test
    void nextFireTime_specificHourAndMinute() {
        // "0 9 * * *" = 9:00 every day
        long now = ZonedDateTime.of(2026, 1, 1, 8, 30, 0, 0, ZoneId.systemDefault())
                .toInstant().toEpochMilli();
        long next = CronExpression.nextFireTime("0 9 * * *", now);
        ZonedDateTime nextDt = Instant.ofEpochMilli(next).atZone(ZoneId.systemDefault());
        assertEquals(9, nextDt.getHour());
        assertEquals(0, nextDt.getMinute());
        assertEquals(1, nextDt.getDayOfMonth());
    }

    // -------------------------------------------------------------------------
    // nextFireTime: weekdays only
    // -------------------------------------------------------------------------

    @Test
    void nextFireTime_weekdaysOnly() {
        // "0 9 * * 1-5" = 9:00 Mon-Fri
        // 2026-01-03 is Saturday
        long sat = ZonedDateTime.of(2026, 1, 3, 10, 0, 0, 0, ZoneId.systemDefault())
                .toInstant().toEpochMilli();
        long next = CronExpression.nextFireTime("0 9 * * 1-5", sat);
        ZonedDateTime nextDt = Instant.ofEpochMilli(next).atZone(ZoneId.systemDefault());
        assertEquals(1, nextDt.getDayOfWeek().getValue()); // Monday
        assertEquals(0, nextDt.getMinute());
    }

    // -------------------------------------------------------------------------
    // nextFireTime: list of minutes
    // -------------------------------------------------------------------------

    @Test
    void nextFireTime_listOfMinutes() {
        // "0,30 * * * *" = :00 and :30 every hour
        long now = ZonedDateTime.of(2026, 1, 1, 10, 15, 0, 0, ZoneId.systemDefault())
                .toInstant().toEpochMilli();
        long next = CronExpression.nextFireTime("0,30 * * * *", now);
        ZonedDateTime nextDt = Instant.ofEpochMilli(next).atZone(ZoneId.systemDefault());
        assertEquals(30, nextDt.getMinute());
        assertEquals(10, nextDt.getHour());
    }
}
