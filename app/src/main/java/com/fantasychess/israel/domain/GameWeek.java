package com.fantasychess.israel.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;

/**
 * The fantasy game-week. Israeli league rounds are usually played once a
 * week, so a game-week runs Sunday..Saturday (the Israeli week).
 */
public final class GameWeek {

    private GameWeek() {
    }

    /** Stable key for the current week, e.g. 202627 (year * 100 + ISO week). */
    public static int currentWeekKey() {
        LocalDate today = LocalDate.now();
        int week = today.get(WeekFields.ISO.weekOfWeekBasedYear());
        int year = today.get(WeekFields.ISO.weekBasedYear());
        return year * 100 + week;
    }

    public static int weekNumber() {
        return LocalDate.now().get(WeekFields.ISO.weekOfWeekBasedYear());
    }

    public static LocalDate weekStart() {
        return LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
    }

    public static LocalDate weekEnd() {
        return weekStart().plusDays(6);
    }
}
