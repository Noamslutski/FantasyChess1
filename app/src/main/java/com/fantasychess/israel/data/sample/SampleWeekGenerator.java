package com.fantasychess.israel.data.sample;

import com.fantasychess.israel.data.model.GameResult;
import com.fantasychess.israel.data.model.Player;
import com.fantasychess.israel.data.model.WeekGame;
import com.fantasychess.israel.domain.GameWeek;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Deterministic demo games for the current week, used when the live games
 * feed is unavailable. Seeded by (playerId, weekKey) so every player gets a
 * stable schedule that still changes from week to week, and results follow
 * the Elo expected score for the rating difference — strong players really
 * do win more.
 */
public final class SampleWeekGenerator {

    private static final String[] FIRST_NAMES = {
            "אורי", "רועי", "אלון", "דנה", "מיכל", "יובל", "עמית", "נדב",
            "טל", "שקד", "אביב", "כרמל", "אסף", "ליה", "מתן", "אלה",
    };
    private static final String[] LAST_NAMES = {
            "ברקוביץ", "שמעוני", "אזולאי", "וייס", "קפלן", "חזן",
            "אורן", "סגל", "דיין", "אשכנזי", "מלכה", "רביד",
    };
    private static final String[] CLUBS = {
            "מועדון ראשון לציון", "שחמט חיפה", "מועדון ירושלים", "שחמט נתניה",
            "מועדון רמת גן", "שחמט אשדוד", "מועדון רחובות", "שחמט כפר סבא",
    };
    private static final String[] COMPETITIONS = {
            "ליגה לאומית", "ליגת העל", "גביע האיגוד", "אליפות מחוזית",
    };

    private SampleWeekGenerator() {
    }

    public static List<WeekGame> gamesFor(Player player) {
        return gamesFor(player, GameWeek.currentWeekKey());
    }

    public static List<WeekGame> gamesFor(Player player, int weekKey) {
        Random random = new Random(player.id * 1_000_003L + weekKey);
        int roll = random.nextInt(100);
        int gameCount;
        if (roll < 15) gameCount = 0;
        else if (roll < 50) gameCount = 1;
        else if (roll < 85) gameCount = 2;
        else gameCount = 3;

        LocalDate weekStart = GameWeek.weekStart();
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;

        List<WeekGame> games = new ArrayList<>(gameCount);
        for (int i = 0; i < gameCount; i++) {
            int opponentRating = clamp(
                    player.nationalRating - 280 + random.nextInt(561), 1200, 2650);
            games.add(new WeekGame(
                    player.id,
                    weekStart.plusDays(random.nextInt(7)).format(formatter),
                    pick(FIRST_NAMES, random) + " " + pick(LAST_NAMES, random),
                    pick(CLUBS, random),
                    opponentRating,
                    (i + random.nextInt(2)) % 2 == 0,
                    rollResult(player.nationalRating, opponentRating, random),
                    pick(COMPETITIONS, random)));
        }
        games.sort(Comparator.comparing(g -> g.dateIso));
        return games;
    }

    /** Elo expected score decides win/draw/loss probabilities. */
    private static GameResult rollResult(int rating, int opponentRating, Random random) {
        double expected = 1.0 / (1.0 + Math.pow(10.0, (opponentRating - rating) / 400.0));
        double drawChance = 0.28;
        double winChance = expected * (1 - drawChance);
        double roll = random.nextDouble();
        if (roll < winChance) return GameResult.WIN;
        if (roll < winChance + drawChance) return GameResult.DRAW;
        return GameResult.LOSS;
    }

    private static String pick(String[] pool, Random random) {
        return pool[random.nextInt(pool.length)];
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
