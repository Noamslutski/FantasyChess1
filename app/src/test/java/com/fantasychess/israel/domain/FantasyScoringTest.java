package com.fantasychess.israel.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.fantasychess.israel.data.model.GameResult;
import com.fantasychess.israel.data.model.Gender;
import com.fantasychess.israel.data.model.Player;
import com.fantasychess.israel.data.model.Rarity;
import com.fantasychess.israel.data.model.WeekGame;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class FantasyScoringTest {

    private static final Player PLAYER =
            new Player(1, "טל לוי", Gender.BOY, 2000, null, null, "הפועל פתח תקווה");

    private static WeekGame game(GameResult result, int opponentRating) {
        return new WeekGame(1, "2026-06-29", "יריב", "מועדון", opponentRating,
                true, result, "ליגה");
    }

    @Test
    public void winAgainstEqualOpponentScoresBase() {
        assertEquals(60, FantasyScoring.gamePoints(2000, game(GameResult.WIN, 2000)));
    }

    @Test
    public void upsetWinPaysStrengthAndBonus() {
        // +200 opponent: 60 base + 10 strength + 20 upset = 90
        assertEquals(90, FantasyScoring.gamePoints(2000, game(GameResult.WIN, 2200)));
    }

    @Test
    public void lossStillPaysParticipation() {
        assertEquals(10, FantasyScoring.gamePoints(2000, game(GameResult.LOSS, 2400)));
    }

    @Test
    public void unbeatenWeekAddsBonus() {
        List<WeekGame> twoWins = Arrays.asList(
                game(GameResult.WIN, 2000), game(GameResult.DRAW, 2000));
        assertEquals(60 + 30 + FantasyScoring.UNBEATEN_BONUS,
                FantasyScoring.weekPoints(PLAYER, twoWins));
    }

    @Test
    public void emptyWeekScoresZero() {
        assertEquals(0, FantasyScoring.weekPoints(PLAYER, Collections.emptyList()));
    }

    @Test
    public void captainAndRarityMultiply() {
        List<WeekGame> oneWin = Collections.singletonList(game(GameResult.WIN, 2000));
        int base = FantasyScoring.slotPoints(PLAYER, oneWin, Rarity.LIMITED, false);
        int unique = FantasyScoring.slotPoints(PLAYER, oneWin, Rarity.UNIQUE, false);
        int captain = FantasyScoring.slotPoints(PLAYER, oneWin, Rarity.LIMITED, true);
        assertEquals(60, base);
        assertEquals(72, unique);   // x1.2
        assertEquals(90, captain);  // x1.5
        assertTrue(unique > base && captain > base);
    }
}
