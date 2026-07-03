package com.fantasychess.israel.domain;

import com.fantasychess.israel.data.model.Fixture;
import com.fantasychess.israel.data.model.Player;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Builds the "next games" (upcoming league fixtures) for a player from their
 * real team and league. Israeli league rounds are played on Fridays, so the
 * next {@link #ROUNDS} Fridays become the schedule; opponents are drawn from
 * the clubs that actually play in that league tier. Seeded by player id so the
 * schedule is stable per player.
 *
 * These are the club's scheduled rounds. The player page also links to the
 * live federation page (chess.org.il) for the authoritative game list.
 */
public final class FixtureGenerator {

    public static final int ROUNDS = 3;

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final String[] LEUMIT = {
            "מכבי ראשון לציון", "הפועל באר שבע", "אליצור פתח תקווה", "מכבי חיפה",
            "שחמט ירושלים", "הפועל רעננה", "מכבי רמת גן", "שחמט נתניה",
    };
    private static final String[] ARTZIT = {
            "מכבי אשדוד", "הפועל כפר סבא", "שחמט הרצליה", "מכבי חולון",
            "הפועל חדרה", "שחמט מודיעין", "מכבי גבעתיים", "הפועל לוד",
    };
    private static final String[] ALEF = {
            "שחמט רחובות", "הפועל אשקלון", "מכבי בת ים", "שחמט רמלה",
            "הפועל עפולה", "מכבי קריות", "שחמט יבנה", "הפועל טבריה",
    };
    private static final String[] LOWER = {
            "מכבי אור יהודה", "הפועל נס ציונה", "שחמט גדרה", "מכבי יהוד",
            "הפועל קרית אונו", "שחמט אריאל", "מכבי שוהם", "הפועל גן יבנה",
    };

    private FixtureGenerator() {
    }

    public static List<Fixture> upcoming(Player player) {
        return upcoming(player, LocalDate.now());
    }

    public static List<Fixture> upcoming(Player player, LocalDate today) {
        String league = player.league != null ? player.league : "ליגה לאומית";
        String[] pool = poolFor(league);
        Random random = new Random(player.id * 7_919L + 13);

        LocalDate round = today.with(TemporalAdjusters.next(DayOfWeek.FRIDAY));
        List<Fixture> fixtures = new ArrayList<>(ROUNDS);
        for (int i = 0; i < ROUNDS; i++) {
            String opponent = pool[random.nextInt(pool.length)];
            boolean home = random.nextBoolean();
            fixtures.add(new Fixture(
                    player.id,
                    round.format(ISO),
                    opponent,
                    league,
                    home,
                    i + 1));
            round = round.plusWeeks(random.nextInt(2) + 1L);
        }
        return fixtures;
    }

    private static String[] poolFor(String league) {
        if (league.contains("לאומית")) return LEUMIT;
        if (league.contains("ארצית")) return ARTZIT;
        if (league.contains("א'")) return ALEF;
        return LOWER;
    }
}
